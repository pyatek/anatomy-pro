package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ptk.anatomypro.api.client.ApiClient
import com.ptk.anatomypro.api.client.ClientStore
import com.ptk.anatomypro.api.client.KtorDailyRepository
import com.ptk.anatomypro.api.client.KtorLeaderboardRepository
import com.ptk.anatomypro.api.client.ServerAccount
import com.ptk.anatomypro.api.client.ServerAccountRepository
import com.ptk.anatomypro.api.client.ServerEntitlementRepository
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.NotBuiltProviderSignIn
import com.ptk.anatomypro.core.data.notBuiltAppDependencies
import com.ptk.anatomypro.core.data.quiz.GeneratedQuizRepository
import com.ptk.anatomypro.core.data.quiz.RoomQuizGraphSource
import com.ptk.anatomypro.core.data.repository.InstalledPack
import com.ptk.anatomypro.core.data.repository.ProviderSignIn
import com.ptk.anatomypro.core.data.repository.RoomAtlasRepository
import com.ptk.anatomypro.core.data.repository.RoomInstalledPacks
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.network.CdnPackRepository
import com.ptk.anatomypro.core.network.PackIndexUnavailable
import com.ptk.anatomypro.core.network.platformHttpClient
import com.ptk.anatomypro.feature.onboarding.FIRST_PACK
import com.ptk.anatomypro.renderer.api.MeshSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/** A pack the canvases can draw: what it is, and where its mesh is on this device. */
data class DrawnPack(val id: PackId, val mesh: MeshSource, val label: String)

/**
 * The downloaded pack every canvas draws. Null where packs are not downloaded — a build
 * with no index to download from draws the pack shipped inside it.
 */
val LocalDrawnPack = staticCompositionLocalOf<DrawnPack?> { null }

/**
 * What an entry point hands to [App].
 *
 * [downloadsPacks] is true for a build that fetches its packs: the atlas is then not in the
 * app until the first download, which changes the order of onboarding. [packMissing] is
 * true once it is known that no pack is installed — as opposed to not known yet, which at
 * launch it never is for a moment.
 */
class AppEntry(
    val dependencies: AppDependencies,
    val pack: DrawnPack?,
    val downloadsPacks: Boolean = false,
    val packMissing: Boolean = false,
)

/**
 * The pack to draw, once [first] is installed.
 *
 * Both witnesses have to agree. The listing says the pack is installed; the database says
 * where its mesh is. A pack being deleted leaves the listing first and the database a moment
 * later, and a mesh must not be drawn from a row that is about to go.
 */
fun drawnPackOf(listed: List<PackState>, installed: List<InstalledPack>, first: PackId = FIRST_PACK): DrawnPack? {
    val state = listed.firstOrNull { it.id == first && it.status == PackStatus.Installed } ?: return null
    val onDevice = installed.firstOrNull { it.id == first } ?: return null
    return DrawnPack(first, MeshSource(onDevice.meshUri), state.label)
}

/** Where downloaded packs are kept: app-private, and not a cache the system may empty. */
@Composable
expect fun rememberPackDirectory(): String

/**
 * The production set for a build that downloads its packs from [packIndexUrl] (backend spec
 * §9) instead of shipping one inside the app.
 *
 * There is no atlas, and nothing to draw, until the first pack is installed; onboarding's
 * third step is what installs it. Only that pack is drawn: others can be downloaded and
 * deleted, and drawing several at once is not built.
 */
@Composable
fun rememberDownloadedApp(packIndexUrl: String): AppEntry {
    val database = rememberAnatomyDatabase()
    val settings = rememberSettingsRepository()
    val progress = rememberProgressRepository()
    val directory = rememberPackDirectory()
    val installed = remember(database) { RoomInstalledPacks(database) }
    val packs = remember(packIndexUrl, directory, installed, settings) {
        CdnPackRepository(
            indexUrl = packIndexUrl,
            client = platformHttpClient(),
            files = FileSystem.SYSTEM,
            directory = directory.toPath(),
            installed = installed,
            // Downloads outlive the screens that ask for them, and end with the process.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            locale = { settings.settings.first().interfaceLocale },
        )
    }

    var pack by remember { mutableStateOf<DrawnPack?>(null) }
    // Whether the listing has been heard from at all. Until then "no pack" is not known.
    var resolved by remember { mutableStateOf(false) }
    LaunchedEffect(packs, installed) {
        packs.packs
            // No index yet and no connection: nothing is installed either. Ask again.
            .retryWhen { cause, _ ->
                (cause is PackIndexUnavailable).also {
                    if (it) {
                        resolved = true
                        delay(INDEX_RETRY_MILLIS)
                    }
                }
            }
            .collect { listed ->
                pack = drawnPackOf(listed, installed.all())
                resolved = true
            }
    }

    return remember(pack, resolved, packs, settings, progress, database) {
        val base = notBuiltAppDependencies(
            atlas = if (pack != null) RoomAtlasRepository(database) else null,
            settings = settings,
        ).copy(progress = progress, packs = packs)
        AppEntry(
            dependencies = if (pack == null) base else {
                base.copy(quiz = GeneratedQuizRepository(RoomQuizGraphSource(database), mastered = progress::mastered))
            },
            pack = pack,
            downloadsPacks = true,
            packMissing = resolved && pack == null,
        )
    }
}

/** Where this device keeps its token: the Keychain, or a Keystore-encrypted file. */
@Composable
expect fun rememberClientStore(): ClientStore

/**
 * What a build is given decides what it is made of.
 *
 * [packIndexUrl]: packs are downloaded from there (backend spec §9); without it the pack
 * shipped inside the app is drawn. [apiBaseUrl]: the daily, the boards and entitlements
 * come from that server (§5 to §8); without it their tabs keep their placeholders.
 * [signIn]: the platform's sign-in sheet, offered on the account screen; it is used only
 * where there is a server to sign in to.
 */
@Composable
fun rememberAppEntry(
    packIndexUrl: String?,
    apiBaseUrl: String? = null,
    signIn: ProviderSignIn = NotBuiltProviderSignIn,
): AppEntry {
    val entry = if (packIndexUrl.isNullOrBlank()) AppEntry(rememberAppDependencies(), pack = null) else rememberDownloadedApp(packIndexUrl)
    if (apiBaseUrl.isNullOrBlank()) return entry

    val settings = entry.dependencies.settings
    val store = rememberClientStore()
    val server = remember(apiBaseUrl, store, settings) {
        val api = ApiClient(platformHttpClient(), apiBaseUrl, store)
        ServerRepositories(
            daily = KtorDailyRepository(api, timed = { settings.settings.first().quizTimerEnabled }),
            leaderboard = KtorLeaderboardRepository(api),
            entitlements = ServerEntitlementRepository(api, store),
            account = ServerAccountRepository(ServerAccount(api)),
        )
    }
    return remember(entry, server, signIn) {
        // Not before the atlas is in. The daily's model is made once and keeps what it is
        // first given, and until the pack is installed that would be a quiz that is not
        // there: its practice mode would be asking a placeholder.
        if (entry.dependencies.atlas == null) {
            entry
        } else {
            AppEntry(
                // A sheet that offers no provider leaves the account screen with the nickname
                // and deletion only.
                dependencies = entry.dependencies.copy(
                    daily = server.daily,
                    leaderboard = server.leaderboard,
                    entitlements = server.entitlements,
                    account = server.account,
                    signIn = signIn,
                ),
                pack = entry.pack,
                downloadsPacks = entry.downloadsPacks,
                packMissing = entry.packMissing,
            )
        }
    }
}

/** What a build given a server gets from it. */
private class ServerRepositories(
    val daily: KtorDailyRepository,
    val leaderboard: KtorLeaderboardRepository,
    val entitlements: ServerEntitlementRepository,
    val account: ServerAccountRepository,
)

private const val INDEX_RETRY_MILLIS = 5_000L

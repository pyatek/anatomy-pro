package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.notBuiltAppDependencies
import com.ptk.anatomypro.core.data.quiz.GeneratedQuizRepository
import com.ptk.anatomypro.core.data.quiz.RoomQuizGraphSource
import com.ptk.anatomypro.core.data.repository.InstalledPack
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

/** What an entry point hands to [App]. */
class AppEntry(val dependencies: AppDependencies, val pack: DrawnPack?)

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
    LaunchedEffect(packs, installed) {
        packs.packs
            // No index yet and no connection: nothing is installed either. Ask again.
            .retryWhen { cause, _ -> (cause is PackIndexUnavailable).also { if (it) delay(INDEX_RETRY_MILLIS) } }
            .collect { listed -> pack = drawnPackOf(listed, installed.all()) }
    }

    return remember(pack, packs, settings, progress, database) {
        val base = notBuiltAppDependencies(
            atlas = if (pack != null) RoomAtlasRepository(database) else null,
            settings = settings,
        ).copy(progress = progress, packs = packs)
        AppEntry(
            dependencies = if (pack == null) base else {
                base.copy(quiz = GeneratedQuizRepository(RoomQuizGraphSource(database), mastered = progress::mastered))
            },
            pack = pack,
        )
    }
}

/** Downloaded packs where there is an index to download them from; the shipped pack otherwise. */
@Composable
fun rememberAppEntry(packIndexUrl: String?): AppEntry =
    if (packIndexUrl.isNullOrBlank()) AppEntry(rememberAppDependencies(), pack = null) else rememberDownloadedApp(packIndexUrl)

private const val INDEX_RETRY_MILLIS = 5_000L

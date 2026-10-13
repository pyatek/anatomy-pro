package com.ptk.anatomypro

import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import com.ptk.anatomypro.core.data.progress.RoomProgressRepository
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.NotBuiltDailyRepository
import com.ptk.anatomypro.core.data.NotBuiltQuizRepository
import com.ptk.anatomypro.core.data.fake.FakeAccountRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.fake.FakeProviderSignIn
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.fake.UnreviewedAtlas
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies
import com.ptk.anatomypro.core.data.model.Account
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.quiz.GeneratedQuizRepository
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Debug builds run on fakes for everything except the atlas.
 *
 * The atlas is the real one, Room over the bundled pack, because the 3D canvas always draws
 * that pack: a fake atlas beside it would show a tree that does not match the model, and
 * picking a structure on the model would select nothing, since the pack's ids are not in the
 * fixture. The fake atlas stays what feature tests use.
 *
 * Onboarding is skipped unless asked for, because fake settings forget everything and a
 * debug build that opened on screen 01 every launch would be in the way:
 *
 *     adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true
 *     adb shell am start -n com.ptk.anatomypro/.MainActivity --ez onboarding true --es packFailure NETWORK
 *
 * `packFailure` takes a PackFailure name and fails the first attempt only, so the resume
 * can be seen working.
 */
@Composable
internal fun appEntry(): AppEntry {
    val intent = LocalActivity.current?.intent
    //   packIndex: the URL of a pack index. The app then runs as a release build given that
    //   index does: nothing shipped inside it, the skeleton fetched by onboarding's third step.
    //   adb shell am start -n com.ptk.anatomypro/.MainActivity --es packIndex http://10.0.2.2:8099/index.json
    //   apiUrl: the server behind the daily, the boards and entitlements, in place of the fakes.
    //   adb shell am start -n com.ptk.anatomypro/.MainActivity --es packIndex … --es apiUrl http://10.0.2.2:8080
    val packIndex = intent?.getStringExtra("packIndex") ?: BuildConfig.PACK_INDEX_URL
    val apiUrl = intent?.getStringExtra("apiUrl") ?: BuildConfig.API_BASE_URL
    if (packIndex.isNotBlank() || apiUrl.isNotBlank()) return rememberAppEntry(packIndex, apiUrl)
    return AppEntry(fakeOrProductionDependencies(), pack = null)
}

@Composable
private fun fakeOrProductionDependencies(): AppDependencies {
    val intent = LocalActivity.current?.intent
    //   production: true runs this debug build on exactly what a release build is given —
    //   the real quiz and progress store, and refusals for everything not built yet.
    if (intent?.getBooleanExtra("production", false) == true) return rememberAppDependencies()

    val atlas = rememberAtlas()
    //   realProgress: true keeps sessions in the database instead of showing the fixture's
    //   history, so mastery, the streak and the calendar can be watched changing.
    val realProgress = intent?.getBooleanExtra("realProgress", false) ?: false
    val database = rememberAnatomyDatabase()
    val stored = remember(database) {
        RoomProgressRepository(
            database,
            today = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    val onboarding = intent?.getBooleanExtra("onboarding", false) ?: false
    val packFailure = intent?.getStringExtra("packFailure")
        ?.let { name -> PackFailure.entries.firstOrNull { it.name == name } }

    // adb shell am start -n com.ptk.anatomypro/.MainActivity --ez subscribed true --es daily offline
    //   subscribed: true shows the daily and the boards; without it, "not subscribed".
    //   daily: "offline" or "attempted"; anything else is a day not yet played.
    //   unranked: true leaves the user off the boards.
    //   purchaseFails: true makes every purchase on the paywall fail.
    val subscribed = intent?.getBooleanExtra("subscribed", false) ?: false
    val dailyState = intent?.getStringExtra("daily")
    val unranked = intent?.getBooleanExtra("unranked", false) ?: false
    val purchaseFails = intent?.getBooleanExtra("purchaseFails", false) ?: false

    // adb shell am start -n com.ptk.anatomypro/.MainActivity --es account locked
    //   account: the account behind Profile → Account. Without it, an anonymous account
    //   that can be renamed ("foramen" and "vesalius_1543" are taken), signed in and deleted.
    //     "locked"    its nickname was reported: no new one for thirty days.
    //     "linked"    already signed in with Google, so it can be signed out of.
    //     "merge"     signing in opens an account from another device (vesalius_1543).
    //     "rejected"  the server does not believe the provider's token.
    //     "cancelled" the provider's sheet is closed without signing in.
    //     "offline"   nothing about the account gets through.
    //   With `subscribed`, deleting the account says a subscription is still running.
    val accountState = intent?.getStringExtra("account")

    // Built once, whatever the atlas is doing: screens and app-scoped models hold on to
    // the repositories they are first given, and a second set made when the pack finished
    // installing would leave them reading the first.
    val fakes = remember(onboarding, packFailure, subscribed, unranked, purchaseFails, accountState) {
        val base = fakeAppDependencies().copy(
            leaderboard = FakeLeaderboardRepository(ranked = !unranked),
            entitlements = FakeEntitlementRepository(
                initial = Entitlements(subscribed = subscribed, ownedSystems = emptySet()),
                purchaseSucceeds = !purchaseFails,
            ),
            account = FakeAccountRepository(
                // Slow enough for a request to be seen being made.
                behaviour = FakeBehaviour(
                    delay = 400.milliseconds,
                    failure = if (accountState == "offline") ({ IllegalStateException("no connection") }) else null,
                ),
                initial = when (accountState) {
                    "locked" -> Account("student-4821", nicknameLockedUntil = Clock.System.now() + 30.days)
                    "linked" -> Account("costa_xii", linked = setOf(SignInProvider.GOOGLE))
                    else -> Account("student-4821")
                },
                elsewhere = if (accountState == "merge") Account("vesalius_1543", linked = setOf(SignInProvider.GOOGLE)) else null,
                believesTokens = accountState != "rejected",
                subscribed = subscribed,
            ),
            // Both providers, so both rows can be seen; a real Android build may offer one.
            signIn = FakeProviderSignIn(cancels = accountState == "cancelled"),
        )
        if (!onboarding) {
            base
        } else {
            base.copy(
                settings = FakeSettingsRepository(AppSettings(onboarded = false)),
                // Slow enough to watch: the fake's steps are otherwise instantaneous.
                packs = FakePackRepository(
                    behaviour = FakeBehaviour(delay = 600.milliseconds),
                    downloadFailure = packFailure,
                    failingAttempts = 1,
                    initiallyInstalled = emptySet(),
                ),
            )
        }
    }
    return remember(atlas, fakes, dailyState, realProgress) {
        val installed = atlas?.repository
            // Until the pack is installed there is no quiz and no daily, and their tabs say
            // so. Either one built over the fixture here would be captured by its
            // app-scoped model and kept: the daily was seen telling a subscriber they had
            // no subscription, and the topic grid would list the atlas while the session
            // asked the fixture.
            ?: return@remember fakes.copy(atlas = null, quiz = NotBuiltQuizRepository, daily = NotBuiltDailyRepository)

        // The real generator (§8.1), asking about the atlas on screen: fixture ids are not
        // in the pack, so nothing a fixture quiz highlighted could be seen and nothing
        // tapped could be right. Over a harness — UnreviewedAtlas switches §7's
        // verified-only rule off, which would leave an unreviewed atlas with nothing to ask.
        val quiz = GeneratedQuizRepository(
            UnreviewedAtlas(atlas.quizGraph),
            mastered = { if (realProgress) stored.mastered() else emptySet() },
        )
        fakes.copy(
            atlas = installed,
            quiz = quiz,
            progress = if (realProgress) stored else fakes.progress,
            // The daily quiz practises on the same questions, so it shares the repository,
            // and asks about its first topic: the atlas may have no "costae".
            daily = FakeDailyRepository(
                available = dailyState != "offline",
                alreadyAttempted = dailyState == "attempted",
                quiz = quiz,
                topic = null,
            ),
        )
    }
}

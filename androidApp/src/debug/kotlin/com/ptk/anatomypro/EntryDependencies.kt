package com.ptk.anatomypro

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.NotBuiltQuizRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeDailyRepository
import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.fake.UnreviewedAtlas
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.quiz.GeneratedQuizRepository
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
internal fun appDependencies(): AppDependencies {
    val atlas = rememberAtlas()
    val intent = LocalActivity.current?.intent
    val onboarding = intent?.getBooleanExtra("onboarding", false) ?: false
    val packFailure = intent?.getStringExtra("packFailure")
        ?.let { name -> PackFailure.entries.firstOrNull { it.name == name } }

    val fakes = remember(onboarding, packFailure) {
        val base = fakeAppDependencies()
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
    // adb shell am start -n com.ptk.anatomypro/.MainActivity --ez subscribed true --es daily offline
    //   subscribed: true shows the daily and the boards; without it, "not subscribed".
    //   daily: "offline" or "attempted"; anything else is a day not yet played.
    //   unranked: true leaves the user off the boards.
    val subscribed = intent?.getBooleanExtra("subscribed", false) ?: false
    val dailyState = intent?.getStringExtra("daily")
    val unranked = intent?.getBooleanExtra("unranked", false) ?: false
    //   purchaseFails: true makes every purchase on the paywall fail.
    val purchaseFails = intent?.getBooleanExtra("purchaseFails", false) ?: false
    return remember(atlas, fakes, subscribed, dailyState, unranked, purchaseFails) {
        val installed = atlas?.repository
            // Until the pack is installed there is no quiz, and the Test tab says so. A quiz
            // over the fixture here would be captured by the app-scoped session and kept:
            // the topic grid would then list the atlas while the session asked the fixture.
            ?: return@remember fakes.copy(atlas = null, quiz = NotBuiltQuizRepository)

        // The real generator (§8.1), asking about the atlas on screen: fixture ids are not
        // in the pack, so nothing a fixture quiz highlighted could be seen and nothing
        // tapped could be right. Over a harness — UnreviewedAtlas switches §7's
        // verified-only rule off, which would leave an unreviewed atlas with nothing to ask.
        val quiz = GeneratedQuizRepository(UnreviewedAtlas(atlas.quizGraph))
        fakes.copy(
            atlas = installed,
            quiz = quiz,
            // The daily quiz practises on the same questions, so it shares the repository,
            // and asks about its first topic: the atlas may have no "costae".
            daily = FakeDailyRepository(
                available = dailyState != "offline",
                alreadyAttempted = dailyState == "attempted",
                quiz = quiz,
                topic = null,
            ),
            leaderboard = FakeLeaderboardRepository(ranked = !unranked),
            entitlements = FakeEntitlementRepository(
                initial = Entitlements(subscribed = subscribed, ownedSystems = emptySet()),
                purchaseSucceeds = !purchaseFails,
            ),
        )
    }
}

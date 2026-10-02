package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.model.AppSettings

/**
 * A fully fake dependency set: no database, no pack on disk, no device.
 *
 * Called only from debug entry points. Every screen can be reached from here, which is
 * what makes running a screen a default rather than an expedition.
 */
fun fakeAppDependencies(): AppDependencies {
    val quiz = FakeQuizRepository()
    return AppDependencies(
        atlas = FakeAtlasRepository(),
        settings = FakeSettingsRepository(AppSettings(onboarded = true)),
        quiz = quiz,
        progress = FakeProgressRepository(),
        daily = FakeDailyRepository(quiz = quiz),
        leaderboard = FakeLeaderboardRepository(),
        entitlements = FakeEntitlementRepository(),
        packs = FakePackRepository(),
    )
}

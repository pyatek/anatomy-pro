package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository

/**
 * Everything the app reads, in one place.
 *
 * App() used to construct its own settings repository, which made the choice of
 * implementation an internal decision of a composable. Passing the set in is what lets a
 * debug entry point supply fakes while production supplies Room — and it is the same swap
 * Phase 3 performs when Ktor implementations arrive (spec §6).
 *
 * This lives in core-data rather than in :shared so that :shared:core-data-fake can build
 * one without depending on :shared, which would be a cycle.
 *
 * [atlas] is nullable because the bundled pack installs asynchronously: null means "still
 * opening", which is a different thing to show than an empty atlas.
 */
data class AppDependencies(
    val atlas: AtlasRepository?,
    val settings: SettingsRepository,
    val quiz: QuizRepository,
    val progress: ProgressRepository,
    val daily: DailyRepository,
    val leaderboard: LeaderboardRepository,
    val entitlements: EntitlementRepository,
    val packs: PackRepository,
)

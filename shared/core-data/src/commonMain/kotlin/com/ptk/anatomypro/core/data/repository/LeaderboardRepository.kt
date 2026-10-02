package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.Leaderboard
import kotlinx.datetime.LocalDate

/** Screen 16. v1 boards are global daily and personal streak (§9.3). */
interface LeaderboardRepository {
    suspend fun daily(date: LocalDate): Leaderboard
    suspend fun streaks(): Leaderboard
}

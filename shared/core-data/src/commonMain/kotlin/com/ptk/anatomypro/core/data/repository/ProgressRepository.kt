package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Screens 08, 14 and 17. */
interface ProgressRepository {
    val streak: Flow<StreakState>
    suspend fun masteryBySystem(locale: String): List<SystemMastery>
    /** Screen 17's calendar. Local dates; the daily quiz's own dates are UTC (§9.1). */
    suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity>
    suspend fun recordSession(summary: QuizSummary)
}

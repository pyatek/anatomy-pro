package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId
import kotlinx.datetime.LocalDate

/**
 * Screen 14's streak and screen 17's calendar.
 *
 * [lastStudied] is a local date. The daily quiz works in UTC (§9.1), and the two must not
 * be conflated: the calendar is the user's day, the daily quiz is the world's.
 */
data class StreakState(
    val currentDays: Int,
    val longestDays: Int,
    val lastStudied: LocalDate?,
)

data class SystemMastery(
    val system: SystemId,
    val title: String,
    val structuresSeen: Int,
    val structuresTotal: Int,
) {
    val fraction: Float get() = if (structuresTotal == 0) 0f else structuresSeen.toFloat() / structuresTotal
}

/** One square in screen 17's calendar. */
data class DayActivity(val sessions: Int, val questionsAnswered: Int, val dailyCompleted: Boolean)

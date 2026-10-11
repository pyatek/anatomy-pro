package com.ptk.anatomypro.core.data.progress

import com.ptk.anatomypro.core.data.model.StreakState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {

    private val today = LocalDate(2026, 10, 11)
    private fun daysAgo(vararg ago: Int) = ago.map { LocalDate.fromEpochDays(today.toEpochDays() - it) }.toSet()

    @Test
    fun nobody_who_has_never_studied_has_a_streak() {
        assertEquals(StreakState(currentDays = 0, longestDays = 0, lastStudied = null), streakOf(emptySet(), today))
    }

    @Test
    fun days_in_a_row_ending_today_are_the_streak() {
        val streak = streakOf(daysAgo(0, 1, 2), today)

        assertEquals(3, streak.currentDays)
        assertEquals(3, streak.longestDays)
        assertEquals(today, streak.lastStudied)
    }

    @Test
    fun a_streak_is_still_alive_on_a_day_not_yet_studied() {
        // Studied yesterday, not yet today: there is until midnight to keep it.
        assertEquals(2, streakOf(daysAgo(1, 2), today).currentDays)
    }

    @Test
    fun a_missed_day_ends_the_streak_and_the_longest_is_remembered() {
        val streak = streakOf(daysAgo(2, 3, 4, 5), today)

        assertEquals(0, streak.currentDays)
        assertEquals(4, streak.longestDays)
        assertEquals(daysAgo(2).single(), streak.lastStudied)
    }

    @Test
    fun the_longest_run_may_be_an_old_one() {
        val streak = streakOf(daysAgo(0, 1, 10, 11, 12, 13, 14), today)

        assertEquals(2, streak.currentDays)
        assertEquals(5, streak.longestDays)
    }

    @Test
    fun a_day_recorded_ahead_of_today_does_not_break_the_count() {
        // The clock was moved, or a timezone crossed: not a reason to show a streak of zero.
        assertEquals(2, streakOf(daysAgo(-1, 0, 1), today).currentDays)
    }
}

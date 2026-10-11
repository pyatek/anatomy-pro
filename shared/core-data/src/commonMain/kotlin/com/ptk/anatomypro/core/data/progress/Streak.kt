package com.ptk.anatomypro.core.data.progress

import com.ptk.anatomypro.core.data.model.StreakState
import kotlinx.datetime.LocalDate

/**
 * The streak, from the days anything was studied on.
 *
 * Days are the user's own (all-screens spec §4.2), never UTC. A streak is alive on a day
 * not yet studied: it ends when a whole day has passed without a session, not at midnight
 * of the last one. A day recorded after [today] — a clock moved, a timezone crossed — is
 * ignored for the current run rather than allowed to break it.
 */
fun streakOf(days: Set<LocalDate>, today: LocalDate): StreakState {
    if (days.isEmpty()) return StreakState(currentDays = 0, longestDays = 0, lastStudied = null)
    val epochDays = days.map { it.toEpochDays() }.toSet()

    var longest = 0
    for (day in epochDays) {
        // Counted from each run's first day only, so every run is walked once.
        if (day - 1 in epochDays) continue
        var length = 1
        while (day + length in epochDays) length++
        if (length > longest) longest = length
    }

    val todayEpoch = today.toEpochDays()
    val end = if (todayEpoch in epochDays) todayEpoch else todayEpoch - 1
    var current = 0
    while (end - current in epochDays) current++

    return StreakState(currentDays = current, longestDays = longest, lastStudied = days.filter { it <= today }.maxOrNull() ?: days.max())
}

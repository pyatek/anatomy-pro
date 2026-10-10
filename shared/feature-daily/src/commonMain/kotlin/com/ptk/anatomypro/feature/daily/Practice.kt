package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.NotBuiltDailyRepository
import com.ptk.anatomypro.core.data.NotBuiltLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.FREE_SYSTEMS
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.DailyRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository

/**
 * The topic practice mode uses, or null when there is nothing to practise on.
 *
 * Practice is an ordinary topic session offered when the daily cannot be reached (design
 * spec §9.1, §14; all-screens spec §15.4). It is on something the student is studying if
 * they may be quizzed on it; otherwise on the skeleton, which everyone may be; otherwise on
 * anything they may be.
 */
fun practiceTopic(topics: List<QuizTopic>, studied: Set<String>, entitlements: Entitlements): QuizTopic? {
    val allowed = topics.filter { entitlements.allows(it.system) }
    return allowed.firstOrNull { it.system.value in studied }
        ?: allowed.firstOrNull { it.system in FREE_SYSTEMS }
        ?: allowed.firstOrNull()
}

/**
 * Whether there is a daily quiz and a leaderboard to show.
 *
 * Production's repositories for both throw on first use, by design (all-screens spec §6).
 * There the Today and Ranking tabs keep their placeholders.
 */
fun dailyIsBuilt(daily: DailyRepository, leaderboard: LeaderboardRepository): Boolean =
    daily !is NotBuiltDailyRepository && leaderboard !is NotBuiltLeaderboardRepository

package com.ptk.anatomypro.core.data.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Mirrors §9.1's three-step flow rather than flattening it: the clock starts when the
 * questions are fetched, and screen 15 is what the user looks at while it runs.
 */
sealed interface DailyQuiz {
    data class NotStarted(val date: LocalDate, val questionCount: Int) : DailyQuiz

    data class InProgress(
        val date: LocalDate,
        val questions: List<QuizQuestion>,
        val startedAt: Instant,
    ) : DailyQuiz

    data class Completed(val date: LocalDate, val result: DailyResult) : DailyQuiz

    /**
     * §14: offline blocks entry with a clear message and offers practice mode.
     *
     * A state rather than a thrown exception, because it has designed copy and a designed
     * screen — it is not an error path.
     */
    data object Unavailable : DailyQuiz
}

/** Scored server-side against a key that never leaves the server (§9.1). */
data class DailyResult(
    val date: LocalDate,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    val rank: Int?,
    val totalPlayers: Int,
)

data class LeaderboardEntry(
    val rank: Int,
    val nickname: String,
    val score: Int,
    val elapsedMillis: Long,
    val isMe: Boolean,
)

/**
 * [me] is nullable: an untimed session is unranked (§12), and a user who has not played
 * today has no row. Carrying it on the board keeps "your position" from being a second
 * call that can disagree with the first.
 */
data class Leaderboard(
    val entries: List<LeaderboardEntry>,
    val me: LeaderboardEntry?,
    val totalPlayers: Int,
)

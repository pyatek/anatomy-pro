package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.datetime.LocalDate

/**
 * v1's two boards (§9.3).
 *
 * [ranked] off is the untimed case: §12 makes a disabled timer unranked, and screen 16 has
 * to show a board the user is not on.
 */
class FakeLeaderboardRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val ranked: Boolean = true,
) : LeaderboardRepository {

    private val nicknames = listOf(
        "anatomia_ninja", "costa_vii", "m_sternocleido", "foramen", "vesalius_1543",
        "kostny", "scapula", "os_hyoideum", "pterygoid", "trochlea",
    )

    /**
     * [myScore] is given rather than derived from [scoreOf]: the user sits far below the
     * listed rows, and extrapolating the listed scores that far down goes negative.
     */
    private fun board(myRank: Int, myScore: Int, scoreOf: (Int) -> Int): Leaderboard {
        val entries = nicknames.mapIndexed { index, nickname ->
            LeaderboardEntry(
                rank = index + 1,
                nickname = nickname,
                score = scoreOf(index),
                elapsedMillis = 40_000L + index * 1_500L,
                isMe = false,
            )
        }
        val me = if (!ranked) null else LeaderboardEntry(
            rank = myRank,
            nickname = "ty",
            score = myScore,
            elapsedMillis = 64_000,
            isMe = true,
        )
        return Leaderboard(entries = entries, me = me, totalPlayers = 5_180)
    }

    override suspend fun daily(date: LocalDate): Leaderboard =
        // 7 of 10, matching FakeDailyRepository's completed attempt.
        behaviour.respond { board(myRank = 412, myScore = 7) { index -> 10 - index / 4 } }

    override suspend fun streaks(): Leaderboard =
        // A 12-day streak, matching FakeProgressRepository's default.
        behaviour.respond { board(myRank = 88, myScore = 12) { index -> 120 - index * 7 } }
}

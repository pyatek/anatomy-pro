package com.ptk.anatomypro.backend

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backend spec §7, §6.3's order, and §4.3: the board's streak is the daily's. */
class BoardsTest {

    private val harness = DailyHarness()

    @Test
    fun correctness_ranks_before_time_and_time_before_who_submitted_first() = harness.run {
        val slowAndRight = player()
        val fastAndRight = player()
        val fastAndWrong = player()
        val tiedFirst = player()
        val tiedSecond = player()

        fastAndWrong.play(right = 1, thinking = Duration.ofSeconds(10))
        slowAndRight.play(thinking = Duration.ofSeconds(90))
        fastAndRight.play(thinking = Duration.ofSeconds(30))
        // The same score in the same time, one after the other.
        tiedFirst.play(right = 2, thinking = Duration.ofSeconds(50))
        tiedSecond.play(right = 2, thinking = Duration.ofSeconds(50))

        val board = slowAndRight.dailyBoard()

        assertEquals(
            listOf(fastAndRight.nickname, slowAndRight.nickname, tiedFirst.nickname, tiedSecond.nickname, fastAndWrong.nickname),
            board.entries.map { it.nickname },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), board.entries.map { it.rank })
        assertEquals(5, board.totalPlayers)
        assertEquals(30_000, board.entries.first().elapsedMillis)
    }

    @Test
    fun the_callers_own_row_comes_with_the_board_and_agrees_with_it() = harness.run {
        val first = player()
        val second = player()
        first.play(thinking = Duration.ofSeconds(20))
        val result = second.play(thinking = Duration.ofSeconds(40))

        val board = second.dailyBoard()

        assertEquals(result.rank, board.me?.rank)
        assertEquals(second.nickname, board.me?.nickname)
        assertTrue(board.me!!.isMe)
        assertEquals(board.entries.single { it.isMe }, board.me)
        assertFalse(board.entries.first().isMe)
    }

    @Test
    fun the_rank_in_a_result_is_the_place_on_the_board() = harness.run {
        val first = player()
        val second = player()
        first.play(thinking = Duration.ofSeconds(20))

        val result = second.play(thinking = Duration.ofSeconds(40))

        assertEquals(2, result.rank)
        assertEquals(2, result.totalPlayers)
    }

    @Test
    fun an_attempt_that_is_not_ranked_is_on_no_board() = harness.run {
        val ranked = player()
        val untimed = player()
        val notPlayed = player()
        ranked.play()
        untimed.play(timed = false)

        val board = untimed.dailyBoard()

        assertEquals(listOf(ranked.nickname), board.entries.map { it.nickname })
        assertEquals(1, board.totalPlayers)
        assertNull(board.me)
        assertNull(notPlayed.dailyBoard().me)
    }

    @Test
    fun only_the_first_fifty_are_listed_and_a_player_below_them_still_has_a_row() {
        val small = DailyHarness(Settings(everyoneIsSubscribed = true, boardSize = 3, requestsPerTokenPerMinute = 10_000, accountsPerAddressPerMinute = 10_000))
        small.run {
            val players = List(5) { player() }
            players.forEachIndexed { index, player -> player.play(thinking = Duration.ofSeconds(20L + index)) }

            val board = players.last().dailyBoard()

            assertEquals(3, board.entries.size)
            assertEquals(5, board.totalPlayers)
            assertEquals(5, board.me?.rank)
        }
    }

    @Test
    fun a_day_nobody_played_has_an_empty_board() = harness.run {
        val board = player().dailyBoard("2026-10-01")

        assertEquals(emptyList(), board.entries)
        assertEquals(0, board.totalPlayers)
    }

    // --- streaks ------------------------------------------------------------------------

    @Test
    fun dailies_on_days_in_a_row_make_a_streak_and_a_missed_day_ends_it() = harness.run {
        val steady = player()
        val lapsed = player()

        steady.play(); lapsed.play()
        harness.nextDay()
        steady.play()
        harness.nextDay()
        steady.play(); lapsed.play()

        val board = steady.streakBoard()

        assertEquals(listOf(steady.nickname to 3, lapsed.nickname to 1), board.entries.map { it.nickname to it.score })
        assertEquals(3, board.me?.score)
        assertEquals(1, board.me?.rank)
    }

    @Test
    fun yesterdays_streak_still_stands_today_and_one_older_than_that_does_not() = harness.run {
        val player = player()
        val watcher = player()
        player.play()

        harness.nextDay()
        assertEquals(listOf(player.nickname), watcher.streakBoard().entries.map { it.nickname })

        harness.nextDay()
        assertEquals(emptyList(), watcher.streakBoard().entries.map { it.nickname })
        assertNull(player.streakBoard().me)
    }

    @Test
    fun a_daily_completed_but_not_ranked_still_counts_towards_the_streak() = harness.run {
        // Turning the timer off costs a place on the day's board, never the habit (design §12).
        val player = player()
        player.play(timed = false)
        harness.nextDay()
        player.play(timed = false)

        assertEquals(2, player.streakBoard().me?.score)
    }

    @Test
    fun submitting_twice_does_not_count_the_day_twice() = harness.run {
        val player = player()
        val attempt = player.start()
        harness.pass(Duration.ofSeconds(60))
        repeat(3) { player.submitResponse(attempt.date, attempt.questions.map { player.answer(it, true) }) }

        assertEquals(1, player.streakBoard().me?.score)
    }
}

package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeEntitlementRepository
import com.ptk.anatomypro.core.data.fake.FakeLeaderboardRepository
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.model.SubscriptionPlan
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaderboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 9, 24)
    private val subscriber = Entitlements(subscribed = true, ownedSystems = emptySet())

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(
        leaderboard: LeaderboardRepository = FakeLeaderboardRepository(),
        entitlements: FakeEntitlementRepository = FakeEntitlementRepository(initial = subscriber),
    ) = LeaderboardViewModel(leaderboard, entitlements, today = { day })

    @Test
    fun the_daily_board_is_shown_first_with_the_users_own_row() = runTest(dispatcher) {
        // §9, screen 16: daily board.
        val model = model()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(Board.DAILY, state.board)
        assertEquals(412, state.leaderboard?.me?.rank)
        assertFalse(state.unranked)
        assertFalse(state.isLoading)
    }

    @Test
    fun the_daily_board_is_asked_for_todays_date() = runTest(dispatcher) {
        var asked: LocalDate? = null
        val real = FakeLeaderboardRepository()
        val recording = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard = real.daily(date).also { asked = date }
        }

        model(leaderboard = recording)
        advanceUntilIdle()

        assertEquals(day, asked)
    }

    @Test
    fun switching_to_the_streak_board_loads_it() = runTest(dispatcher) {
        // §9, screen 16: streak board.
        val model = model()
        advanceUntilIdle()

        model.onBoard(Board.STREAK)
        advanceUntilIdle()

        assertEquals(Board.STREAK, model.state.value.board)
        assertEquals(88, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_user_who_is_not_on_the_board_is_unranked_and_the_board_is_still_shown() = runTest(dispatcher) {
        // §9, screen 16: unranked user. Review Focus 5.
        val model = model(leaderboard = FakeLeaderboardRepository(ranked = false))
        advanceUntilIdle()

        assertTrue(model.state.value.unranked)
        assertNull(model.state.value.leaderboard?.me)
        assertEquals(10, model.state.value.leaderboard?.entries?.size)
    }

    @Test
    fun a_free_user_is_told_they_are_not_subscribed_and_no_board_is_asked_for() = runTest(dispatcher) {
        // §15.3.
        var asked = 0
        val real = FakeLeaderboardRepository()
        val counting = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard = real.daily(date).also { asked++ }
            override suspend fun streaks(): Leaderboard = real.streaks().also { asked++ }
        }

        val model = model(leaderboard = counting, entitlements = FakeEntitlementRepository())
        advanceUntilIdle()

        assertFalse(model.state.value.subscribed)
        assertNull(model.state.value.leaderboard)
        assertEquals(0, asked)
    }

    @Test
    fun subscribing_while_the_screen_is_open_loads_the_board() = runTest(dispatcher) {
        // Review Focus 4.
        val entitlements = FakeEntitlementRepository()
        val model = model(entitlements = entitlements)
        advanceUntilIdle()

        entitlements.purchase(SubscriptionPlan("monthly", "Miesięcznie", "29 zł", periodMonths = 1))
        advanceUntilIdle()

        assertTrue(model.state.value.subscribed)
        assertEquals(412, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_board_that_cannot_be_loaded_is_a_failure_that_can_be_retried() = runTest(dispatcher) {
        var broken = true
        val real = FakeLeaderboardRepository()
        val flaky = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard =
                if (broken) throw IllegalStateException("no board") else real.daily(date)
        }
        val model = model(leaderboard = flaky)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals(412, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun a_slow_board_does_not_overwrite_the_one_switched_to_after_it() = runTest(dispatcher) {
        // The daily board answers late, after the user has moved to streaks.
        val real = FakeLeaderboardRepository()
        val slowDaily = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard {
                kotlinx.coroutines.delay(5_000)
                return real.daily(date)
            }
        }
        val model = model(leaderboard = slowDaily)
        // Let the daily board's request go out and be left waiting.
        runCurrent()
        assertTrue(model.state.value.isLoading)

        model.onBoard(Board.STREAK)
        advanceUntilIdle()

        assertEquals(Board.STREAK, model.state.value.board)
        assertEquals(88, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun the_board_is_read_again_when_the_screen_is_come_back_to() = runTest(dispatcher) {
        // The model outlives a tab switch. A user who played the daily and came back was
        // still shown the board from before, without their place on it.
        var asked = 0
        val real = FakeLeaderboardRepository()
        val counting = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard { asked++; return real.daily(date) }
        }
        val model = model(leaderboard = counting)
        advanceUntilIdle()
        assertEquals(1, asked)

        model.refresh()
        advanceUntilIdle()

        assertEquals(2, asked)
        assertEquals(412, model.state.value.leaderboard?.me?.rank)
    }

    @Test
    fun coming_back_as_a_free_user_still_asks_for_no_board() = runTest(dispatcher) {
        var asked = 0
        val real = FakeLeaderboardRepository()
        val counting = object : LeaderboardRepository by real {
            override suspend fun daily(date: LocalDate): Leaderboard { asked++; return real.daily(date) }
        }
        val model = model(leaderboard = counting, entitlements = FakeEntitlementRepository())
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()

        assertEquals(0, asked)
    }
}

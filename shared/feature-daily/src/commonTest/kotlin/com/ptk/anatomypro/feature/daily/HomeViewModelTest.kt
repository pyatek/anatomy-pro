package com.ptk.anatomypro.feature.daily

import com.ptk.anatomypro.core.data.fake.FakeProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_streak_is_shown_as_the_store_has_it() = runTest(dispatcher) {
        val model = HomeViewModel(FakeProgressRepository())
        advanceUntilIdle()

        assertEquals(12, model.state.value.streak?.currentDays)
        assertEquals(31, model.state.value.streak?.longestDays)
    }

    @Test
    fun a_student_who_has_never_studied_has_a_streak_of_nothing_not_no_streak() = runTest(dispatcher) {
        val never = StreakState(currentDays = 0, longestDays = 0, lastStudied = null)

        val model = HomeViewModel(FakeProgressRepository(initialStreak = never))
        advanceUntilIdle()

        assertEquals(never, model.state.value.streak)
    }

    @Test
    fun a_streak_that_cannot_be_read_is_unknown_and_nothing_crashes() = runTest(dispatcher) {
        val broken = object : ProgressRepository {
            override val streak: Flow<StreakState> = flow { throw IllegalStateException("no store") }
            override suspend fun masteryBySystem(locale: String): List<SystemMastery> = emptyList()
            override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> = emptyMap()
            override suspend fun recordSession(summary: QuizSummary) = Unit
        }

        val model = HomeViewModel(broken)
        advanceUntilIdle()

        assertNull(model.state.value.streak)
    }
}

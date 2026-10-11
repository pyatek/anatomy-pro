package com.ptk.anatomypro.feature.profile

import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.NotBuiltProgressRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakePackRepository
import com.ptk.anatomypro.core.data.fake.FakeProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 9, 24)

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model(progress: ProgressRepository = FakeProgressRepository(), locale: String = "pl") =
        ProfileViewModel(progress, FakePackRepository(), locale, today = { today })

    @Test
    fun the_calendar_is_the_last_five_weeks_ending_today_oldest_first() = runTest(dispatcher) {
        // §9, screen 17: streak calendar.
        val model = model()
        advanceUntilIdle()

        val calendar = model.state.value.calendar
        assertEquals(CALENDAR_DAYS, calendar.size)
        assertEquals(LocalDate(2026, 8, 21), calendar.first().date)
        assertEquals(today, calendar.last().date)
    }

    @Test
    fun a_day_carries_what_was_done_on_it() = runTest(dispatcher) {
        // The fake studies every day whose number is not a multiple of three, and completes
        // the daily on the even ones among them.
        val model = model()
        advanceUntilIdle()
        val days = model.state.value.calendar.associateBy { it.date }

        assertEquals(DayActivity(sessions = 1, questionsAnswered = 10, dailyCompleted = true), days.getValue(LocalDate(2026, 9, 22)).activity)
        assertEquals(DayActivity(sessions = 1, questionsAnswered = 10, dailyCompleted = false), days.getValue(LocalDate(2026, 9, 23)).activity)
        assertEquals(0, days.getValue(today).activity?.sessions)
    }

    @Test
    fun the_streak_is_shown_beside_the_calendar() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(12, model.state.value.streak?.currentDays)
    }

    @Test
    fun mastery_is_listed_by_system_and_titled_in_the_interface_language() = runTest(dispatcher) {
        // §9, screen 17: mastery by system.
        val model = model(locale = "en")
        advanceUntilIdle()

        val mastery = model.state.value.mastery
        assertEquals(listOf("Skeletal system", "Muscular system"), mastery.map { it.title })
        assertEquals(14, mastery.first().structuresSeen)
        assertEquals(0f, mastery.last().fraction)
    }

    @Test
    fun what_is_installed_is_counted_and_sized() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(1, model.state.value.installedPacks)
        assertEquals(48_300_000L, model.state.value.installedBytes)
    }

    @Test
    fun progress_that_cannot_be_read_is_a_failure_not_an_empty_calendar() = runTest(dispatcher) {
        // Review Focus 5. An empty calendar would say "you have studied nothing".
        val model = model(progress = FakeProgressRepository(FakeBehaviour(failure = { IllegalStateException("no store") })))
        advanceUntilIdle()

        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.calendar.isEmpty())
    }

    @Test
    fun retrying_after_a_failure_loads_the_profile() = runTest(dispatcher) {
        var broken = true
        val real = FakeProgressRepository()
        val flaky = object : ProgressRepository by real {
            override suspend fun masteryBySystem(locale: String) =
                if (broken) throw IllegalStateException("no store") else real.masteryBySystem(locale)
        }
        val model = model(progress = flaky)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)

        broken = false
        model.onRetry()
        advanceUntilIdle()

        assertFalse(model.state.value.failed)
        assertEquals(CALENDAR_DAYS, model.state.value.calendar.size)
    }

    @Test
    fun there_is_no_profile_where_its_repositories_refuse() {
        // All-screens spec §6.
        assertFalse(profileIsBuilt(NotBuiltProgressRepository, FakePackRepository()))
        assertFalse(profileIsBuilt(FakeProgressRepository(), NotBuiltPackRepository))
        assertTrue(profileIsBuilt(FakeProgressRepository(), FakePackRepository()))
    }

    @Test
    fun the_calendar_and_mastery_are_read_again_when_the_screen_is_come_back_to() = runTest(dispatcher) {
        // The model outlives a tab switch: a session finished on the Test tab was not in
        // the calendar until the app was restarted.
        var asked = 0
        val real = FakeProgressRepository()
        val counting = object : ProgressRepository by real {
            override suspend fun activity(from: LocalDate, to: LocalDate): Map<LocalDate, DayActivity> { asked++; return real.activity(from, to) }
        }
        val model = model(progress = counting)
        advanceUntilIdle()
        assertEquals(1, asked)

        model.refresh()
        advanceUntilIdle()

        assertEquals(2, asked)
        assertFalse(model.state.value.isLoading)
    }
}

package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.model.QuizSessionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeProgressRepositoryTest {

    @Test
    fun a_streak_is_offered_for_screens_14_and_17() = runTest {
        val streak = FakeProgressRepository().streak.first()

        assertTrue(streak.currentDays > 0)
        assertTrue(streak.longestDays >= streak.currentDays)
    }

    @Test
    fun mastery_is_reported_per_system_as_a_fraction() = runTest {
        val mastery = FakeProgressRepository().masteryBySystem("pl")

        assertTrue(mastery.isNotEmpty())
        assertTrue(mastery.all { it.fraction in 0f..1f })
    }

    @Test
    fun mastery_titles_follow_the_requested_locale() = runTest {
        val repository = FakeProgressRepository()

        assertEquals("Skeletal system", repository.masteryBySystem("en").first().title)
        assertEquals("Układ kostny", repository.masteryBySystem("pl").first().title)
    }

    @Test
    fun the_calendar_covers_every_day_in_the_range_including_both_ends() = runTest {
        val from = LocalDate(2026, 9, 1)
        val to = LocalDate(2026, 9, 7)

        val activity = FakeProgressRepository().activity(from, to)

        assertEquals(7, activity.size)
        assertTrue(from in activity && to in activity)
    }

    @Test
    fun a_recorded_session_is_kept_and_does_not_by_itself_move_the_streak() = runTest {
        val repository = FakeProgressRepository()
        val before = repository.streak.first().currentDays

        repository.recordSession(
            QuizSummary(QuizSessionId("s1"), correct = 4, total = 4, elapsedMillis = 5_000, needsReview = emptyList()),
        )

        assertEquals(before, repository.streak.first().currentDays)
        assertEquals(1, repository.recorded.size)
    }
}

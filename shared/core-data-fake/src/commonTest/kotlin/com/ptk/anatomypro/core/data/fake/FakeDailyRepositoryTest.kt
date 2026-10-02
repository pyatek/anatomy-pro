package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.DailyQuiz
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizQuestion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeDailyRepositoryTest {

    @Test
    fun a_fresh_day_has_not_been_started() = runTest {
        assertIs<DailyQuiz.NotStarted>(FakeDailyRepository().today())
    }

    @Test
    fun starting_fetches_questions_and_records_the_clock_start() = runTest {
        val repository = FakeDailyRepository()

        val started = repository.start(locale = "la")

        assertTrue(started.questions.isNotEmpty())
        assertIs<DailyQuiz.InProgress>(repository.today())
    }

    @Test
    fun offline_reports_unavailable_rather_than_throwing_because_it_is_a_designed_screen() = runTest {
        assertIs<DailyQuiz.Unavailable>(FakeDailyRepository(available = false).today())
    }

    @Test
    fun starting_while_offline_fails_because_the_daily_needs_a_connection() = runTest {
        assertFailsWith<IllegalStateException> { FakeDailyRepository(available = false).start(locale = "la") }
    }

    @Test
    fun one_attempt_per_day_is_enforced() = runTest {
        val repository = FakeDailyRepository(alreadyAttempted = true)

        assertIs<DailyQuiz.Completed>(repository.today())
        assertFailsWith<IllegalStateException> { repository.start(locale = "la") }
    }

    @Test
    fun submitting_scores_server_side_and_returns_a_rank() = runTest {
        val repository = FakeDailyRepository()
        val started = repository.start(locale = "la")
        val answers = started.questions.map { question ->
            val chosen = when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
            QuizAnswer(question.id, chosen, elapsedMillis = 1_000)
        }

        val result = repository.submit(answers)

        assertEquals(answers.size, result.correct)
        assertTrue((result.rank ?: 0) > 0)
        assertIs<DailyQuiz.Completed>(repository.today())
    }

    @Test
    fun the_daily_questions_are_named_in_the_requested_locale() = runTest {
        val started = FakeDailyRepository().start(locale = "en")

        val names = started.questions.flatMap { (it as QuizQuestion.NameTheHighlighted).options }.map { it.name }
        assertTrue(names.all { it.startsWith("Rib ") }, "$names")
    }
}

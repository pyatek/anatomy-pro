package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeProgressRepository
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QuizSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val ribs = QuizTopicId("costae")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** A model whose clock is the test's virtual time. */
    private fun TestScope.model(
        quiz: QuizRepository = FakeQuizRepository(),
        progress: ProgressRepository = FakeProgressRepository(),
    ) = QuizSessionViewModel(quiz, progress, nextSeed = { 42L }, now = { testScheduler.currentTime })

    /** Starts a naming session on the ribs with the question on screen. */
    private fun TestScope.started(
        model: QuizSessionViewModel,
        format: QuizFormat = QuizFormat.NAME_THE_HIGHLIGHTED,
        timed: Boolean = false,
    ): QuizStage.Asking {
        model.start(ribs, format, locale = "la", timed = timed)
        runCurrent()
        model.onQuestionShown()
        runCurrent()
        return assertIs<QuizStage.Asking>(model.stage.value)
    }

    private fun QuizStage.Asking.rightAnswer(): StructureId = when (val q = question) {
        is QuizQuestion.NameTheHighlighted -> q.correct.id
        is QuizQuestion.TapTheStructure -> q.target
    }

    private fun QuizStage.Asking.wrongAnswer(): StructureId =
        (question as QuizQuestion.NameTheHighlighted).options.first { it.id != rightAnswer() }.id

    // --- starting ---------------------------------------------------------------------

    @Test
    fun nothing_is_in_progress_until_a_topic_is_chosen() {
        assertEquals(QuizStage.Idle, QuizSessionViewModel(FakeQuizRepository(), FakeProgressRepository()).stage.value)
    }

    @Test
    fun starting_asks_the_first_of_ten_questions() = runTest(dispatcher) {
        val asking = started(model())

        assertEquals(0, asking.index)
        assertEquals(QUESTIONS_PER_SESSION, asking.total)
    }

    @Test
    fun the_session_is_started_in_the_examination_locale_it_was_given() = runTest(dispatcher) {
        // §13: names in a session follow the examination locale, whatever the interface reads in.
        val model = model()
        model.start(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, locale = "en", timed = false)
        runCurrent()

        val asking = assertIs<QuizStage.Asking>(model.stage.value)
        assertEquals("en", asking.session.locale)
        assertTrue((asking.question as QuizQuestion.NameTheHighlighted).options.all { it.name.startsWith("Rib ") })
    }

    @Test
    fun a_topic_too_small_to_ask_about_is_a_failure_to_start_not_a_crash() = runTest(dispatcher) {
        // Review Focus 5. The fake refuses a topic with fewer than four answerable structures.
        val model = model()

        model.start(QuizTopicId("skeletal"), QuizFormat.NAME_THE_HIGHLIGHTED, locale = "la", timed = false)
        runCurrent()

        assertEquals(QuizStage.Failed(FailedDuring.START), model.stage.value)
    }

    @Test
    fun a_failure_to_start_can_be_followed_by_a_start_that_works() = runTest(dispatcher) {
        val model = model()
        model.start(QuizTopicId("skeletal"), QuizFormat.NAME_THE_HIGHLIGHTED, locale = "la", timed = false)
        runCurrent()

        model.start(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, locale = "la", timed = false)
        runCurrent()

        assertIs<QuizStage.Asking>(model.stage.value)
    }

    // --- screen 09 and 10: asking -----------------------------------------------------

    @Test
    fun the_tap_format_asks_tap_questions_and_the_name_format_name_questions() = runTest(dispatcher) {
        assertIs<QuizQuestion.TapTheStructure>(started(model(), QuizFormat.TAP_THE_STRUCTURE).question)
        assertIs<QuizQuestion.NameTheHighlighted>(started(model(), QuizFormat.NAME_THE_HIGHLIGHTED).question)
    }

    @Test
    fun a_name_question_offers_four_options_and_none_is_chosen() = runTest(dispatcher) {
        // §9, screen 10.
        val asking = started(model())

        assertEquals(4, (asking.question as QuizQuestion.NameTheHighlighted).options.size)
        assertFalse(asking.submitting)
    }

    @Test
    fun an_untimed_question_left_on_screen_runs_nothing_in_the_background() = runTest(dispatcher) {
        // Elapsed time is read from the clock, not counted by a loop. A loop here would never
        // end, and nor would any test that left an untimed question on screen.
        val model = model()
        started(model, timed = false)

        assertEquals(0, testScheduler.currentTime)
        testScheduler.advanceUntilIdle()

        assertEquals(0, testScheduler.currentTime, "something was still scheduled for an untimed question")
    }

    @Test
    fun with_the_timer_on_the_question_counts_down() = runTest(dispatcher) {
        // §9, screen 09: timer running.
        val model = model()
        started(model, timed = true)

        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(QUESTION_MILLIS - 5_000, assertIs<QuizStage.Asking>(model.stage.value).remainingMillis)
    }

    @Test
    fun with_the_timer_off_there_is_no_countdown_and_no_time_limit() = runTest(dispatcher) {
        // §9, screen 09: timer disabled. §12 requires it to be possible.
        val model = model()
        started(model, timed = false)

        advanceTimeBy(QUESTION_MILLIS * 3)
        runCurrent()

        assertNull(assertIs<QuizStage.Asking>(model.stage.value).remainingMillis)
    }

    @Test
    fun when_the_time_runs_out_the_question_is_answered_with_nothing() = runTest(dispatcher) {
        // Review Focus 2.
        val model = model()
        started(model, timed = true)

        advanceTimeBy(QUESTION_MILLIS + TICK_MILLIS)
        runCurrent()

        val feedback = assertIs<QuizStage.Feedback>(model.stage.value)
        assertFalse(feedback.result.correct)
        assertNull(feedback.result.chosen)
    }

    @Test
    fun the_timer_stops_while_the_question_is_off_screen_and_resumes_where_it_was() = runTest(dispatcher) {
        // Review Focus 3. Looking something up in the atlas must not cost the question.
        val model = model()
        started(model, timed = true)
        advanceTimeBy(4_000)
        runCurrent()

        model.onQuestionHidden()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(QUESTION_MILLIS - 4_000, assertIs<QuizStage.Asking>(model.stage.value).remainingMillis)

        model.onQuestionShown()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(QUESTION_MILLIS - 5_000, assertIs<QuizStage.Asking>(model.stage.value).remainingMillis)
    }

    // --- screens 11 and 12: feedback --------------------------------------------------

    @Test
    fun a_right_answer_is_followed_by_correct_feedback() = runTest(dispatcher) {
        val model = model()
        val asking = started(model)

        model.answer(asking.rightAnswer())
        runCurrent()

        val feedback = assertIs<QuizStage.Feedback>(model.stage.value)
        assertTrue(feedback.result.correct)
        assertEquals(0, feedback.index)
    }

    @Test
    fun a_wrong_answer_is_followed_by_feedback_holding_both_structures() = runTest(dispatcher) {
        // §4.1: screen 12 needs the right one and the mistaken one in hand at once.
        val model = model()
        val asking = started(model)
        val wrong = asking.wrongAnswer()

        model.answer(wrong)
        runCurrent()

        val result = assertIs<QuizStage.Feedback>(model.stage.value).result
        assertFalse(result.correct)
        assertEquals(asking.rightAnswer(), result.expected.id)
        assertEquals(wrong, result.chosen?.id)
        assertEquals("costae", result.sharedAncestor?.id?.value)
    }

    @Test
    fun how_long_the_answer_took_is_measured_while_the_question_was_shown() = runTest(dispatcher) {
        val model = model()
        val asking = started(model)
        advanceTimeBy(3_000)
        runCurrent()

        model.answer(asking.rightAnswer())
        runCurrent()

        assertEquals(3_000L, assertIs<QuizStage.Feedback>(model.stage.value).result.elapsedMillis)
    }

    @Test
    fun an_answer_given_twice_is_submitted_once() = runTest(dispatcher) {
        // Review Focus 1. A double tap, or a tap as the timer runs out.
        var submissions = 0
        val real = FakeQuizRepository()
        val counting = object : QuizRepository by real {
            override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult {
                submissions++
                return real.submit(session, answer)
            }
        }
        val model = model(quiz = counting)
        val asking = started(model)

        model.answer(asking.rightAnswer())
        model.answer(asking.wrongAnswer())
        runCurrent()

        assertEquals(1, submissions)
        assertTrue(assertIs<QuizStage.Feedback>(model.stage.value).result.correct)
    }

    @Test
    fun an_answer_that_cannot_be_checked_leaves_the_question_to_be_answered_again() = runTest(dispatcher) {
        // Review Focus 4.
        var failNext = true
        val real = FakeQuizRepository()
        val flaky = object : QuizRepository by real {
            override suspend fun submit(session: QuizSessionId, answer: QuizAnswer): AnswerResult {
                if (failNext) { failNext = false; throw IllegalStateException("could not check") }
                return real.submit(session, answer)
            }
        }
        val model = model(quiz = flaky)
        val asking = started(model)

        model.answer(asking.rightAnswer())
        runCurrent()
        val again = assertIs<QuizStage.Asking>(model.stage.value)
        assertTrue(again.submitFailed)
        assertFalse(again.submitting)
        assertEquals(0, again.index)

        model.answer(asking.rightAnswer())
        runCurrent()
        assertIs<QuizStage.Feedback>(model.stage.value)
    }

    @Test
    fun a_tap_arriving_during_feedback_changes_nothing() = runTest(dispatcher) {
        // The model stays on screen during feedback and can still be tapped.
        val model = model()
        val asking = started(model)
        model.answer(asking.rightAnswer())
        runCurrent()
        val feedback = model.stage.value

        model.answer(asking.wrongAnswer())
        runCurrent()

        assertEquals(feedback, model.stage.value)
    }

    // --- moving on, and screen 13 -----------------------------------------------------

    @Test
    fun next_asks_the_following_question_with_a_fresh_clock() = runTest(dispatcher) {
        val model = model()
        val first = started(model, timed = true)
        advanceTimeBy(7_000)
        model.answer(first.rightAnswer())
        runCurrent()

        model.next()
        runCurrent()
        model.onQuestionShown()
        runCurrent()

        val second = assertIs<QuizStage.Asking>(model.stage.value)
        assertEquals(1, second.index)
        assertEquals(QUESTION_MILLIS, second.remainingMillis)
    }

    @Test
    fun feedback_on_the_last_question_knows_it_is_the_last() = runTest(dispatcher) {
        val model = model()
        answerEverything(model, rightly = true)

        assertTrue(assertIs<QuizStage.Feedback>(model.stage.value).isLast)
    }

    @Test
    fun a_session_answered_perfectly_finishes_with_nothing_to_review() = runTest(dispatcher) {
        // §9, screen 13: perfect score.
        val model = model()
        answerEverything(model, rightly = true)
        model.next()
        runCurrent()

        val finished = assertIs<QuizStage.Finished>(model.stage.value)
        assertEquals(QUESTIONS_PER_SESSION, finished.summary.correct)
        assertTrue(finished.summary.needsReview.isEmpty())
        assertEquals(ribs, finished.topic)
    }

    @Test
    fun a_session_with_misses_finishes_with_them_listed_for_review() = runTest(dispatcher) {
        // §9, screen 13: score with review list.
        val model = model()
        answerEverything(model, rightly = false)
        model.next()
        runCurrent()

        val summary = assertIs<QuizStage.Finished>(model.stage.value).summary
        assertEquals(0, summary.correct)
        assertEquals(QUESTIONS_PER_SESSION, summary.needsReview.size)
    }

    @Test
    fun a_finished_session_is_recorded_as_progress() = runTest(dispatcher) {
        val progress = FakeProgressRepository()
        val model = model(progress = progress)
        answerEverything(model, rightly = true)
        model.next()
        runCurrent()

        assertEquals(1, progress.recorded.size)
        assertEquals(QUESTIONS_PER_SESSION, progress.recorded.single().total)
    }

    @Test
    fun a_session_that_cannot_be_recorded_still_shows_its_summary() = runTest(dispatcher) {
        // Decision 6. Losing the record is not the student's problem at this moment.
        val broken = FakeProgressRepository(FakeBehaviour(failure = { IllegalStateException("no store") }))
        val model = model(progress = broken)
        answerEverything(model, rightly = true)
        model.next()
        runCurrent()

        assertIs<QuizStage.Finished>(model.stage.value)
    }

    @Test
    fun abandoning_a_session_returns_to_nothing_in_progress_and_records_nothing() = runTest(dispatcher) {
        val progress = FakeProgressRepository()
        val model = model(progress = progress)
        started(model, timed = true)

        model.abandon()
        advanceTimeBy(QUESTION_MILLIS * 2)
        runCurrent()

        assertEquals(QuizStage.Idle, model.stage.value)
        assertTrue(progress.recorded.isEmpty())
    }

    /** Answers every question, leaving the model on the last question's feedback. */
    private fun TestScope.answerEverything(model: QuizSessionViewModel, rightly: Boolean) {
        var asking = started(model)
        while (true) {
            model.answer(if (rightly) asking.rightAnswer() else asking.wrongAnswer())
            runCurrent()
            val feedback = assertIs<QuizStage.Feedback>(model.stage.value)
            if (feedback.isLast) return
            model.next()
            runCurrent()
            model.onQuestionShown()
            runCurrent()
            asking = assertIs<QuizStage.Asking>(model.stage.value)
        }
    }
}

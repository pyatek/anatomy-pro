package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.data.NotBuiltQuizRepository
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.data.model.QuizSummary
import com.ptk.anatomypro.core.designsystem.HighlightTokens
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizCanvasTest {

    private val quiz = FakeQuizRepository()
    private val ribs = QuizTopicId("costae")

    private suspend fun session(format: QuizFormat): QuizSession =
        quiz.startSession(ribs, format, questionCount = 3, seed = 9, locale = "la")

    private fun QuizSession.expected(index: Int): StructureId = when (val q = questions[index]) {
        is QuizQuestion.NameTheHighlighted -> q.correct.id
        is QuizQuestion.TapTheStructure -> q.target
    }

    private suspend fun feedback(session: QuizSession, chosen: StructureId?): QuizStage.Feedback =
        QuizStage.Feedback(session, 0, quiz.submit(session.id, QuizAnswer(session.questions[0].id, chosen, 1_000)))

    // --- the canvas -------------------------------------------------------------------

    @Test
    fun a_tap_question_shows_the_whole_model_with_nothing_marked() = runTest {
        // Framing or highlighting the target would be the answer.
        val canvas = quizCanvasFor(QuizStage.Asking(session(QuizFormat.TAP_THE_STRUCTURE), 0, remainingMillis = null))

        assertTrue(canvas.highlights.isEmpty())
        assertNull(canvas.focus)
        assertTrue(canvas.pickable)
    }

    @Test
    fun a_name_question_highlights_and_frames_its_structure_and_ignores_taps() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        val canvas = quizCanvasFor(QuizStage.Asking(session, 0, remainingMillis = null))

        assertEquals(mapOf(session.expected(0) to HighlightTokens.Selected), canvas.highlights)
        assertEquals(session.expected(0), canvas.focus)
        assertFalse(canvas.pickable)
    }

    @Test
    fun correct_feedback_marks_the_one_structure_as_correct() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        val canvas = quizCanvasFor(feedback(session, session.expected(0)))

        assertEquals(mapOf(session.expected(0) to HighlightTokens.Correct), canvas.highlights)
        assertFalse(canvas.pickable)
    }

    @Test
    fun incorrect_feedback_shows_the_expected_and_the_chosen_together_each_in_its_own_style() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)
        val wrong = (session.questions[0] as QuizQuestion.NameTheHighlighted).options.first { it.id != session.expected(0) }.id

        val canvas = quizCanvasFor(feedback(session, wrong))

        assertEquals(
            mapOf(session.expected(0) to HighlightTokens.Correct, wrong to HighlightTokens.Incorrect),
            canvas.highlights,
        )
        assertEquals(session.expected(0), canvas.focus)
    }

    @Test
    fun feedback_with_no_chosen_structure_shows_only_the_expected_one() = runTest {
        // Review Focus 2: the timer ran out.
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        val canvas = quizCanvasFor(feedback(session, chosen = null))

        assertEquals(mapOf(session.expected(0) to HighlightTokens.Correct), canvas.highlights)
    }

    @Test
    fun nothing_is_marked_when_no_question_is_on_screen() {
        assertTrue(quizCanvasFor(QuizStage.Idle).highlights.isEmpty())
        assertTrue(quizCanvasFor(QuizStage.Starting).highlights.isEmpty())
    }

    // --- screen 12 --------------------------------------------------------------------

    @Test
    fun the_expected_and_the_chosen_answer_differ_by_more_than_hue() = runTest {
        // All-screens spec §10, test 1. Screen 12 ships without §12's outlines; this is what
        // keeps that shortcut honest. Glyph and outline style are shape; the direction of
        // the luminance shift is brightness. None of the three is colour.
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)
        val wrong = (session.questions[0] as QuizQuestion.NameTheHighlighted).options.first { it.id != session.expected(0) }.id

        val marks = answerMarks(feedback(session, wrong).result)
        val expected = marks.single { it.role == AnswerRole.Expected }
        val chosen = marks.single { it.role == AnswerRole.Chosen }

        assertNotEquals(expected.glyph, chosen.glyph)
        assertNotEquals(expected.style.outlineStyle, chosen.style.outlineStyle)
        assertTrue(expected.style.fillLuminanceShift > 0f, "the expected answer must be lighter")
        assertTrue(chosen.style.fillLuminanceShift < 0f, "the chosen answer must be darker")
        assertNotEquals(expected.style.outlineArgb, chosen.style.outlineArgb)
    }

    @Test
    fun a_right_answer_has_one_mark_and_the_expected_answer_comes_first_when_there_are_two() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)
        val wrong = (session.questions[0] as QuizQuestion.NameTheHighlighted).options.first { it.id != session.expected(0) }.id

        assertEquals(listOf(AnswerRole.Expected), answerMarks(feedback(session, session.expected(0)).result).map { it.role })
        assertEquals(
            listOf(AnswerRole.Expected, AnswerRole.Chosen),
            answerMarks(feedback(session, wrong).result).map { it.role },
        )
    }

    // --- navigation -------------------------------------------------------------------

    @Test
    fun a_question_and_its_feedback_are_the_same_destination() = runTest {
        // One destination, so the canvas is not torn down and its pack reloaded between them.
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)
        val asking = QuizStage.Asking(session, 0, remainingMillis = 30_000)

        assertEquals(QuizDestination.Session(session.id.value), destinationOf(asking))
        assertEquals(destinationOf(asking), destinationOf(feedback(session, session.expected(0))))
    }

    @Test
    fun the_clock_ticking_and_the_next_question_arriving_do_not_change_the_destination() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        assertEquals(
            destinationOf(QuizStage.Asking(session, 0, remainingMillis = 30_000)),
            destinationOf(QuizStage.Asking(session, 2, remainingMillis = 100, submitting = true)),
        )
    }

    @Test
    fun a_finished_session_is_at_its_summary() {
        val summary = QuizSummary(QuizSessionId("session-0"), correct = 3, total = 3, elapsedMillis = 9_000, needsReview = emptyList())

        assertEquals(
            QuizDestination.Summary("session-0"),
            destinationOf(QuizStage.Finished(summary, ribs, QuizFormat.NAME_THE_HIGHLIGHTED)),
        )
    }

    @Test
    fun with_nothing_in_progress_or_after_a_failure_the_place_to_be_is_the_topic_grid() {
        assertEquals(QuizDestination.Topics, destinationOf(QuizStage.Idle))
        assertEquals(QuizDestination.Topics, destinationOf(QuizStage.Starting))
        assertEquals(QuizDestination.Topics, destinationOf(QuizStage.Failed(FailedDuring.START)))
        assertEquals(QuizDestination.Topics, destinationOf(QuizStage.Failed(FailedDuring.FINISH)))
    }

    // --- production -------------------------------------------------------------------

    @Test
    fun the_quiz_is_not_built_where_the_repository_refuses() {
        // All-screens spec §6. Production's QuizRepository throws on first use, so the Test
        // tab must not construct a quiz screen there.
        assertFalse(quizIsBuilt(NotBuiltQuizRepository))
        assertTrue(quizIsBuilt(FakeQuizRepository()))
    }
}

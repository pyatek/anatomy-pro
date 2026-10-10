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
    fun a_tap_question_marks_nothing_and_frames_everything_the_session_asks_about() = runTest {
        // Highlighting the target, or framing it alone, would be the answer. Framing the
        // whole model made a rib too small to tap; the session's targets together are the
        // region being asked about, and the frame is the same for every question in it.
        val session = session(QuizFormat.TAP_THE_STRUCTURE)
        val targets = session.questions.indices.map { session.expected(it) }.toSet()

        val canvas = quizCanvasFor(QuizStage.Asking(session, 0, remainingMillis = null))

        assertTrue(canvas.highlights.isEmpty())
        assertTrue(canvas.pickable)
        assertEquals(targets, canvas.framedWith + setOfNotNull(canvas.focus))
    }

    @Test
    fun every_tap_question_in_a_session_is_framed_alike_so_the_frame_says_nothing() = runTest {
        val session = session(QuizFormat.TAP_THE_STRUCTURE)

        val frames = session.questions.indices.map { index ->
            val canvas = quizCanvasFor(QuizStage.Asking(session, index, remainingMillis = null))
            canvas.focus to canvas.framedWith
        }

        assertEquals(1, frames.distinct().size)
    }

    @Test
    fun a_tap_session_about_fewer_than_three_structures_shows_the_whole_model() = runTest {
        // Framing one or two structures would all but point at the answer.
        val session = session(QuizFormat.TAP_THE_STRUCTURE)
        val short = session.copy(questions = session.questions.take(2))

        val canvas = quizCanvasFor(QuizStage.Asking(short, 0, remainingMillis = null))

        assertNull(canvas.focus)
        assertTrue(canvas.framedWith.isEmpty())
    }

    // --- the system back gesture ------------------------------------------------------

    @Test
    fun back_on_a_question_or_its_feedback_is_swallowed_because_end_session_is_the_way_out() = runTest {
        // Popping to the topic grid sent the session straight back and rebuilt the canvas.
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        assertEquals(BackAction.Ignore, backActionFor(QuizStage.Asking(session, 0, remainingMillis = null)))
        assertEquals(BackAction.Ignore, backActionFor(feedback(session, session.expected(0))))
    }

    @Test
    fun back_on_the_summary_leaves_it_as_back_to_topics_does() {
        val summary = QuizSummary(QuizSessionId("s"), correct = 1, total = 1, elapsedMillis = 1, needsReview = emptyList())

        assertEquals(BackAction.Leave, backActionFor(QuizStage.Finished(summary, ribs, QuizFormat.NAME_THE_HIGHLIGHTED)))
    }

    @Test
    fun back_is_left_alone_when_no_session_is_running() {
        assertEquals(BackAction.PassOn, backActionFor(QuizStage.Idle))
        assertEquals(BackAction.PassOn, backActionFor(QuizStage.Starting))
        assertEquals(BackAction.PassOn, backActionFor(QuizStage.Failed(FailedDuring.START)))
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
    fun incorrect_feedback_frames_the_chosen_structure_together_with_the_expected_one() = runTest {
        // Screen 12: framing the expected answer alone often left the chosen one out of view.
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)
        val wrong = (session.questions[0] as QuizQuestion.NameTheHighlighted).options.first { it.id != session.expected(0) }.id

        val canvas = quizCanvasFor(feedback(session, wrong))

        assertEquals(session.expected(0), canvas.focus)
        assertEquals(setOf(wrong), canvas.framedWith)
    }

    @Test
    fun correct_feedback_and_a_question_frame_one_structure_alone() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED)

        assertTrue(quizCanvasFor(feedback(session, session.expected(0))).framedWith.isEmpty())
        assertTrue(quizCanvasFor(feedback(session, chosen = null)).framedWith.isEmpty())
        assertTrue(quizCanvasFor(QuizStage.Asking(session, 0, remainingMillis = null)).framedWith.isEmpty())
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

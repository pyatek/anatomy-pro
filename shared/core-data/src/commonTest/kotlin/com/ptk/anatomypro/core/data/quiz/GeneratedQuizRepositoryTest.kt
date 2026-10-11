package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.QuizSession
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratedQuizRepositoryTest {

    private val asked = mutableListOf<String>()
    private val repository = GeneratedQuizRepository { locale ->
        asked += locale
        SAMPLE_QUIZ_NODES
    }
    private val ribs = QuizTopicId("costae")

    private suspend fun session(format: QuizFormat = QuizFormat.TAP_THE_STRUCTURE, seed: Long = 11): QuizSession =
        repository.startSession(ribs, format, questionCount = 5, seed = seed, locale = "la")

    private fun QuizSession.target(index: Int): StructureId = when (val q = questions[index]) {
        is QuizQuestion.NameTheHighlighted -> q.correct.id
        is QuizQuestion.TapTheStructure -> q.target
    }

    private fun mirrorOf(id: StructureId): StructureId = StructureId(
        if (id.value.endsWith("-left")) id.value.removeSuffix("-left") + "-right" else id.value.removeSuffix("-right") + "-left",
    )

    @Test
    fun topics_and_sessions_are_read_from_the_atlas_in_the_language_asked_for() = runTest {
        assertEquals(3, repository.topics("pl").size)
        repository.startSession(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, 3, seed = 1, locale = "en")

        assertEquals(listOf("pl", "en"), asked)
    }

    @Test
    fun a_session_records_what_reproduces_it() = runTest {
        val session = session(QuizFormat.NAME_THE_HIGHLIGHTED, seed = 77)

        assertEquals(77, session.seed)
        assertEquals("la", session.locale)
        assertEquals(ribs, session.topic)
        assertEquals(5, session.questions.size)
        assertNotEquals(session.id, session(QuizFormat.NAME_THE_HIGHLIGHTED, seed = 77).id)
    }

    @Test
    fun the_expected_structure_is_a_right_answer() = runTest {
        val session = session()

        val result = repository.submit(session.id, QuizAnswer(session.questions[0].id, session.target(0), 1_200))

        assertTrue(result.correct)
        assertEquals(session.target(0), result.expected.id)
        assertEquals(1_200, result.elapsedMillis)
    }

    @Test
    fun the_other_side_is_a_right_answer_and_is_the_one_shown_as_right() = runTest {
        // The question named no side. What the student tapped is marked, not its twin.
        val session = session()
        val mirror = mirrorOf(session.target(0))

        val result = repository.submit(session.id, QuizAnswer(session.questions[0].id, mirror, 900))

        assertTrue(result.correct)
        assertEquals(mirror, result.expected.id)
    }

    @Test
    fun a_wrong_answer_names_both_structures_and_what_they_share() = runTest {
        val session = session()
        val wrong = session.target(1)

        val result = repository.submit(session.id, QuizAnswer(session.questions[0].id, wrong, 900))

        assertFalse(result.correct)
        assertEquals(session.target(0), result.expected.id)
        assertEquals(wrong, result.chosen?.id)
        assertEquals("costae", result.sharedAncestor?.id?.value)
    }

    @Test
    fun a_tap_outside_the_atlas_and_a_question_that_timed_out_are_wrong_with_nothing_to_name() = runTest {
        val session = session()

        val stray = repository.submit(session.id, QuizAnswer(session.questions[0].id, StructureId("not-in-the-atlas"), 500))
        val late = repository.submit(session.id, QuizAnswer(session.questions[1].id, chosen = null, elapsedMillis = 30_000))

        assertFalse(stray.correct)
        assertNull(stray.chosen)
        assertFalse(late.correct)
        assertNull(late.chosen)
        assertNull(late.sharedAncestor)
    }

    @Test
    fun the_summary_counts_what_was_answered_and_lists_the_slowest_miss_first() = runTest {
        val session = session()
        repository.submit(session.id, QuizAnswer(session.questions[0].id, session.target(0), 1_000))
        repository.submit(session.id, QuizAnswer(session.questions[1].id, session.target(0), 2_000))
        repository.submit(session.id, QuizAnswer(session.questions[2].id, chosen = null, elapsedMillis = 30_000))

        val summary = repository.finish(session.id)

        assertEquals(1, summary.correct)
        assertEquals(3, summary.total)
        assertEquals(33_000, summary.elapsedMillis)
        assertEquals(listOf(session.target(2), session.target(1)), summary.needsReview.map { it.id })
    }

    @Test
    fun an_unknown_session_is_refused() = runTest {
        val session = session()

        assertFailsWith<IllegalArgumentException> {
            repository.submit(QuizSessionId("no-such-session"), QuizAnswer(session.questions[0].id, null, 1))
        }
    }

    @Test
    fun only_the_last_few_sessions_are_kept_so_abandoned_ones_do_not_pile_up() = runTest {
        // A session left by its "end" button is never finished, and each one held a copy of
        // the whole atlas for as long as the app ran.
        val first = session()
        val later = (1..GeneratedQuizRepository.SESSIONS_KEPT).map { session(seed = it.toLong()) }

        assertFailsWith<IllegalArgumentException> {
            repository.submit(first.id, QuizAnswer(first.questions[0].id, null, 1))
        }
        val newest = later.last()
        assertTrue(repository.submit(newest.id, QuizAnswer(newest.questions[0].id, newest.target(0), 1)).correct)
    }
}

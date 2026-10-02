package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizAnswer
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FakeQuizRepositoryTest {

    private val repository = FakeQuizRepository()
    private val ribs = QuizTopicId("costae")

    private suspend fun session(count: Int = 4) =
        repository.startSession(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, count, seed = 1L)

    @Test
    fun a_topic_grid_is_offered_with_mastery_as_a_fraction() = runTest {
        val topics = repository.topics("pl")

        assertTrue(topics.isNotEmpty())
        assertTrue(topics.all { it.mastery in 0f..1f })
    }

    @Test
    fun the_same_seed_produces_the_same_questions_so_a_bad_set_is_reproducible() = runTest {
        // Compare what is asked, not the ids: ids are derived from the seed and position,
        // so they would match even if the targets and options did not.
        fun asked(questions: List<QuizQuestion>) = questions.map { question ->
            question as QuizQuestion.NameTheHighlighted
            question.highlighted.value to question.options.map { it.id.value }
        }

        assertEquals(asked(session().questions), asked(session().questions))
    }

    @Test
    fun a_different_seed_asks_something_different() = runTest {
        val one = repository.startSession(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, 4, seed = 1L)
        val two = repository.startSession(ribs, QuizFormat.NAME_THE_HIGHLIGHTED, 4, seed = 2L)

        assertNotEquals(
            one.questions.map { (it as QuizQuestion.NameTheHighlighted).highlighted },
            two.questions.map { (it as QuizQuestion.NameTheHighlighted).highlighted },
        )
    }

    @Test
    fun no_unverified_structure_is_ever_the_expected_answer() = runTest {
        val unverified = AtlasFixture.all
            .filter { it.verification["la"] != VerificationState.VERIFIED }
            .map { it.id }
            .toSet()

        val expected = session(count = 12).questions.map { question ->
            when (question) {
                is QuizQuestion.NameTheHighlighted -> question.correct.id
                is QuizQuestion.TapTheStructure -> question.target
            }
        }

        assertTrue(unverified.isNotEmpty(), "the fixture must contain an unverified structure")
        assertTrue(expected.none { it in unverified }, "an unverified structure was used as an answer")
    }

    @Test
    fun a_right_answer_scores_correct() = runTest {
        val session = session()
        val question = session.questions.first() as QuizQuestion.NameTheHighlighted

        val result = repository.submit(
            session.id,
            QuizAnswer(question.id, question.correct.id, elapsedMillis = 1_200),
        )

        assertTrue(result.correct)
        assertEquals(question.correct.id, result.expected.id)
    }

    @Test
    fun a_wrong_answer_carries_both_structures_and_where_they_diverge() = runTest {
        val session = session()
        val question = session.questions.first() as QuizQuestion.NameTheHighlighted
        val wrong = question.options.first { it.id != question.correct.id }

        val result = repository.submit(session.id, QuizAnswer(question.id, wrong.id, elapsedMillis = 900))

        assertFalse(result.correct)
        assertEquals(wrong.id, result.chosen?.id)
        assertEquals(StructureId("costae"), result.sharedAncestor?.id)
    }

    @Test
    fun a_timed_out_question_has_no_chosen_structure_and_is_not_correct() = runTest {
        val session = session()
        val question = session.questions.first()

        val result = repository.submit(session.id, QuizAnswer(question.id, chosen = null, elapsedMillis = 30_000))

        assertFalse(result.correct)
        assertEquals(null, result.chosen)
    }

    @Test
    fun the_summary_counts_what_was_submitted_and_lists_the_misses_for_review() = runTest {
        val session = session(count = 2)
        val first = session.questions[0] as QuizQuestion.NameTheHighlighted
        val second = session.questions[1] as QuizQuestion.NameTheHighlighted
        val wrong = second.options.first { it.id != second.correct.id }

        repository.submit(session.id, QuizAnswer(first.id, first.correct.id, 1_000))
        repository.submit(session.id, QuizAnswer(second.id, wrong.id, 1_000))

        val summary = repository.finish(session.id)

        assertEquals(1, summary.correct)
        assertEquals(2, summary.total)
        assertEquals(listOf(second.correct.id), summary.needsReview.map { it.id })
    }
}

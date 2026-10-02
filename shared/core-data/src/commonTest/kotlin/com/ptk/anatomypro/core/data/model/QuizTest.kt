package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.StructureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun summary(id: String) = StructureSummary(
    id = StructureId(id),
    name = id,
    latinName = id,
    laterality = Laterality.MEDIAN,
    isGroup = false,
    hasChildren = false,
)

class QuizTest {

    private val options = listOf(summary("costa-i"), summary("costa-ii"), summary("costa-iii"), summary("costa-iv"))

    @Test
    fun a_multiple_choice_question_names_its_correct_option() {
        val question = QuizQuestion.NameTheHighlighted(
            id = QuizQuestionId("q1"),
            difficulty = Difficulty.HARD,
            highlighted = StructureId("costa-ii"),
            options = options,
            correctIndex = 1,
        )

        assertEquals(summary("costa-ii"), question.correct)
    }

    @Test
    fun a_correct_index_outside_the_options_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            QuizQuestion.NameTheHighlighted(
                id = QuizQuestionId("q1"),
                difficulty = Difficulty.HARD,
                highlighted = StructureId("costa-ii"),
                options = options,
                correctIndex = 4,
            )
        }
    }

    @Test
    fun repeated_options_are_rejected_because_two_identical_answers_cannot_both_be_wrong() {
        assertFailsWith<IllegalArgumentException> {
            QuizQuestion.NameTheHighlighted(
                id = QuizQuestionId("q1"),
                difficulty = Difficulty.EASY,
                highlighted = StructureId("costa-i"),
                options = listOf(summary("costa-i"), summary("costa-i")),
                correctIndex = 0,
            )
        }
    }

    @Test
    fun a_summary_scores_as_a_fraction_and_survives_an_empty_session() {
        val scored = QuizSummary(QuizSessionId("s1"), correct = 3, total = 4, elapsedMillis = 0, needsReview = emptyList())
        val empty = QuizSummary(QuizSessionId("s2"), correct = 0, total = 0, elapsedMillis = 0, needsReview = emptyList())

        assertEquals(0.75f, scored.fraction)
        assertEquals(0f, empty.fraction)
    }
}

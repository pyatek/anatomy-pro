package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.QuizQuestionId
import com.ptk.anatomypro.core.model.QuizSessionId
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId

/** The two v1 formats (spec §8). Both generate from the structure graph. */
enum class QuizFormat { TAP_THE_STRUCTURE, NAME_THE_HIGHLIGHTED }

/** §8.1's single lever: how close the distractors sit in the structure graph. */
enum class Difficulty { EASY, MEDIUM, HARD }

/** Screen 08's grid cell. */
data class QuizTopic(
    val id: QuizTopicId,
    val title: String,
    val system: SystemId,
    val structureCount: Int,
    /** 0f to 1f. Screen 08 shows this as progress, never as a percentage of nothing. */
    val mastery: Float,
) {
    init { require(mastery in 0f..1f) { "mastery must be a fraction, was $mastery" } }
}

sealed interface QuizQuestion {
    val id: QuizQuestionId
    val difficulty: Difficulty

    /** Screen 09: a name is shown, the body is tapped. */
    data class TapTheStructure(
        override val id: QuizQuestionId,
        override val difficulty: Difficulty,
        val prompt: String,
        val promptLocale: String,
        val target: StructureId,
    ) : QuizQuestion

    /** Screen 10: one structure is highlighted, options are offered. */
    data class NameTheHighlighted(
        override val id: QuizQuestionId,
        override val difficulty: Difficulty,
        val highlighted: StructureId,
        val options: List<StructureSummary>,
        val correctIndex: Int,
    ) : QuizQuestion {
        init {
            require(correctIndex in options.indices) {
                "correctIndex $correctIndex is outside ${options.size} options"
            }
            require(options.distinctBy { it.id }.size == options.size) {
                "options must be distinct; two identical answers cannot both be wrong"
            }
        }

        val correct: StructureSummary get() = options[correctIndex]
    }
}

/** What the user did. [chosen] is null when a timed question ran out. */
data class QuizAnswer(
    val questionId: QuizQuestionId,
    val chosen: StructureId?,
    val elapsedMillis: Long,
)

data class QuizSession(
    val id: QuizSessionId,
    val topic: QuizTopicId,
    val format: QuizFormat,
    val questions: List<QuizQuestion>,
    /** Recorded so a bad question set can be reproduced (spec §8.2). */
    val seed: Long,
)

/**
 * Shaped by screen 12 rather than screen 11.
 *
 * The incorrect state needs both structures at once — the right one and the mistaken one —
 * and [sharedAncestor] is what lets it say how they relate rather than only that they differ.
 */
data class AnswerResult(
    val questionId: QuizQuestionId,
    val correct: Boolean,
    val expected: StructureSummary,
    val chosen: StructureSummary?,
    val sharedAncestor: StructureSummary?,
    val elapsedMillis: Long,
)

data class QuizSummary(
    val session: QuizSessionId,
    val correct: Int,
    val total: Int,
    val elapsedMillis: Long,
    /** Screen 13's review list, worst first. */
    val needsReview: List<StructureSummary>,
) {
    /** 0f for an empty session rather than a division by zero on a screen. */
    val fraction: Float get() = if (total == 0) 0f else correct.toFloat() / total
}

package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.data.NotBuiltQuizRepository
import com.ptk.anatomypro.core.data.model.AnswerResult
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.QuizRepository
import com.ptk.anatomypro.core.designsystem.HighlightTokens
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.HighlightStyle

/** What the 3D canvas shows for a stage of the quiz. */
data class QuizCanvas(
    val highlights: Map<StructureId, HighlightStyle>,
    /** What the camera should frame; null is the whole model. */
    val focus: StructureId?,
    /** Whether a tap on the model is an answer. */
    val pickable: Boolean,
    /** Framed in the same shot as [focus]: on screen 12, the answer that was chosen. */
    val framedWith: Set<StructureId> = emptySet(),
) {
    companion object {
        val Empty = QuizCanvas(highlights = emptyMap(), focus = null, pickable = false)
    }
}

fun quizCanvasFor(stage: QuizStage): QuizCanvas = when (stage) {
    is QuizStage.Asking -> when (val question = stage.question) {
        // The whole model, unmarked: framing or highlighting the target would answer it.
        is QuizQuestion.TapTheStructure -> QuizCanvas(emptyMap(), focus = null, pickable = !stage.submitting)
        is QuizQuestion.NameTheHighlighted -> QuizCanvas(
            highlights = mapOf(question.highlighted to HighlightTokens.Selected),
            focus = question.highlighted,
            pickable = false,
        )
    }

    is QuizStage.Feedback -> {
        val marks = answerMarks(stage.result)
        QuizCanvas(
            highlights = marks.associate { it.structure.id to it.style },
            focus = stage.result.expected.id,
            pickable = false,
            // Both answers in one shot: framing the expected one alone often left the
            // chosen one out of view, and the screen is about the difference between them.
            framedWith = marks.filter { it.role == AnswerRole.Chosen }.map { it.structure.id }.toSet(),
        )
    }

    else -> QuizCanvas.Empty
}

enum class AnswerRole { Expected, Chosen }

/**
 * One structure on the feedback screen, with everything that tells it from the other: a
 * glyph and an outline style for shape, a highlight style for hue and brightness.
 */
data class AnswerMark(
    val role: AnswerRole,
    val structure: StructureSummary,
    val glyph: String,
    val style: HighlightStyle,
)

/**
 * The expected answer, and beside it the chosen one when it was wrong and can be named.
 *
 * A timed-out question, and a tap on something outside the topic, have nothing to put
 * beside the expected answer.
 */
fun answerMarks(result: AnswerResult): List<AnswerMark> = buildList {
    add(AnswerMark(AnswerRole.Expected, result.expected, glyph = "✓", style = HighlightTokens.Correct))
    val chosen = result.chosen
    if (!result.correct && chosen != null && chosen.id != result.expected.id) {
        add(AnswerMark(AnswerRole.Chosen, chosen, glyph = "✕", style = HighlightTokens.Incorrect))
    }
}

/** Where the quiz's navigation should be for a stage. */
sealed interface QuizDestination {
    data object Topics : QuizDestination

    /**
     * Questions and their feedback, all of them. One destination for a whole session: the
     * canvas reloads its pack whenever it is recreated, and must not be between a question
     * and its feedback.
     */
    data class Session(val sessionId: String) : QuizDestination

    data class Summary(val sessionId: String) : QuizDestination
}

fun destinationOf(stage: QuizStage): QuizDestination = when (stage) {
    is QuizStage.Asking -> QuizDestination.Session(stage.session.id.value)
    is QuizStage.Feedback -> QuizDestination.Session(stage.session.id.value)
    is QuizStage.Finished -> QuizDestination.Summary(stage.summary.session.value)
    QuizStage.Idle, QuizStage.Starting, is QuizStage.Failed -> QuizDestination.Topics
}

/**
 * Whether there is a quiz to show.
 *
 * Production has no QuizRepository yet — the one it is given throws on first use, by design
 * (all-screens spec §6). There the Test tab keeps its placeholder. When a real repository is
 * built this returns true everywhere and can be deleted.
 */
fun quizIsBuilt(quiz: QuizRepository): Boolean = quiz !is NotBuiltQuizRepository

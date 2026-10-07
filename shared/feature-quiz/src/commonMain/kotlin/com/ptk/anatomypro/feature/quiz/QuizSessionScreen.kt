package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_quiz.generated.resources.Res
import anatomypro.shared.feature_quiz.generated.resources.feedback_chosen
import anatomypro.shared.feature_quiz.generated.resources.feedback_correct
import anatomypro.shared.feature_quiz.generated.resources.feedback_expected
import anatomypro.shared.feature_quiz.generated.resources.feedback_finish
import anatomypro.shared.feature_quiz.generated.resources.feedback_incorrect
import anatomypro.shared.feature_quiz.generated.resources.feedback_next
import anatomypro.shared.feature_quiz.generated.resources.feedback_no_choice
import anatomypro.shared.feature_quiz.generated.resources.feedback_shared
import anatomypro.shared.feature_quiz.generated.resources.question_end
import anatomypro.shared.feature_quiz.generated.resources.question_name_instruction
import anatomypro.shared.feature_quiz.generated.resources.question_progress
import anatomypro.shared.feature_quiz.generated.resources.question_prompt_locale
import anatomypro.shared.feature_quiz.generated.resources.question_submit_failed
import anatomypro.shared.feature_quiz.generated.resources.question_submitting
import anatomypro.shared.feature_quiz.generated.resources.question_tap_instruction
import anatomypro.shared.feature_quiz.generated.resources.question_time_left
import anatomypro.shared.feature_quiz.generated.resources.question_untimed
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.CorrectGreen
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.StructureId
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screens 09 to 12: a question, then what became of the answer.
 *
 * One composable for all four so that [canvas] is called from one place. The renderer
 * behind it is created with the composable that calls it and reloads its pack when it is
 * recreated; a screen per stage would blank the model between every question and its
 * feedback.
 *
 * Screen 12 is unfinished: see [SCREEN_12_UNFINISHED].
 */
@Composable
fun QuizSessionScreen(
    stage: QuizStage,
    canvas: @Composable (Modifier) -> Unit,
    onAnswer: (StructureId) -> Unit,
    onNext: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (index, total) = when (stage) {
        is QuizStage.Asking -> stage.index to stage.total
        is QuizStage.Feedback -> stage.index to stage.session.questions.size
        else -> 0 to 0
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (total > 0) stringResource(Res.string.question_progress, index + 1, total) else "",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                stringResource(Res.string.question_end),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onEnd)
                    .padding(start = 16.dp, top = 14.dp),
            )
        }

        canvas(Modifier.weight(1f).fillMaxWidth())

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (stage) {
                is QuizStage.Asking -> AskingPanel(stage, onAnswer)
                is QuizStage.Feedback -> FeedbackPanel(stage, onNext)
                else -> Unit
            }
        }
    }
}

@Composable
private fun AskingPanel(stage: QuizStage.Asking, onAnswer: (StructureId) -> Unit) {
    when (val question = stage.question) {
        is QuizQuestion.TapTheStructure -> {
            Text(stringResource(Res.string.question_tap_instruction), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            // Said only when the name is not in the language the session asked for.
            if (question.promptLocale != stage.session.locale) {
                Text(
                    stringResource(Res.string.question_prompt_locale, question.promptLocale.uppercase()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        is QuizQuestion.NameTheHighlighted -> {
            Text(stringResource(Res.string.question_name_instruction), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            for (option in question.options) {
                OutlinedButton(
                    onClick = { onAnswer(option.id) },
                    enabled = !stage.submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(option.name)
                }
            }
        }
    }

    val status = when {
        stage.submitting -> stringResource(Res.string.question_submitting)
        stage.remainingMillis != null ->
            // Rounded up: "0 s left" while there is still time would be a lie.
            stringResource(Res.string.question_time_left, ((stage.remainingMillis + 999) / 1000).toInt())
        else -> stringResource(Res.string.question_untimed)
    }
    Text(status, style = MaterialTheme.typography.labelSmall, color = TextTertiary)

    if (stage.submitFailed) {
        // Announced when it appears. The countdown beside it is not: read aloud every second
        // it would drown the question.
        Text(
            stringResource(Res.string.question_submit_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = IncorrectAmber,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun FeedbackPanel(stage: QuizStage.Feedback, onNext: () -> Unit) {
    val result = stage.result
    val marks = answerMarks(result)

    // A glyph and a word as well as a colour (§12). Announced as it appears: the verdict is
    // the most important thing this screen says.
    Text(
        text = if (result.correct) "✓  " + stringResource(Res.string.feedback_correct)
        else "✕  " + stringResource(Res.string.feedback_incorrect),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (result.correct) CorrectGreen else IncorrectAmber,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )

    for (mark in marks) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                mark.glyph,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mark.role == AnswerRole.Expected) CorrectGreen else IncorrectAmber,
            )
            Column {
                Text(
                    stringResource(if (mark.role == AnswerRole.Expected) Res.string.feedback_expected else Res.string.feedback_chosen),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Text(mark.structure.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (!result.correct && marks.none { it.role == AnswerRole.Chosen }) {
        Text(stringResource(Res.string.feedback_no_choice), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
    }

    // How the two relate, which is what makes a wrong answer worth having (spec §4.1).
    val shared = result.sharedAncestor
    if (!result.correct && shared != null && marks.size == 2) {
        Text(stringResource(Res.string.feedback_shared, shared.name), style = MaterialTheme.typography.bodyMedium)
    }

    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(if (stage.isLast) Res.string.feedback_finish else Res.string.feedback_next))
    }
}

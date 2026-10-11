package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_quiz.generated.resources.Res
import anatomypro.shared.feature_quiz.generated.resources.feedback_chosen
import anatomypro.shared.feature_quiz.generated.resources.feedback_correct
import anatomypro.shared.feature_quiz.generated.resources.feedback_expected
import anatomypro.shared.feature_quiz.generated.resources.feedback_finish
import anatomypro.shared.feature_quiz.generated.resources.feedback_incorrect
import anatomypro.shared.feature_quiz.generated.resources.feedback_next
import anatomypro.shared.feature_quiz.generated.resources.feedback_no_choice
import anatomypro.shared.feature_quiz.generated.resources.feedback_shared
import anatomypro.shared.feature_quiz.generated.resources.feedback_shared_heading
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
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.designsystem.ControlOutline
import com.ptk.anatomypro.core.designsystem.CorrectGreen
import com.ptk.anatomypro.core.designsystem.CorrectText
import com.ptk.anatomypro.core.designsystem.DashedPanel
import com.ptk.anatomypro.core.designsystem.FreeOutline
import com.ptk.anatomypro.core.designsystem.GlyphDisc
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.IncorrectOutline
import com.ptk.anatomypro.core.designsystem.IncorrectText
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.MeterBar
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.OnCorrect
import com.ptk.anatomypro.core.designsystem.OnIncorrect
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
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
    val type = LocalAnatomyType.current
    val (index, total) = when (stage) {
        is QuizStage.Asking -> stage.index to stage.total
        is QuizStage.Feedback -> stage.index to stage.session.questions.size
        else -> 0 to 0
    }
    val remaining = (stage as? QuizStage.Asking)?.remainingMillis

    Column(modifier = modifier.fillMaxSize()) {
        // The prototype's bar: the way out, then how far through and how long is left.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val end = stringResource(Res.string.question_end)
            Box(
                modifier = Modifier.size(44.dp).clickable(role = Role.Button, onClick = onEnd).semantics { contentDescription = end },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", style = type.title.copy(fontSize = 24.sp), color = TextStrong, modifier = Modifier.clearAndSetSemantics { })
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val progress = if (total > 0) stringResource(Res.string.question_progress, index + 1, total) else ""
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (total > 0) "${index + 1} / $total" else "",
                        style = type.value.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = progress },
                    )
                    // Not read out: a countdown announced every second would drown the question.
                    if (remaining != null) {
                        // Rounded up: "0:00" while there is still time would be a lie.
                        val seconds = ((remaining + 999) / 1000).toInt()
                        Text(
                            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            style = type.value.copy(fontSize = 11.sp),
                            color = TextSecondary,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
                MeterBar(if (total > 0) (index + 1f) / total else 0f)
            }
        }

        canvas(Modifier.weight(1f).fillMaxWidth())

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val type = LocalAnatomyType.current
    when (val question = stage.question) {
        is QuizQuestion.TapTheStructure -> {
            Overline(stringResource(Res.string.question_tap_instruction).uppercase(), color = TextSecondary)
            Text(question.prompt, style = type.title, color = TextPrimary)
            // Said only when the name is not in the language the session asked for.
            if (question.promptLocale != stage.session.locale) {
                Text(stringResource(Res.string.question_prompt_locale, question.promptLocale.uppercase()), style = type.note, color = TextTertiary)
            }
        }

        is QuizQuestion.NameTheHighlighted -> {
            Overline(stringResource(Res.string.question_name_instruction).uppercase(), color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            question.options.forEachIndexed { position, option ->
                OptionCard(letter = ('A' + position).toString(), option = option, enabled = !stage.submitting, onClick = { onAnswer(option.id) })
            }
        }
    }

    val status = when {
        stage.submitting -> stringResource(Res.string.question_submitting)
        stage.remainingMillis != null -> stringResource(Res.string.question_time_left, ((stage.remainingMillis + 999) / 1000).toInt())
        else -> stringResource(Res.string.question_untimed)
    }
    // The time is in the bar for the eye; this line is what a screen reader finds on demand.
    Text(status, style = type.note, color = TextTertiary)
    if (stage.submitFailed) {
        // Announced when it appears. The countdown beside it is not: read aloud every second
        // it would drown the question.
        Text(
            stringResource(Res.string.question_submit_failed),
            style = type.caption,
            color = IncorrectAmber,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun OptionCard(letter: String, option: StructureSummary, enabled: Boolean, onClick: () -> Unit) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(12.dp)
    val key = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(Surface)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).border(BorderStroke(1.dp, ControlOutline), key).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, style = type.value.copy(fontSize = 11.sp), color = TextSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(option.name, style = type.latin.copy(fontSize = 16.sp, lineHeight = 20.sp), color = if (enabled) TextPrimary else TextTertiary)
            // The Latin beneath, where the name asked for is in another language.
            val latin = option.latinName
            if (latin != null && latin != option.name) Text(latin, style = type.note, color = TextSecondary)
        }
    }
}

@Composable
private fun FeedbackPanel(stage: QuizStage.Feedback, onNext: () -> Unit) {
    val type = LocalAnatomyType.current
    val result = stage.result
    val marks = answerMarks(result)

    // A glyph and a word as well as a colour (§12). Announced as it appears: the verdict is
    // the most important thing this screen says.
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (result.correct) GlyphDisc("✓", fill = CorrectGreen, ink = OnCorrect) else GlyphDisc("✕", fill = IncorrectAmber, ink = OnIncorrect)
        Text(
            stringResource(if (result.correct) Res.string.feedback_correct else Res.string.feedback_incorrect),
            style = type.button.copy(fontSize = 19.sp, lineHeight = 24.sp),
            color = if (result.correct) CorrectText else IncorrectText,
        )
    }

    for (mark in marks) {
        val expected = mark.role == AnswerRole.Expected
        val content: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (expected) GlyphDisc(mark.glyph, fill = CorrectGreen, ink = OnCorrect, size = 18.dp)
                else GlyphDisc(mark.glyph, fill = IncorrectAmber, ink = OnIncorrect, size = 18.dp)
                Text(
                    stringResource(if (expected) Res.string.feedback_expected else Res.string.feedback_chosen).uppercase(),
                    style = type.code,
                    color = if (expected) CorrectText else IncorrectText,
                )
            }
            Text(mark.structure.name, style = type.heading.copy(fontSize = 19.sp, lineHeight = 23.sp), color = if (expected) TextPrimary else TextStrong)
        }
        // Solid for the right answer, dashed for the learner's: the line says it too.
        if (expected) Panel(outline = FreeOutline, spacing = 5.dp) { content() }
        else DashedPanel(outline = IncorrectOutline) { content() }
    }

    if (!result.correct && marks.none { it.role == AnswerRole.Chosen }) {
        Text(stringResource(Res.string.feedback_no_choice), style = type.caption, color = TextTertiary)
    }

    // How the two relate, which is what makes a wrong answer worth having (spec §4.1).
    val shared = result.sharedAncestor
    if (!result.correct && shared != null && marks.size == 2) {
        Panel(fill = NavSurface, spacing = 6.dp) {
            Overline(stringResource(Res.string.feedback_shared_heading), color = TextSecondary)
            Text(stringResource(Res.string.feedback_shared, shared.name), style = type.caption, color = TextStrong)
        }
    }

    PrimaryButton(
        stringResource(if (stage.isLast) Res.string.feedback_finish else Res.string.feedback_next),
        onClick = onNext,
        modifier = Modifier.padding(top = 4.dp),
    )
}

package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_quiz.generated.resources.Res
import anatomypro.shared.feature_quiz.generated.resources.finish_failed
import anatomypro.shared.feature_quiz.generated.resources.topics_count
import anatomypro.shared.feature_quiz.generated.resources.topics_empty
import anatomypro.shared.feature_quiz.generated.resources.topics_failed
import anatomypro.shared.feature_quiz.generated.resources.topics_format_name
import anatomypro.shared.feature_quiz.generated.resources.topics_format_tap
import anatomypro.shared.feature_quiz.generated.resources.topics_loading
import anatomypro.shared.feature_quiz.generated.resources.topics_locked
import anatomypro.shared.feature_quiz.generated.resources.topics_locked_description
import anatomypro.shared.feature_quiz.generated.resources.topics_mastery
import anatomypro.shared.feature_quiz.generated.resources.topics_retry
import anatomypro.shared.feature_quiz.generated.resources.topics_start_failed
import anatomypro.shared.feature_quiz.generated.resources.topics_title
import anatomypro.shared.feature_quiz.generated.resources.topics_untouched
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.MeterBar
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.SegmentedTabs
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 08: topic selection.
 *
 * Every state of a cell is said in words — not started, a percentage, locked — because a
 * progress bar and a dimmed row are colour and nothing else (§12).
 */
@Composable
fun TopicsScreen(
    state: TopicsUiState,
    startFailed: Boolean,
    finishFailed: Boolean,
    onFormat: (QuizFormat) -> Unit,
    onTopic: (TopicCell) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    Column(
        modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.topics_title), style = type.title.copy(fontSize = 23.sp), color = TextPrimary)

        // The prototype's chips filter by system and region; what is chosen here is the
        // question's format, which the prototype has no control for.
        SegmentedTabs(
            labels = listOf(stringResource(Res.string.topics_format_name), stringResource(Res.string.topics_format_tap)),
            selected = if (state.format == QuizFormat.NAME_THE_HIGHLIGHTED) 0 else 1,
            onSelect = { onFormat(if (it == 0) QuizFormat.NAME_THE_HIGHLIGHTED else QuizFormat.TAP_THE_STRUCTURE) },
        )

        if (startFailed) Text(stringResource(Res.string.topics_start_failed), style = type.caption, color = IncorrectAmber)
        if (finishFailed) Text(stringResource(Res.string.finish_failed), style = type.caption, color = IncorrectAmber)

        when {
            state.isLoading -> Text(stringResource(Res.string.topics_loading), style = type.body, color = TextTertiary)

            state.failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.topics_failed), style = type.body, color = TextPrimary)
                OutlineButton(stringResource(Res.string.topics_retry), onClick = onRetry)
            }

            state.cells.isEmpty() -> Text(stringResource(Res.string.topics_empty), style = type.body, color = TextTertiary)

            else -> Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Two to a row, as the prototype's grid; a row's cards are as tall as its taller one.
                for (pair in state.cells.chunked(2)) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (cell in pair) TopicCard(cell, onClick = { onTopic(cell) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(cell: TopicCell, onClick: () -> Unit, modifier: Modifier) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(14.dp)
    val lockedDescription = stringResource(Res.string.topics_locked_description, cell.topic.title)
    Column(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clip(shape)
            .background(if (cell.locked) NavSurface else Surface)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (cell.locked) Modifier.semantics { contentDescription = lockedDescription } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(cell.topic.title, style = type.latin.copy(fontSize = 16.sp, lineHeight = 20.sp), color = if (cell.locked) TextSecondary else TextPrimary)
            Text(
                pluralStringResource(Res.plurals.topics_count, cell.topic.structureCount, cell.topic.structureCount),
                style = type.note,
                color = TextSecondary,
            )
        }
        when {
            // Words, not a dimmed card alone (§12).
            cell.locked -> Text(stringResource(Res.string.topics_locked), style = type.code, color = TextSecondary)
            cell.untouched -> {
                Text(stringResource(Res.string.topics_untouched).uppercase(), style = type.code, color = TextTertiary)
                MeterBar(0f)
            }
            else -> {
                Text(stringResource(Res.string.topics_mastery, (cell.topic.mastery * 100).toInt()).uppercase(), style = type.code, color = TextSecondary)
                MeterBar(cell.topic.mastery)
            }
        }
    }
}

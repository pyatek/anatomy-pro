package com.ptk.anatomypro.feature.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
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
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.topics_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FormatOption(Res.string.topics_format_name, state.format == QuizFormat.NAME_THE_HIGHLIGHTED) {
                onFormat(QuizFormat.NAME_THE_HIGHLIGHTED)
            }
            FormatOption(Res.string.topics_format_tap, state.format == QuizFormat.TAP_THE_STRUCTURE) {
                onFormat(QuizFormat.TAP_THE_STRUCTURE)
            }
        }

        if (startFailed) {
            Text(stringResource(Res.string.topics_start_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
        }
        if (finishFailed) {
            Text(stringResource(Res.string.finish_failed), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
        }

        when {
            state.isLoading ->
                Text(stringResource(Res.string.topics_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.topics_failed), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.topics_retry))
                }
            }

            state.cells.isEmpty() ->
                Text(stringResource(Res.string.topics_empty), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            else -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                for (cell in state.cells) TopicRow(cell, onClick = { onTopic(cell) })
            }
        }
    }
}

@Composable
private fun FormatOption(label: StringResource, selected: Boolean, onSelect: () -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Accent else TextTertiary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(top = 12.dp),
    )
}

@Composable
private fun TopicRow(cell: TopicCell, onClick: () -> Unit) {
    val lockedDescription = stringResource(Res.string.topics_locked_description, cell.topic.title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (cell.locked) Modifier.semantics { contentDescription = lockedDescription } else Modifier)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                cell.topic.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (cell.locked) TextTertiary else MaterialTheme.typography.bodyMedium.color,
            )
            Text(
                pluralStringResource(Res.plurals.topics_count, cell.topic.structureCount, cell.topic.structureCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
        when {
            cell.locked ->
                Text(stringResource(Res.string.topics_locked), style = MaterialTheme.typography.labelSmall, color = Accent)

            cell.untouched ->
                Text(stringResource(Res.string.topics_untouched), style = MaterialTheme.typography.labelSmall, color = TextTertiary)

            else -> {
                LinearProgressIndicator(
                    progress = { cell.topic.mastery },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Hairline,
                )
                Text(
                    stringResource(Res.string.topics_mastery, (cell.topic.mastery * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

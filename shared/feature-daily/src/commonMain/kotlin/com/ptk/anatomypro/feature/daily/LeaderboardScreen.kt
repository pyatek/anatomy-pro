package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.board_daily
import anatomypro.shared.feature_daily.generated.resources.board_failed
import anatomypro.shared.feature_daily.generated.resources.board_loading
import anatomypro.shared.feature_daily.generated.resources.board_players
import anatomypro.shared.feature_daily.generated.resources.board_retry
import anatomypro.shared.feature_daily.generated.resources.board_row_description
import anatomypro.shared.feature_daily.generated.resources.board_row_description_you
import anatomypro.shared.feature_daily.generated.resources.board_score_daily
import anatomypro.shared.feature_daily.generated.resources.board_score_streak
import anatomypro.shared.feature_daily.generated.resources.board_streak
import anatomypro.shared.feature_daily.generated.resources.board_title
import anatomypro.shared.feature_daily.generated.resources.board_unranked_daily
import anatomypro.shared.feature_daily.generated.resources.board_unranked_streak
import anatomypro.shared.feature_daily.generated.resources.board_you
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_board
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 16: the leaderboard.
 *
 * The user's own row says YOU, in a word as well as a colour (§12). A user who is not on
 * the board is told so in a sentence, under a board that is still shown.
 */
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBoard: (Board) -> Unit,
    onRetry: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.board_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        if (!state.subscribed) {
            Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(Res.string.subscribe_board), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.subscribe_action))
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BoardOption(Res.string.board_daily, state.board == Board.DAILY) { onBoard(Board.DAILY) }
            BoardOption(Res.string.board_streak, state.board == Board.STREAK) { onBoard(Board.STREAK) }
        }

        val board = state.leaderboard
        when {
            state.isLoading ->
                Text(stringResource(Res.string.board_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed || board == null -> {
                Text(stringResource(Res.string.board_failed), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.board_retry))
                }
            }

            else -> {
                Text(
                    stringResource(Res.string.board_players, board.totalPlayers),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    for (entry in board.entries) EntryRow(entry, state.board)
                }
                val me = board.me
                if (me != null) {
                    // Shown apart only when the listed rows do not already include it.
                    if (board.entries.none { it.isMe }) EntryRow(me, state.board)
                } else {
                    Text(
                        stringResource(
                            if (state.board == Board.DAILY) Res.string.board_unranked_daily else Res.string.board_unranked_streak
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardOption(label: StringResource, selected: Boolean, onSelect: () -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Accent else TextTertiary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .padding(top = 12.dp),
    )
}

@Composable
private fun EntryRow(entry: LeaderboardEntry, board: Board) {
    val description = if (entry.isMe) {
        stringResource(Res.string.board_row_description_you, entry.rank)
    } else {
        stringResource(Res.string.board_row_description, entry.rank, entry.nickname)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            // One announcement per row, not rank, name and score as three.
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(entry.rank.toString(), style = MaterialTheme.typography.bodyMedium, color = TextTertiary, modifier = Modifier.width(48.dp))
        Text(
            if (entry.isMe) stringResource(Res.string.board_you) else entry.nickname,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.isMe) FontWeight.SemiBold else FontWeight.Normal,
            color = if (entry.isMe) Accent else MaterialTheme.typography.bodyMedium.color,
            modifier = Modifier.weight(1f),
        )
        Text(
            when (board) {
                Board.DAILY -> stringResource(Res.string.board_score_daily, entry.score, (entry.elapsedMillis / 1000).toInt())
                Board.STREAK -> stringResource(Res.string.board_score_streak, entry.score)
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

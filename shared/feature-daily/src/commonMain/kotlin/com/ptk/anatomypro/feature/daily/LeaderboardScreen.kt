package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import anatomypro.shared.feature_daily.generated.resources.board_your_place
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_board
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.LeaderboardEntry
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.AccentWash
import com.ptk.anatomypro.core.designsystem.FooterHairline
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Monogram
import com.ptk.anatomypro.core.designsystem.OnAccent
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.SegmentedTabs
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/** How many places are drawn as cards before the list becomes plain rows. */
private const val PODIUM = 3

/**
 * Prototype screen 16: the leaderboard.
 *
 * The prototype's board: a segmented switch between boards, the first three places as
 * cards, the rest as ruled rows, and the user's own place as a card of its own, marked in
 * words as well as colour (§12). The prototype has a third board by university and shows
 * each player's school; neither is in the contract before sign-in (§9.3).
 */
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onBoard: (Board) -> Unit,
    onRetry: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    Column(
        modifier = modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.board_title), style = type.title.copy(fontSize = 23.sp), color = TextPrimary)

        if (!state.subscribed) {
            Panel {
                Text(stringResource(Res.string.subscribe_title), style = type.heading, color = TextPrimary)
                Text(stringResource(Res.string.subscribe_board), style = type.body, color = TextSecondary)
                PrimaryButton(stringResource(Res.string.subscribe_action), onClick = onSubscribe)
            }
            return@Column
        }

        SegmentedTabs(
            labels = listOf(stringResource(Res.string.board_daily), stringResource(Res.string.board_streak)),
            selected = if (state.board == Board.DAILY) 0 else 1,
            onSelect = { onBoard(if (it == 0) Board.DAILY else Board.STREAK) },
        )

        val board = state.leaderboard
        when {
            state.isLoading -> Text(stringResource(Res.string.board_loading), style = type.body, color = TextTertiary)

            state.failed || board == null -> {
                Text(stringResource(Res.string.board_failed), style = type.body, color = TextPrimary)
                OutlineButton(stringResource(Res.string.board_retry), onClick = onRetry)
            }

            else -> {
                Text(stringResource(Res.string.board_players, board.totalPlayers).uppercase(), style = type.value.copy(fontSize = 11.sp), color = TextTertiary)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (entry in board.entries) {
                        when {
                            entry.isMe -> OwnRow(entry, state.board)
                            entry.rank <= PODIUM -> PodiumRow(entry, state.board)
                            else -> PlainRow(entry, state.board)
                        }
                    }
                    val me = board.me
                    if (me != null) {
                        // Shown apart only when the listed rows do not already include it.
                        if (board.entries.none { it.isMe }) {
                            Text("⋮", style = type.value, color = TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
                            OwnRow(me, state.board)
                        }
                    } else {
                        Text(
                            stringResource(if (state.board == Board.DAILY) Res.string.board_unranked_daily else Res.string.board_unranked_streak),
                            style = type.caption,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun scoreOf(entry: LeaderboardEntry, board: Board): String = when (board) {
    Board.DAILY -> stringResource(Res.string.board_score_daily, entry.score, (entry.elapsedMillis / 1000).toInt())
    Board.STREAK -> stringResource(Res.string.board_score_streak, entry.score)
}

private fun place(rank: Int): String = rank.toString().padStart(2, '0')

private fun initials(nickname: String): String =
    nickname.split('_', ' ', '.').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/** One announcement per row, not rank, name and score as three. */
@Composable
private fun rowDescription(entry: LeaderboardEntry): String =
    if (entry.isMe) stringResource(Res.string.board_row_description_you, entry.rank)
    else stringResource(Res.string.board_row_description, entry.rank, entry.nickname)

@Composable
private fun PodiumRow(entry: LeaderboardEntry, board: Board) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(11.dp)
    val description = rowDescription(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(Surface)
            .border(BorderStroke(1.dp, Hairline), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(place(entry.rank), style = type.value.copy(fontSize = 14.sp), color = if (entry.rank == 1) Accent else TextSecondary, modifier = Modifier.width(30.dp))
        Monogram(initials(entry.nickname))
        Text(entry.nickname, style = type.bodyStrong.copy(fontSize = 14.sp), color = TextPrimary, modifier = Modifier.weight(1f))
        Text(scoreOf(entry, board), style = type.value, color = TextPrimary)
    }
}

@Composable
private fun PlainRow(entry: LeaderboardEntry, board: Board) {
    val type = LocalAnatomyType.current
    val description = rowDescription(entry)
    Column(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(place(entry.rank), style = type.value.copy(fontSize = 13.sp), color = TextTertiary, modifier = Modifier.width(30.dp))
            Text(entry.nickname, style = type.body, color = TextStrong, modifier = Modifier.weight(1f))
            Text(scoreOf(entry, board), style = type.value, color = TextStrong)
        }
        HorizontalDivider(color = FooterHairline, thickness = 1.dp)
    }
}

@Composable
private fun OwnRow(entry: LeaderboardEntry, board: Board) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(11.dp)
    val description = rowDescription(entry)
    val you = stringResource(Res.string.board_you)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(AccentWash)
            .border(BorderStroke(1.dp, Accent), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(place(entry.rank), style = type.value.copy(fontSize = 14.sp), color = HighlightOutline, modifier = Modifier.width(30.dp))
        Monogram(you.take(2), fill = Accent, color = OnAccent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(you, style = type.bodyStrong.copy(fontSize = 14.sp), color = TextPrimary)
            Text(stringResource(Res.string.board_your_place), style = type.code, color = HighlightOutline)
        }
        Text(scoreOf(entry, board), style = type.value, color = TextPrimary)
    }
}

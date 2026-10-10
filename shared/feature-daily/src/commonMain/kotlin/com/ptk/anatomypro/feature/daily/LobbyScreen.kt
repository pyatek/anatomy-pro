package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.lobby_back
import anatomypro.shared.feature_daily.generated.resources.lobby_board
import anatomypro.shared.feature_daily.generated.resources.lobby_done_rank
import anatomypro.shared.feature_daily.generated.resources.lobby_done_score
import anatomypro.shared.feature_daily.generated.resources.lobby_done_time
import anatomypro.shared.feature_daily.generated.resources.lobby_done_tomorrow
import anatomypro.shared.feature_daily.generated.resources.lobby_done_unranked
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_load
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_start
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_submit
import anatomypro.shared.feature_daily.generated.resources.lobby_loading
import anatomypro.shared.feature_daily.generated.resources.lobby_no_verdicts
import anatomypro.shared.feature_daily.generated.resources.lobby_offline
import anatomypro.shared.feature_daily.generated.resources.lobby_practice
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_none
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_offer
import anatomypro.shared.feature_daily.generated.resources.lobby_ready_body
import anatomypro.shared.feature_daily.generated.resources.lobby_resend
import anatomypro.shared.feature_daily.generated.resources.lobby_retry
import anatomypro.shared.feature_daily.generated.resources.lobby_start
import anatomypro.shared.feature_daily.generated.resources.lobby_submitting
import anatomypro.shared.feature_daily.generated.resources.lobby_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 15: the daily quiz lobby.
 *
 * Every stage but [DailyStage.Playing]. That one is the quiz's question screen, drawn by
 * the route in this same destination so that its canvas is not recreated on the way in.
 */
@Composable
fun LobbyScreen(
    stage: DailyStage,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onPractice: (QuizTopic) -> Unit,
    onSubscribe: () -> Unit,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.lobby_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.lobby_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        // Announced as it changes: sending, sent, failed.
        Column(
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (stage) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.subscribe_daily), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    Button(onClick = onSubscribe, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.subscribe_action))
                    }
                }

                DailyStage.Loading ->
                    Text(stringResource(Res.string.lobby_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                is DailyStage.Ready -> {
                    Text(stringResource(Res.string.lobby_ready_body, stage.questionCount), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(Res.string.lobby_no_verdicts), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_start))
                    }
                }

                DailyStage.Submitting ->
                    Text(stringResource(Res.string.lobby_submitting), style = MaterialTheme.typography.bodyMedium)

                is DailyStage.Done -> {
                    val result = stage.result
                    Text(
                        stringResource(Res.string.lobby_done_score, result.correct, result.total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val rank = result.rank
                    Text(
                        if (rank != null) stringResource(Res.string.lobby_done_rank, rank, result.totalPlayers)
                        else stringResource(Res.string.lobby_done_unranked),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.lobby_done_time, (result.elapsedMillis / 1000).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    Text(stringResource(Res.string.lobby_done_tomorrow), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    OutlinedButton(onClick = onLeaderboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_board))
                    }
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.lobby_offline), style = MaterialTheme.typography.bodyMedium)
                    val practice = stage.practice
                    if (practice != null) {
                        Text(
                            stringResource(Res.string.lobby_practice_offer, practice.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                        )
                        Button(onClick = { onPractice(practice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(Res.string.lobby_practice))
                        }
                    } else {
                        Text(stringResource(Res.string.lobby_practice_none), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.lobby_retry))
                    }
                }

                is DailyStage.Failed -> {
                    val message = when (stage.during) {
                        DailyFailure.LOAD -> Res.string.lobby_failed_load
                        DailyFailure.START -> Res.string.lobby_failed_start
                        DailyFailure.SUBMIT -> Res.string.lobby_failed_submit
                    }
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(
                            stringResource(
                                if (stage.during == DailyFailure.SUBMIT) Res.string.lobby_resend else Res.string.lobby_retry
                            )
                        )
                    }
                }

                // Drawn by the route, with the quiz's question screen.
                is DailyStage.Playing -> Unit
            }
        }
    }
}

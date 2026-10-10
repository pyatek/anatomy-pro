package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.lobby_back
import anatomypro.shared.feature_daily.generated.resources.lobby_board
import anatomypro.shared.feature_daily.generated.resources.lobby_done_rank
import anatomypro.shared.feature_daily.generated.resources.lobby_done_score
import anatomypro.shared.feature_daily.generated.resources.lobby_done_time
import anatomypro.shared.feature_daily.generated.resources.lobby_done_tomorrow
import anatomypro.shared.feature_daily.generated.resources.lobby_done_unranked
import anatomypro.shared.feature_daily.generated.resources.lobby_fact_attempts
import anatomypro.shared.feature_daily.generated.resources.lobby_fact_attempts_value
import anatomypro.shared.feature_daily.generated.resources.lobby_fact_place
import anatomypro.shared.feature_daily.generated.resources.lobby_fact_questions
import anatomypro.shared.feature_daily.generated.resources.lobby_fact_time
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_load
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_start
import anatomypro.shared.feature_daily.generated.resources.lobby_failed_submit
import anatomypro.shared.feature_daily.generated.resources.lobby_loading
import anatomypro.shared.feature_daily.generated.resources.lobby_no_verdicts
import anatomypro.shared.feature_daily.generated.resources.lobby_offline
import anatomypro.shared.feature_daily.generated.resources.lobby_practice
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_none
import anatomypro.shared.feature_daily.generated.resources.lobby_practice_offer
import anatomypro.shared.feature_daily.generated.resources.lobby_resend
import anatomypro.shared.feature_daily.generated.resources.lobby_retry
import anatomypro.shared.feature_daily.generated.resources.lobby_rule_clock
import anatomypro.shared.feature_daily.generated.resources.lobby_rule_same
import anatomypro.shared.feature_daily.generated.resources.lobby_rules
import anatomypro.shared.feature_daily.generated.resources.lobby_start
import anatomypro.shared.feature_daily.generated.resources.lobby_submitting
import anatomypro.shared.feature_daily.generated.resources.lobby_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.designsystem.FactRow
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.NumberedLine
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.TopBar
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 15: the daily quiz's lobby, and its result.
 *
 * Every stage except Playing, which the route draws with the quiz's question screen.
 *
 * The prototype's lobby: the day and the quiz named at the top, the facts in a panel, the
 * rules numbered, and one button at the foot. It also names the day's topic, a time limit
 * and how many have played. The day's topic is the server's to say and is not in the
 * contract, the daily has no limit per question (plan decision 2), and the count is not
 * reported, so the panel holds what is known.
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
    val type = LocalAnatomyType.current
    val practice = (stage as? DailyStage.Offline)?.practice

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(stringResource(Res.string.lobby_title).uppercase(), backLabel = stringResource(Res.string.lobby_back), onBack = onBack)

        // Announced as it changes: sending, sent, failed.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (stage) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = type.title, color = TextPrimary, textAlign = TextAlign.Center)
                    Text(stringResource(Res.string.subscribe_daily), style = type.body, color = TextSecondary, textAlign = TextAlign.Center)
                }

                DailyStage.Loading -> Text(stringResource(Res.string.lobby_loading), style = type.body, color = TextTertiary)

                is DailyStage.Ready -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Overline(stage.date.toString())
                        Text(stringResource(Res.string.lobby_title), style = type.title, color = TextPrimary, textAlign = TextAlign.Center)
                    }
                    Panel(spacing = 14.dp) {
                        FactRow(stringResource(Res.string.lobby_fact_questions), stage.questionCount.toString())
                        FactRow(stringResource(Res.string.lobby_fact_attempts), stringResource(Res.string.lobby_fact_attempts_value))
                    }
                    Panel(fill = NavSurface, spacing = 9.dp) {
                        Overline(stringResource(Res.string.lobby_rules), color = TextSecondary)
                        NumberedLine(1, stringResource(Res.string.lobby_rule_same))
                        NumberedLine(2, stringResource(Res.string.lobby_rule_clock))
                        NumberedLine(3, stringResource(Res.string.lobby_no_verdicts))
                    }
                }

                DailyStage.Submitting -> Text(stringResource(Res.string.lobby_submitting), style = type.body, color = TextSecondary)

                is DailyStage.Done -> {
                    val result = stage.result
                    val rank = result.rank
                    Text(
                        stringResource(Res.string.lobby_done_score, result.correct, result.total),
                        style = type.title,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Panel(spacing = 14.dp) {
                        FactRow(
                            stringResource(Res.string.lobby_fact_place),
                            if (rank != null) stringResource(Res.string.lobby_done_rank, rank, result.totalPlayers)
                            else stringResource(Res.string.lobby_done_unranked),
                        )
                        FactRow(
                            stringResource(Res.string.lobby_fact_time),
                            stringResource(Res.string.lobby_done_time, (result.elapsedMillis / 1000).toInt()),
                        )
                    }
                    Text(stringResource(Res.string.lobby_done_tomorrow), style = type.caption, color = TextTertiary, textAlign = TextAlign.Center)
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.lobby_offline), style = type.body, color = TextPrimary, textAlign = TextAlign.Center)
                    Text(
                        if (practice != null) stringResource(Res.string.lobby_practice_offer, practice.title)
                        else stringResource(Res.string.lobby_practice_none),
                        style = type.caption,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                is DailyStage.Failed -> {
                    val message = when (stage.during) {
                        DailyFailure.LOAD -> Res.string.lobby_failed_load
                        DailyFailure.START -> Res.string.lobby_failed_start
                        DailyFailure.SUBMIT -> Res.string.lobby_failed_submit
                    }
                    Text(stringResource(message), style = type.body, color = IncorrectAmber, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                // Drawn by the route, with the quiz's question screen.
                is DailyStage.Playing -> Unit
            }
        }

        when (stage) {
            DailyStage.NotSubscribed -> ScreenFooter { PrimaryButton(stringResource(Res.string.subscribe_action), onClick = onSubscribe) }
            is DailyStage.Ready -> ScreenFooter { PrimaryButton(stringResource(Res.string.lobby_start), onClick = onStart) }
            is DailyStage.Done -> ScreenFooter { OutlineButton(stringResource(Res.string.lobby_board), onClick = onLeaderboard) }
            is DailyStage.Offline -> ScreenFooter {
                if (practice != null) PrimaryButton(stringResource(Res.string.lobby_practice), onClick = { onPractice(practice) })
                OutlineButton(stringResource(Res.string.lobby_retry), onClick = onRetry)
            }
            is DailyStage.Failed -> ScreenFooter {
                PrimaryButton(
                    stringResource(if (stage.during == DailyFailure.SUBMIT) Res.string.lobby_resend else Res.string.lobby_retry),
                    onClick = onRetry,
                )
            }
            DailyStage.Loading, DailyStage.Submitting, is DailyStage.Playing -> Unit
        }
    }
}

package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_daily.generated.resources.Res
import anatomypro.shared.feature_daily.generated.resources.home_daily_continue
import anatomypro.shared.feature_daily.generated.resources.home_daily_done
import anatomypro.shared.feature_daily.generated.resources.home_daily_failed
import anatomypro.shared.feature_daily.generated.resources.home_daily_heading
import anatomypro.shared.feature_daily.generated.resources.home_daily_loading
import anatomypro.shared.feature_daily.generated.resources.home_daily_offline
import anatomypro.shared.feature_daily.generated.resources.home_daily_open
import anatomypro.shared.feature_daily.generated.resources.home_daily_playing
import anatomypro.shared.feature_daily.generated.resources.home_daily_ready
import anatomypro.shared.feature_daily.generated.resources.home_daily_result
import anatomypro.shared.feature_daily.generated.resources.home_streak_longest
import anatomypro.shared.feature_daily.generated.resources.home_streak_none
import anatomypro.shared.feature_daily.generated.resources.home_streak_unit
import anatomypro.shared.feature_daily.generated.resources.home_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 14: the home dashboard.
 *
 * The streak, and today's quiz in whichever of its states it is in (spec §9 and §15.3):
 * not started, in progress, done, offline, not subscribed.
 */
@Composable
fun HomeScreen(
    home: HomeUiState,
    daily: DailyStage,
    onOpenDaily: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(Res.string.home_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        home.streak?.let { streak ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (streak.currentDays == 0) {
                    Text(stringResource(Res.string.home_streak_none), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                } else {
                    Text(streak.currentDays.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.home_streak_unit), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                if (streak.longestDays > 0) {
                    Text(
                        stringResource(Res.string.home_streak_longest, streak.longestDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.home_daily_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            when (daily) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.subscribe_daily), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                    OutlinedButton(onClick = onSubscribe, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.subscribe_action))
                    }
                }

                DailyStage.Loading ->
                    Text(stringResource(Res.string.home_daily_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

                is DailyStage.Ready -> {
                    Text(stringResource(Res.string.home_daily_ready, daily.questionCount), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }

                is DailyStage.Playing, DailyStage.Submitting -> {
                    Text(stringResource(Res.string.home_daily_playing), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_continue))
                    }
                }

                is DailyStage.Done -> {
                    Text(
                        stringResource(Res.string.home_daily_done, daily.result.correct, daily.result.total),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_result))
                    }
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.home_daily_offline), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }

                is DailyStage.Failed -> {
                    Text(stringResource(Res.string.home_daily_failed), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenDaily, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(Res.string.home_daily_open))
                    }
                }
            }
        }
    }
}

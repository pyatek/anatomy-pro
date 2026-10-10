package com.ptk.anatomypro.feature.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import anatomypro.shared.feature_daily.generated.resources.home_streak_label
import anatomypro.shared.feature_daily.generated.resources.home_streak_longest
import anatomypro.shared.feature_daily.generated.resources.home_streak_none
import anatomypro.shared.feature_daily.generated.resources.home_streak_unit
import anatomypro.shared.feature_daily.generated.resources.home_title
import anatomypro.shared.feature_daily.generated.resources.subscribe_action
import anatomypro.shared.feature_daily.generated.resources.subscribe_daily
import anatomypro.shared.feature_daily.generated.resources.subscribe_title
import com.ptk.anatomypro.core.designsystem.HeroPanel
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 14: home.
 *
 * The daily quiz leads, on the prototype's warm card; the streak sits beneath it as a tile.
 * The prototype also greets the student by name, counts mastered structures and offers two
 * things to continue. There is no name before sign-in (§9.3), mastery belongs to the
 * profile (plan decision 7), and nothing records what was last open, so those are left out
 * rather than filled with invented figures.
 */
@Composable
fun HomeScreen(
    home: HomeUiState,
    daily: DailyStage,
    onOpenDaily: () -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalAnatomyType.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(Res.string.home_title), style = type.title.copy(fontSize = 23.sp), color = TextPrimary)

        HeroPanel {
            Overline(stringResource(Res.string.home_daily_heading), color = HighlightOutline)
            when (daily) {
                DailyStage.NotSubscribed -> {
                    Text(stringResource(Res.string.subscribe_title), style = type.heading, color = TextPrimary)
                    Text(stringResource(Res.string.subscribe_daily), style = type.caption, color = TextStrong)
                    OutlineButton(stringResource(Res.string.subscribe_action), onClick = onSubscribe)
                }

                DailyStage.Loading -> Text(stringResource(Res.string.home_daily_loading), style = type.caption, color = TextStrong)

                is DailyStage.Ready -> {
                    Text(stringResource(Res.string.home_daily_ready, daily.questionCount), style = type.caption, color = TextStrong)
                    PrimaryButton(stringResource(Res.string.home_daily_open), onClick = onOpenDaily)
                }

                is DailyStage.Playing, DailyStage.Submitting -> {
                    Text(stringResource(Res.string.home_daily_playing), style = type.caption, color = TextStrong)
                    PrimaryButton(stringResource(Res.string.home_daily_continue), onClick = onOpenDaily)
                }

                is DailyStage.Done -> {
                    Text(
                        stringResource(Res.string.home_daily_done, daily.result.correct, daily.result.total),
                        style = type.heading,
                        color = TextPrimary,
                    )
                    OutlineButton(stringResource(Res.string.home_daily_result), onClick = onOpenDaily)
                }

                is DailyStage.Offline -> {
                    Text(stringResource(Res.string.home_daily_offline), style = type.caption, color = TextStrong)
                    OutlineButton(stringResource(Res.string.home_daily_open), onClick = onOpenDaily)
                }

                is DailyStage.Failed -> {
                    Text(stringResource(Res.string.home_daily_failed), style = type.caption, color = TextStrong)
                    OutlineButton(stringResource(Res.string.home_daily_open), onClick = onOpenDaily)
                }
            }
        }

        home.streak?.let { streak ->
            Panel(spacing = 6.dp) {
                Overline(stringResource(Res.string.home_streak_label), color = TextSecondary)
                if (streak.currentDays == 0) {
                    Text(stringResource(Res.string.home_streak_none), style = type.caption, color = TextTertiary)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(streak.currentDays.toString(), style = type.value.copy(fontSize = 26.sp, lineHeight = 30.sp), color = TextPrimary)
                        Text(stringResource(Res.string.home_streak_unit), style = type.note, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                if (streak.longestDays > 0) {
                    Text(stringResource(Res.string.home_streak_longest, streak.longestDays), style = type.note, color = TextTertiary)
                }
            }
        }
    }
}

package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.decimal_separator
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_heading
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_legend
import anatomypro.shared.feature_profile.generated.resources.profile_day_daily
import anatomypro.shared.feature_profile.generated.resources.profile_day_nothing
import anatomypro.shared.feature_profile.generated.resources.profile_day_studied
import anatomypro.shared.feature_profile.generated.resources.profile_failed
import anatomypro.shared.feature_profile.generated.resources.profile_loading
import anatomypro.shared.feature_profile.generated.resources.profile_mastery
import anatomypro.shared.feature_profile.generated.resources.profile_mastery_heading
import anatomypro.shared.feature_profile.generated.resources.profile_packs
import anatomypro.shared.feature_profile.generated.resources.profile_retry
import anatomypro.shared.feature_profile.generated.resources.profile_settings
import anatomypro.shared.feature_profile.generated.resources.profile_streak
import anatomypro.shared.feature_profile.generated.resources.profile_streak_none
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_active
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_free
import anatomypro.shared.feature_profile.generated.resources.profile_title
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 17: the profile.
 *
 * A day is a glyph — ● studied, ◆ studied and the daily done, · nothing — with a sentence
 * for a screen reader, so the calendar says the same thing without colour (§12).
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    subscribed: Boolean,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onPacks: () -> Unit,
    onSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(Res.string.profile_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        val streak = state.streak
        if (streak != null) {
            Text(
                if (streak.currentDays == 0 && streak.longestDays == 0) stringResource(Res.string.profile_streak_none)
                else stringResource(Res.string.profile_streak, streak.currentDays, streak.longestDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when {
            state.isLoading ->
                Text(stringResource(Res.string.profile_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed -> {
                Text(stringResource(Res.string.profile_failed), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.profile_retry))
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.profile_calendar_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    for (week in state.calendar.chunked(7)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (day in week) DayCell(day, Modifier.weight(1f))
                        }
                    }
                    Text(stringResource(Res.string.profile_calendar_legend), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.profile_mastery_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    for (system in state.mastery) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(system.title, style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(
                                progress = { system.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent,
                                trackColor = Hairline,
                            )
                            Text(
                                stringResource(Res.string.profile_mastery, system.structuresSeen, system.structuresTotal),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }
                }
            }
        }

        Column {
            Link(stringResource(Res.string.profile_settings), onSettings)
            Link(
                stringResource(
                    Res.string.profile_packs,
                    state.installedPacks,
                    megabytes(state.installedBytes, stringResource(Res.string.decimal_separator)),
                ),
                onPacks,
            )
            Link(
                stringResource(if (subscribed) Res.string.profile_subscription_active else Res.string.profile_subscription_free),
                onSubscription,
            )
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier) {
    val activity = day.activity
    val studied = activity != null && activity.sessions > 0
    val glyph = when {
        studied && activity?.dailyCompleted == true -> "◆"
        studied -> "●"
        else -> "·"
    }
    val date = day.date.toString()
    val description = when {
        studied && activity?.dailyCompleted == true -> stringResource(Res.string.profile_day_daily, date)
        studied -> stringResource(Res.string.profile_day_studied, date)
        else -> stringResource(Res.string.profile_day_nothing, date)
    }
    Text(
        glyph,
        style = MaterialTheme.typography.bodyMedium,
        color = if (studied) Accent else TextTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(vertical = 4.dp).clearAndSetSemantics { contentDescription = description },
    )
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(top = 14.dp),
    )
}

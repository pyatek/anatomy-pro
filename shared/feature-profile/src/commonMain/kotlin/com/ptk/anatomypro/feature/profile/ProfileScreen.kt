package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.decimal_separator
import anatomypro.shared.feature_profile.generated.resources.profile_account
import anatomypro.shared.feature_profile.generated.resources.profile_account_title
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_heading
import anatomypro.shared.feature_profile.generated.resources.profile_calendar_legend
import anatomypro.shared.feature_profile.generated.resources.profile_day_daily
import anatomypro.shared.feature_profile.generated.resources.profile_day_nothing
import anatomypro.shared.feature_profile.generated.resources.profile_day_studied
import anatomypro.shared.feature_profile.generated.resources.profile_failed
import anatomypro.shared.feature_profile.generated.resources.profile_loading
import anatomypro.shared.feature_profile.generated.resources.profile_mastery
import anatomypro.shared.feature_profile.generated.resources.profile_mastery_heading
import anatomypro.shared.feature_profile.generated.resources.profile_mastery_value
import anatomypro.shared.feature_profile.generated.resources.profile_packs
import anatomypro.shared.feature_profile.generated.resources.profile_packs_title
import anatomypro.shared.feature_profile.generated.resources.profile_retry
import anatomypro.shared.feature_profile.generated.resources.profile_settings
import anatomypro.shared.feature_profile.generated.resources.profile_streak
import anatomypro.shared.feature_profile.generated.resources.profile_streak_none
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_active
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_free
import anatomypro.shared.feature_profile.generated.resources.profile_subscription_title
import anatomypro.shared.feature_profile.generated.resources.profile_title
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.MonogramFill
import com.ptk.anatomypro.core.designsystem.OnAccent
import com.ptk.anatomypro.core.designsystem.OutlineButton
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import com.ptk.anatomypro.feature.atlas.layers.systemNames
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 17: the profile.
 *
 * The prototype's page: a heading with the way to settings beside it, the streak over a
 * calendar of squares, mastery as a bar per system, and what else lives here as cards.
 *
 * The prototype tells a full day from a partial one by the square's colour and a legend.
 * §12 does not allow colour alone, so each square also carries the glyph the legend names,
 * and is read out as a date and what happened on it. The prototype's name, school and year
 * wait for sign-in (§9.3).
 *
 * [onAccount] is null where there is no account to open — a build with no server — and
 * then no card offers one.
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
    onAccount: (() -> Unit)? = null,
) {
    val type = LocalAnatomyType.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.profile_title), style = type.title.copy(fontSize = 23.sp), color = TextPrimary, modifier = Modifier.weight(1f))
            val settings = stringResource(Res.string.profile_settings)
            val shape = RoundedCornerShape(11.dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape)
                    .border(BorderStroke(1.dp, Hairline), shape)
                    .clickable(role = Role.Button, onClick = onSettings)
                    .semantics { contentDescription = settings },
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", style = type.bodyStrong, color = TextStrong, modifier = Modifier.clearAndSetSemantics { })
            }
        }

        when {
            state.isLoading -> Text(stringResource(Res.string.profile_loading), style = type.body, color = TextTertiary)

            state.failed -> {
                Text(stringResource(Res.string.profile_failed), style = type.body, color = TextPrimary)
                OutlineButton(stringResource(Res.string.profile_retry), onClick = onRetry)
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Overline(stringResource(Res.string.profile_calendar_heading), color = TextSecondary)
                    state.streak?.let { streak ->
                        Text(
                            if (streak.currentDays == 0 && streak.longestDays == 0) stringResource(Res.string.profile_streak_none)
                            else stringResource(Res.string.profile_streak, streak.currentDays, streak.longestDays),
                            style = type.note,
                            color = TextStrong,
                        )
                    }
                    Panel(spacing = 5.dp) {
                        for (week in state.calendar.chunked(7)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                for (day in week) DayCell(day, Modifier.weight(1f))
                                // A last week that is not full still keeps its squares' size.
                                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                        Text(
                            stringResource(Res.string.profile_calendar_legend),
                            style = type.code,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Overline(stringResource(Res.string.profile_mastery_heading), color = TextSecondary)
                    for (system in state.mastery) {
                        // Named as the atlas names its systems, Latin leading: a repository
                        // can only offer the id where the pack has no structure for the system.
                        val names = systemNames(system.system)
                        val spoken = stringResource(Res.string.profile_mastery, system.structuresSeen, system.structuresTotal)
                        Column(
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${names.local}. $spoken" },
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                Text(names.latin, style = type.latin.copy(fontSize = 15.sp), color = TextPrimary, modifier = Modifier.clearAndSetSemantics { })
                                Text(
                                    stringResource(
                                        Res.string.profile_mastery_value,
                                        system.structuresSeen,
                                        system.structuresTotal,
                                        (system.fraction * 100).toInt(),
                                    ),
                                    style = type.value,
                                    color = TextPrimary,
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                            }
                            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).clip(RoundedCornerShape(3.dp)).background(Hairline)) {
                                Box(Modifier.fillMaxWidth(system.fraction.coerceIn(0f, 1f)).heightIn(min = 6.dp).background(Accent))
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onAccount != null) {
                LinkCard(
                    title = stringResource(Res.string.profile_account_title),
                    detail = stringResource(Res.string.profile_account),
                    onClick = onAccount,
                )
            }
            LinkCard(
                title = stringResource(Res.string.profile_packs_title),
                detail = stringResource(
                    Res.string.profile_packs,
                    state.installedPacks,
                    megabytes(state.installedBytes, stringResource(Res.string.decimal_separator)),
                ),
                onClick = onPacks,
            )
            LinkCard(
                title = stringResource(Res.string.profile_subscription_title),
                detail = stringResource(if (subscribed) Res.string.profile_subscription_active else Res.string.profile_subscription_free),
                onClick = onSubscription,
            )
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier) {
    val activity = day.activity
    val studied = activity != null && activity.sessions > 0
    val full = studied && activity?.dailyCompleted == true
    val date = day.date.toString()
    val description = when {
        full -> stringResource(Res.string.profile_day_daily, date)
        studied -> stringResource(Res.string.profile_day_studied, date)
        else -> stringResource(Res.string.profile_day_nothing, date)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(if (full) Accent else if (studied) AccentMuted else MonogramFill)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // The glyph the legend names: the day is told apart without its colour (§12).
        val glyph = if (full) "◆" else if (studied) "●" else ""
        if (glyph.isNotEmpty()) {
            Text(glyph, style = LocalAnatomyType.current.code, color = if (full) OnAccent else TextStrong)
        }
    }
}

@Composable
private fun LinkCard(title: String, detail: String, onClick: () -> Unit) {
    val type = LocalAnatomyType.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(Surface)
            .border(BorderStroke(1.dp, Hairline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = type.bodyStrong.copy(fontSize = 14.sp), color = TextPrimary)
            Text(detail, style = type.note, color = TextSecondary)
        }
        Text("›", style = type.button, color = TextTertiary, modifier = Modifier.clearAndSetSemantics { })
    }
}

package com.ptk.anatomypro.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.decimal_separator
import anatomypro.shared.feature_onboarding.generated.resources.download_body
import anatomypro.shared.feature_onboarding.generated.resources.download_done
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_checksum
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_entitlement
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_kept
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_network
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_space
import anatomypro.shared.feature_onboarding.generated.resources.download_finish
import anatomypro.shared.feature_onboarding.generated.resources.download_progress
import anatomypro.shared.feature_onboarding.generated.resources.download_progress_description
import anatomypro.shared.feature_onboarding.generated.resources.download_queued
import anatomypro.shared.feature_onboarding.generated.resources.download_restart
import anatomypro.shared.feature_onboarding.generated.resources.download_resume
import anatomypro.shared.feature_onboarding.generated.resources.download_size
import anatomypro.shared.feature_onboarding.generated.resources.download_skip
import anatomypro.shared.feature_onboarding.generated.resources.download_title
import anatomypro.shared.feature_onboarding.generated.resources.download_unavailable
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_back
import anatomypro.shared.feature_onboarding.generated.resources.onboarding_step
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 03: the first pack downloads.
 *
 * Progress is a bar and a sentence, because a bar alone says nothing to a screen reader and
 * nothing about size (spec §4.6: "a download UI that cannot report bytes is a spinner"). A
 * failure is named in words, never by colour alone (§12).
 */
@Composable
fun FirstDownloadScreen(
    state: FirstDownloadUiState,
    step: Int,
    stepCount: Int,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(Res.string.decimal_separator)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.onboarding_step, step, stepCount),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                stringResource(Res.string.onboarding_back),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 16.dp, top = 14.dp),
            )
        }
        Text(
            stringResource(Res.string.download_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(Res.string.download_body), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

        state.label?.let { label ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(Res.string.download_size, megabytes(state.byteSize, separator)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        // Announced as it changes: someone who cannot see the bar hears each state arrive.
        Column(
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val phase = state.phase) {
                DownloadPhase.Queued -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                    Text(stringResource(Res.string.download_queued), style = MaterialTheme.typography.bodyMedium)
                }

                is DownloadPhase.Progress -> {
                    val percent = (phase.fraction * 100).toInt()
                    val description = stringResource(Res.string.download_progress_description, percent)
                    LinearProgressIndicator(
                        progress = { phase.fraction },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
                        color = Accent,
                        trackColor = Hairline,
                    )
                    Text(
                        stringResource(
                            Res.string.download_progress,
                            megabytes(phase.bytesDone, separator),
                            megabytes(phase.bytesTotal, separator),
                            percent,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DownloadPhase.Failed -> {
                    Text(stringResource(failureText(phase.reason)), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                    if (phase.resumable) {
                        Text(
                            stringResource(Res.string.download_failed_kept),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }

                DownloadPhase.Done ->
                    Text(stringResource(Res.string.download_done), style = MaterialTheme.typography.bodyMedium)

                DownloadPhase.Unavailable ->
                    Text(stringResource(Res.string.download_unavailable), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val phase = state.phase
            if (phase is DownloadPhase.Failed) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (phase.resumable) Res.string.download_resume else Res.string.download_restart))
                }
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.download_skip))
                }
            } else {
                Button(
                    onClick = onFinish,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.download_finish))
                }
            }
        }
    }
}

private fun failureText(reason: PackFailure): StringResource = when (reason) {
    PackFailure.NETWORK -> Res.string.download_failed_network
    PackFailure.CHECKSUM -> Res.string.download_failed_checksum
    PackFailure.OUT_OF_SPACE -> Res.string.download_failed_space
    PackFailure.NOT_ENTITLED -> Res.string.download_failed_entitlement
}

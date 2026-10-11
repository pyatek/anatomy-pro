package com.ptk.anatomypro.feature.onboarding

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_onboarding.generated.resources.Res
import anatomypro.shared.feature_onboarding.generated.resources.decimal_separator
import anatomypro.shared.feature_onboarding.generated.resources.download_body
import anatomypro.shared.feature_onboarding.generated.resources.download_done
import anatomypro.shared.feature_onboarding.generated.resources.download_fact_size
import anatomypro.shared.feature_onboarding.generated.resources.download_fact_stored
import anatomypro.shared.feature_onboarding.generated.resources.download_fact_stored_value
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_checksum
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_kept
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_network
import anatomypro.shared.feature_onboarding.generated.resources.download_failed_space
import anatomypro.shared.feature_onboarding.generated.resources.download_finish
import anatomypro.shared.feature_onboarding.generated.resources.download_of
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
import com.ptk.anatomypro.core.designsystem.CorrectGreen
import com.ptk.anatomypro.core.designsystem.FactRow
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.PrimaryButton
import com.ptk.anatomypro.core.designsystem.ProgressRing
import com.ptk.anatomypro.core.designsystem.QuietAction
import com.ptk.anatomypro.core.designsystem.ScreenFooter
import com.ptk.anatomypro.core.designsystem.StepHeader
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 03: the first pack downloads.
 *
 * The prototype's ring, with the share done inside it and the bytes beneath: a ring alone
 * says nothing to a screen reader and nothing about size (spec §4.6: "a download UI that
 * cannot report bytes is a spinner"), so the state is also a sentence, announced as it
 * changes. A failure is a glyph and words, never colour alone (§12).
 *
 * The prototype also shows the network in use, the time remaining and the space free, and
 * offers a pause. The pack contract reports none of the first three and has no pause, so
 * the panel says what is known and the footer offers what can be done.
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
    val type = LocalAnatomyType.current
    val separator = stringResource(Res.string.decimal_separator)
    val phase = state.phase

    Column(modifier = modifier.fillMaxSize()) {
        StepHeader(
            label = stringResource(Res.string.onboarding_step, step, stepCount),
            step = step,
            stepCount = stepCount,
            backLabel = stringResource(Res.string.onboarding_back),
            onBack = onBack,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
        ) {
            val fraction = when (phase) {
                is DownloadPhase.Progress -> phase.fraction
                DownloadPhase.Done -> 1f
                else -> null
            }
            ProgressRing(fraction = fraction, color = if (phase == DownloadPhase.Done) CorrectGreen else Accent) {
                // Said in words below; read twice it would only be noise.
                Column(
                    modifier = Modifier.clearAndSetSemantics { },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val figure = type.value.copy(fontSize = 38.sp, lineHeight = 42.sp)
                    when (phase) {
                        is DownloadPhase.Progress -> {
                            Text("${(phase.fraction * 100).toInt()} %", style = figure, color = TextPrimary)
                            Text(
                                stringResource(
                                    Res.string.download_of,
                                    megabytes(phase.bytesDone, separator),
                                    megabytes(phase.bytesTotal, separator),
                                ),
                                style = type.value.copy(fontSize = 11.sp),
                                color = TextSecondary,
                            )
                        }
                        DownloadPhase.Done -> Text("✓", style = figure, color = CorrectGreen)
                        is DownloadPhase.Failed -> Text("✕", style = figure, color = IncorrectAmber)
                        DownloadPhase.Queued, DownloadPhase.Unavailable -> Text("–", style = figure, color = TextTertiary)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.label ?: stringResource(Res.string.download_title),
                    style = type.title.copy(fontSize = 25.sp),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(Res.string.download_body), style = type.caption, color = TextTertiary, textAlign = TextAlign.Center)
            }

            // Announced as it changes: someone who cannot see the ring hears each state arrive.
            Column(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (phase) {
                    DownloadPhase.Queued -> Text(stringResource(Res.string.download_queued), style = type.body, color = TextSecondary)
                    is DownloadPhase.Progress -> {
                        val description = stringResource(Res.string.download_progress_description, (phase.fraction * 100).toInt())
                        // The figures are in the ring; here they are only spoken.
                        Text("", modifier = Modifier.semantics { contentDescription = description })
                    }
                    is DownloadPhase.Failed -> {
                        Text(stringResource(failureText(phase.reason)), style = type.body, color = IncorrectAmber, textAlign = TextAlign.Center)
                        if (phase.resumable) {
                            Text(stringResource(Res.string.download_failed_kept), style = type.note, color = TextTertiary, textAlign = TextAlign.Center)
                        }
                    }
                    DownloadPhase.Done -> Text(stringResource(Res.string.download_done), style = type.body, color = TextSecondary, textAlign = TextAlign.Center)
                    DownloadPhase.Unavailable ->
                        Text(stringResource(Res.string.download_unavailable), style = type.body, color = TextTertiary, textAlign = TextAlign.Center)
                }
            }

            if (state.label != null) {
                Panel {
                    FactRow(
                        stringResource(Res.string.download_fact_size),
                        stringResource(Res.string.download_size, megabytes(state.byteSize, separator)),
                    )
                    FactRow(stringResource(Res.string.download_fact_stored), stringResource(Res.string.download_fact_stored_value))
                }
            }
        }

        ScreenFooter {
            if (phase is DownloadPhase.Failed) {
                PrimaryButton(
                    text = stringResource(if (phase.resumable) Res.string.download_resume else Res.string.download_restart),
                    onClick = onRetry,
                )
                QuietAction(stringResource(Res.string.download_skip), onClick = onFinish)
            } else {
                PrimaryButton(stringResource(Res.string.download_finish), onClick = onFinish, enabled = state.canContinue)
            }
        }
    }
}

private fun failureText(reason: PackFailure): StringResource = when (reason) {
    PackFailure.NETWORK -> Res.string.download_failed_network
    PackFailure.CHECKSUM -> Res.string.download_failed_checksum
    PackFailure.OUT_OF_SPACE -> Res.string.download_failed_space
}

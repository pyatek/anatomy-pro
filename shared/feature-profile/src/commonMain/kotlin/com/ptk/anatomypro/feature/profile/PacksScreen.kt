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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_profile.generated.resources.Res
import anatomypro.shared.feature_profile.generated.resources.decimal_separator
import anatomypro.shared.feature_profile.generated.resources.packs_available
import anatomypro.shared.feature_profile.generated.resources.packs_back
import anatomypro.shared.feature_profile.generated.resources.packs_cancel
import anatomypro.shared.feature_profile.generated.resources.packs_delete
import anatomypro.shared.feature_profile.generated.resources.packs_delete_confirm
import anatomypro.shared.feature_profile.generated.resources.packs_delete_no
import anatomypro.shared.feature_profile.generated.resources.packs_delete_yes
import anatomypro.shared.feature_profile.generated.resources.packs_download
import anatomypro.shared.feature_profile.generated.resources.packs_downloading
import anatomypro.shared.feature_profile.generated.resources.packs_failed
import anatomypro.shared.feature_profile.generated.resources.packs_failed_checksum
import anatomypro.shared.feature_profile.generated.resources.packs_failed_network
import anatomypro.shared.feature_profile.generated.resources.packs_failed_other
import anatomypro.shared.feature_profile.generated.resources.packs_failed_space
import anatomypro.shared.feature_profile.generated.resources.packs_installed
import anatomypro.shared.feature_profile.generated.resources.packs_loading
import anatomypro.shared.feature_profile.generated.resources.packs_queued
import anatomypro.shared.feature_profile.generated.resources.packs_resume
import anatomypro.shared.feature_profile.generated.resources.packs_restart
import anatomypro.shared.feature_profile.generated.resources.packs_size
import anatomypro.shared.feature_profile.generated.resources.packs_title
import anatomypro.shared.feature_profile.generated.resources.packs_total
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.megabytes
import com.ptk.anatomypro.core.model.PackId
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 19: the pack manager.
 *
 * Every row says its state in words. Nothing is ever locked. Deleting is asked about on the
 * row itself, so the question sits beside the thing it is about.
 */
@Composable
fun PacksScreen(
    state: PacksUiState,
    onDownload: (PackId) -> Unit,
    onCancel: (PackId) -> Unit,
    onDeleteRequested: (PackId) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(Res.string.decimal_separator)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(Res.string.packs_back),
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onBack).padding(top = 14.dp),
        )
        Text(stringResource(Res.string.packs_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        when {
            state.isLoading ->
                Text(stringResource(Res.string.packs_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            state.failed ->
                Text(stringResource(Res.string.packs_failed), style = MaterialTheme.typography.bodyMedium)

            else -> {
                Text(
                    stringResource(Res.string.packs_total, megabytes(state.installedBytes, separator)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (pack in state.packs) {
                        PackRow(
                            pack = pack,
                            separator = separator,
                            confirming = state.confirmingDelete == pack.id,
                            onDownload = { onDownload(pack.id) },
                            onCancel = { onCancel(pack.id) },
                            onDeleteRequested = { onDeleteRequested(pack.id) },
                            onDeleteConfirmed = onDeleteConfirmed,
                            onDeleteDismissed = onDeleteDismissed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: PackState,
    separator: String,
    confirming: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pack.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.packs_size, megabytes(pack.byteSize, separator)),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }

        when (val status = pack.status) {
            PackStatus.Installed -> {
                if (confirming) {
                    Text(stringResource(Res.string.packs_delete_confirm), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action(stringResource(Res.string.packs_delete_yes), onDeleteConfirmed)
                        Action(stringResource(Res.string.packs_delete_no), onDeleteDismissed)
                    }
                } else {
                    Text(stringResource(Res.string.packs_installed), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Action(stringResource(Res.string.packs_delete), onDeleteRequested)
                }
            }

            PackStatus.Available -> {
                Text(stringResource(Res.string.packs_available), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Action(stringResource(Res.string.packs_download), onDownload)
            }

            PackStatus.Queued -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                Text(stringResource(Res.string.packs_queued), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Action(stringResource(Res.string.packs_cancel), onCancel)
            }

            is PackStatus.Downloading -> {
                LinearProgressIndicator(
                    progress = { status.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Hairline,
                )
                Text(
                    stringResource(
                        Res.string.packs_downloading,
                        megabytes(status.bytesDone, separator),
                        megabytes(status.bytesTotal, separator),
                        (status.fraction * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Action(stringResource(Res.string.packs_cancel), onCancel)
            }

            is PackStatus.Failed -> {
                val message = when (status.reason) {
                    PackFailure.NETWORK -> Res.string.packs_failed_network
                    PackFailure.CHECKSUM -> Res.string.packs_failed_checksum
                    PackFailure.OUT_OF_SPACE -> Res.string.packs_failed_space
                    // Cannot occur: packs carry no entitlement (all-screens spec §15.2).
                    PackFailure.NOT_ENTITLED -> Res.string.packs_failed_other
                }
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = IncorrectAmber)
                Action(stringResource(if (status.resumable) Res.string.packs_resume else Res.string.packs_restart), onDownload)
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

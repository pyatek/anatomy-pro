package com.ptk.anatomypro.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import anatomypro.shared.feature_profile.generated.resources.packs_failed_space
import anatomypro.shared.feature_profile.generated.resources.packs_installed
import anatomypro.shared.feature_profile.generated.resources.packs_loading
import anatomypro.shared.feature_profile.generated.resources.packs_on_device
import anatomypro.shared.feature_profile.generated.resources.packs_queued
import anatomypro.shared.feature_profile.generated.resources.packs_restart
import anatomypro.shared.feature_profile.generated.resources.packs_resume
import anatomypro.shared.feature_profile.generated.resources.packs_section_available
import anatomypro.shared.feature_profile.generated.resources.packs_section_downloaded
import anatomypro.shared.feature_profile.generated.resources.packs_size
import anatomypro.shared.feature_profile.generated.resources.packs_title
import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.IncorrectAmber
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.MeterBar
import com.ptk.anatomypro.core.designsystem.NavSurface
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Panel
import com.ptk.anatomypro.core.designsystem.RowAction
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.TopBar
import com.ptk.anatomypro.core.designsystem.megabytes
import com.ptk.anatomypro.core.model.PackId
import org.jetbrains.compose.resources.stringResource

/**
 * Prototype screen 19: the pack manager.
 *
 * The prototype's two lists — what is on the device, and what can be fetched — each pack a
 * card with its one action beside it. A pack being fetched sits with the downloaded ones,
 * with a bar. Nothing is locked: the prototype's "LOCKED" row was drawn before packs became
 * free (all-screens spec §15.2). Its device-storage bar and its Wi-Fi switch need things the
 * app does not read or have, and are left out.
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
    val type = LocalAnatomyType.current
    val separator = stringResource(Res.string.decimal_separator)

    Column(modifier = modifier.fillMaxSize()) {
        TopBar(stringResource(Res.string.packs_title).uppercase(), backLabel = stringResource(Res.string.packs_back), onBack = onBack)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.isLoading -> Text(stringResource(Res.string.packs_loading), style = type.body, color = TextTertiary)
                state.failed -> Text(stringResource(Res.string.packs_failed), style = type.body, color = TextPrimary)
                else -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(Res.string.packs_on_device), style = type.note, color = TextSecondary)
                        Text(
                            stringResource(Res.string.packs_size, megabytes(state.installedBytes, separator)),
                            style = type.value,
                            color = TextSecondary,
                        )
                    }

                    val (onDevice, toFetch) = state.packs.partition { it.status != PackStatus.Available }
                    if (onDevice.isNotEmpty()) {
                        Overline(stringResource(Res.string.packs_section_downloaded, onDevice.size), color = TextSecondary)
                        for (pack in onDevice) {
                            PackCard(
                                pack, separator, confirming = state.confirmingDelete == pack.id,
                                onDownload = { onDownload(pack.id) }, onCancel = { onCancel(pack.id) },
                                onDeleteRequested = { onDeleteRequested(pack.id) },
                                onDeleteConfirmed = onDeleteConfirmed, onDeleteDismissed = onDeleteDismissed,
                            )
                        }
                    }
                    if (toFetch.isNotEmpty()) {
                        Overline(stringResource(Res.string.packs_section_available, toFetch.size), color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                        for (pack in toFetch) {
                            PackCard(
                                pack, separator, confirming = false,
                                onDownload = { onDownload(pack.id) }, onCancel = { onCancel(pack.id) },
                                onDeleteRequested = {}, onDeleteConfirmed = {}, onDeleteDismissed = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: PackState,
    separator: String,
    confirming: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
) {
    val type = LocalAnatomyType.current
    val size = stringResource(Res.string.packs_size, megabytes(pack.byteSize, separator))
    val status = pack.status

    Panel(fill = if (status == PackStatus.Available) NavSurface else Surface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(pack.label, style = type.latin.copy(fontSize = 16.sp), color = TextPrimary)
                Text(
                    when (status) {
                        PackStatus.Installed -> "$size · " + stringResource(Res.string.packs_installed)
                        PackStatus.Available -> "$size · " + stringResource(Res.string.packs_available)
                        PackStatus.Queued -> "$size · " + stringResource(Res.string.packs_queued)
                        is PackStatus.Downloading -> stringResource(
                            Res.string.packs_downloading,
                            megabytes(status.bytesDone, separator),
                            megabytes(status.bytesTotal, separator),
                            (status.fraction * 100).toInt(),
                        )
                        is PackStatus.Failed -> size
                    },
                    style = type.note,
                    color = TextSecondary,
                )
            }
            when (status) {
                PackStatus.Installed -> if (!confirming) RowAction(stringResource(Res.string.packs_delete), onDeleteRequested, color = TextSecondary)
                PackStatus.Available -> RowAction(stringResource(Res.string.packs_download), onDownload)
                PackStatus.Queued, is PackStatus.Downloading -> RowAction(stringResource(Res.string.packs_cancel), onCancel, color = TextSecondary)
                is PackStatus.Failed ->
                    RowAction(stringResource(if (status.resumable) Res.string.packs_resume else Res.string.packs_restart), onDownload, color = Accent)
            }
        }

        when (status) {
            PackStatus.Queued -> MeterBar(0f)
            is PackStatus.Downloading -> MeterBar(status.fraction)
            is PackStatus.Failed -> {
                val message = when (status.reason) {
                    PackFailure.NETWORK -> Res.string.packs_failed_network
                    PackFailure.CHECKSUM -> Res.string.packs_failed_checksum
                    PackFailure.OUT_OF_SPACE -> Res.string.packs_failed_space
                }
                Text(stringResource(message), style = type.caption, color = IncorrectAmber)
            }
            else -> Unit
        }

        // Asked on the row it concerns, not in a dialog (plan decision 5).
        if (confirming) {
            Text(stringResource(Res.string.packs_delete_confirm), style = type.caption, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RowAction(stringResource(Res.string.packs_delete_yes), onDeleteConfirmed, color = IncorrectAmber)
                RowAction(stringResource(Res.string.packs_delete_no), onDeleteDismissed)
            }
        }
    }
}

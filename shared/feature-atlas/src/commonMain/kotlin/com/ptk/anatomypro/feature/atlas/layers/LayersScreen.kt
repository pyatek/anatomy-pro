package com.ptk.anatomypro.feature.atlas.layers

import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.atlas_loading
import anatomypro.shared.feature_atlas.generated.resources.layers_back
import anatomypro.shared.feature_atlas.generated.resources.layers_canvas_isolated
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_less
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_more
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_opacity
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_value
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate_none
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate_target
import anatomypro.shared.feature_atlas.generated.resources.layers_isolation_heading
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_ghosted
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_hidden
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_visible
import anatomypro.shared.feature_atlas.generated.resources.layers_reset
import anatomypro.shared.feature_atlas.generated.resources.layers_state_ghosted
import anatomypro.shared.feature_atlas.generated.resources.layers_state_hidden
import anatomypro.shared.feature_atlas.generated.resources.layers_state_visible
import anatomypro.shared.feature_atlas.generated.resources.layers_title
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.scene.LayerMode
import com.ptk.anatomypro.feature.atlas.scene.LayerPanelUiState
import com.ptk.anatomypro.feature.atlas.scene.LayerRow
import org.jetbrains.compose.resources.stringResource

private const val GHOST_STEP = 5

/** Prototype screen 07: the model above, the panel below. */
@Composable
fun LayersScreen(
    state: LayerPanelUiState,
    focusName: String?,
    canvas: @Composable (Modifier) -> Unit,
    onMode: (SystemId, LayerMode) -> Unit,
    onReset: () -> Unit,
    onIsolate: (Boolean) -> Unit,
    onGhostPercent: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            canvas(Modifier.fillMaxSize())
            if (state.isolate && focusName != null) {
                Text(
                    text = stringResource(Res.string.layers_canvas_isolated, focusName.uppercase(), state.ghostPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val backLabel = stringResource(Res.string.layers_back)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
                            .semantics { role = Role.Button; contentDescription = backLabel }
                            .clickable(onClick = onBack),
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge, color = Accent) }
                    Text(stringResource(Res.string.layers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                // The panel's mutators do nothing until the systems have loaded, so no
                // control is offered before then.
                if (!state.isLoading) {
                    Text(
                        stringResource(Res.string.layers_reset),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                        modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onReset).padding(top = 14.dp),
                    )
                }
            }

            if (state.isLoading) {
                Text(stringResource(Res.string.atlas_loading), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                return@Column
            }

            for (row in state.rows) LayerRowView(row, state.ghostPercent, onMode)

            Text(stringResource(Res.string.layers_isolation_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            // The whole row is the switch, so a screen reader hears the row's text as its
            // label instead of a bare "switch, off".
            val canIsolate = state.focus != null || state.isolate
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    .toggleable(value = state.isolate, role = Role.Switch, enabled = canIsolate, onValueChange = onIsolate),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.layers_isolate), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = focusName?.let { stringResource(Res.string.layers_isolate_target, it) }
                            ?: stringResource(Res.string.layers_isolate_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
                Switch(checked = state.isolate, onCheckedChange = null, enabled = canIsolate)
            }

            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.layers_ghost_opacity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Step("−", stringResource(Res.string.layers_ghost_less)) { onGhostPercent(state.ghostPercent - GHOST_STEP) }
                Text(stringResource(Res.string.layers_ghost_value, state.ghostPercent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
                Step("+", stringResource(Res.string.layers_ghost_more)) { onGhostPercent(state.ghostPercent + GHOST_STEP) }
            }
        }
    }
}

@Composable
private fun LayerRowView(row: LayerRow, ghostPercent: Int, onMode: (SystemId, LayerMode) -> Unit) {
    val names = systemNames(row.system)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(names.latin, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when (row.mode) {
                    LayerMode.Visible -> stringResource(Res.string.layers_state_visible)
                    LayerMode.Ghosted -> stringResource(Res.string.layers_state_ghosted, ghostPercent)
                    LayerMode.Hidden -> stringResource(Res.string.layers_state_hidden, names.local)
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
        for (mode in LayerMode.entries) {
            val isSelected = row.mode == mode
            Text(
                text = stringResource(
                    when (mode) {
                        LayerMode.Visible -> Res.string.layers_mode_visible
                        LayerMode.Ghosted -> Res.string.layers_mode_ghosted
                        LayerMode.Hidden -> Res.string.layers_mode_hidden
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Accent else TextTertiary,
                modifier = Modifier
                    .heightIn(min = 44.dp).widthIn(min = 52.dp)
                    .semantics { role = Role.RadioButton; selected = isSelected }
                    .clickable { onMode(row.system, mode) }
                    .padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun Step(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
    ) { Text(glyph, style = MaterialTheme.typography.titleLarge, color = Accent) }
}

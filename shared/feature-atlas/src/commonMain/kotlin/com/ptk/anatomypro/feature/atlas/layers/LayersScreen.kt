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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.AccentWash
import com.ptk.anatomypro.core.designsystem.CanvasAnnotation
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Overline
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.SurfaceRaised
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextStrong
import com.ptk.anatomypro.core.designsystem.anatomySwitchColors
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
                    style = LocalAnatomyType.current.code,
                    color = CanvasAnnotation,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }

        Column(
            // The prototype's sheet, rising over the canvas.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val backLabel = stringResource(Res.string.layers_back)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
                            .semantics { role = Role.Button; contentDescription = backLabel }
                            .clickable(onClick = onBack),
                    ) { Text("‹", style = LocalAnatomyType.current.title, color = TextStrong) }
                    Text(stringResource(Res.string.layers_title), style = LocalAnatomyType.current.heading.copy(fontSize = 20.sp), color = TextPrimary)
                }
                // The panel's mutators do nothing until the systems have loaded, so no
                // control is offered before then.
                if (!state.isLoading) {
                    Text(
                        stringResource(Res.string.layers_reset),
                        style = LocalAnatomyType.current.label,
                        color = TextSecondary,
                        modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onReset).padding(top = 15.dp),
                    )
                }
            }

            if (state.isLoading) {
                Text(stringResource(Res.string.atlas_loading), style = LocalAnatomyType.current.body, color = TextTertiary)
                return@Column
            }

            for (row in state.rows) LayerRowView(row, state.ghostPercent, onMode)

            HorizontalDivider(color = Hairline, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
            Overline(stringResource(Res.string.layers_isolation_heading), color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
            // The whole row is the switch, so a screen reader hears the row's text as its
            // label instead of a bare "switch, off".
            val canIsolate = state.focus != null || state.isolate
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceRaised)
                    .border(BorderStroke(1.dp, if (state.isolate) Accent else Hairline), RoundedCornerShape(12.dp))
                    .toggleable(value = state.isolate, role = Role.Switch, enabled = canIsolate, onValueChange = onIsolate)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.layers_isolate), style = LocalAnatomyType.current.bodyStrong, color = if (canIsolate) TextPrimary else TextTertiary)
                    Text(
                        text = focusName?.let { stringResource(Res.string.layers_isolate_target, it) }
                            ?: stringResource(Res.string.layers_isolate_none),
                        style = LocalAnatomyType.current.note,
                        color = TextSecondary,
                    )
                }
                Switch(checked = state.isolate, onCheckedChange = null, enabled = canIsolate, colors = anatomySwitchColors())
            }

            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.layers_ghost_opacity), style = LocalAnatomyType.current.body, color = TextStrong, modifier = Modifier.weight(1f))
                Step("−", stringResource(Res.string.layers_ghost_less)) { onGhostPercent(state.ghostPercent - GHOST_STEP) }
                Text(stringResource(Res.string.layers_ghost_value, state.ghostPercent), style = LocalAnatomyType.current.value, color = TextPrimary, modifier = Modifier.padding(horizontal = 8.dp))
                Step("+", stringResource(Res.string.layers_ghost_more)) { onGhostPercent(state.ghostPercent + GHOST_STEP) }
            }
        }
    }
}

@Composable
private fun LayerRowView(row: LayerRow, ghostPercent: Int, onMode: (SystemId, LayerMode) -> Unit) {
    val names = systemNames(row.system)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(SurfaceRaised)
            // Fully visible is outlined, as in the prototype; the row says its state in words too.
            .border(BorderStroke(1.dp, if (row.mode == LayerMode.Visible) Accent else Hairline), shape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(names.latin, style = LocalAnatomyType.current.latin.copy(fontSize = 16.sp), color = TextPrimary)
            Text(
                text = when (row.mode) {
                    LayerMode.Visible -> stringResource(Res.string.layers_state_visible)
                    LayerMode.Ghosted -> stringResource(Res.string.layers_state_ghosted, ghostPercent)
                    LayerMode.Hidden -> stringResource(Res.string.layers_state_hidden, names.local)
                },
                style = LocalAnatomyType.current.note,
                color = TextSecondary,
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
                style = LocalAnatomyType.current.code,
                textAlign = TextAlign.Center,
                color = if (isSelected) HighlightOutline else TextTertiary,
                modifier = Modifier
                    .heightIn(min = 44.dp).widthIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; selected = isSelected }
                    .clickable { onMode(row.system, mode) }
                    .padding(horizontal = 2.dp, vertical = 8.dp)
                    .then(
                        if (isSelected) Modifier.clip(RoundedCornerShape(4.dp)).background(AccentWash).border(BorderStroke(1.dp, AccentMuted), RoundedCornerShape(4.dp))
                        else Modifier,
                    )
                    .padding(horizontal = 4.dp, vertical = 7.dp),
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
    ) { Text(glyph, style = LocalAnatomyType.current.heading, color = TextStrong) }
}

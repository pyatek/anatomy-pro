package com.ptk.anatomypro.feature.atlas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.atlas_empty
import anatomypro.shared.feature_atlas.generated.resources.atlas_load_failed
import anatomypro.shared.feature_atlas.generated.resources.atlas_loading
import anatomypro.shared.feature_atlas.generated.resources.atlas_select_prompt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import anatomypro.shared.feature_atlas.generated.resources.atlas_selected
import com.ptk.anatomypro.core.designsystem.AccentMuted
import com.ptk.anatomypro.core.designsystem.Badge
import com.ptk.anatomypro.core.designsystem.Ground
import com.ptk.anatomypro.core.designsystem.Hairline
import com.ptk.anatomypro.core.designsystem.HighlightOutline
import com.ptk.anatomypro.core.designsystem.LocalAnatomyType
import com.ptk.anatomypro.core.designsystem.Surface
import com.ptk.anatomypro.core.designsystem.SurfaceRaised
import com.ptk.anatomypro.core.designsystem.TextPrimary
import com.ptk.anatomypro.core.designsystem.TextSecondary
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.designsystem.SideBadge
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.StructureId
import org.jetbrains.compose.resources.stringResource

/**
 * Browse the taxonomy and highlight what is selected.
 *
 * A pure function of [state]: every interaction leaves through a callback, so the screen
 * can be driven in a test without a database or a renderer behind it (spec §15).
 */
@Composable
fun AtlasScreen(
    state: AtlasUiState,
    /** The selected structure that has geometry; a selected group marks no row, as it outlines nothing. */
    highlighted: StructureId?,
    /** The selection's name in the interface language, or null when nothing is selected. */
    selectedName: String?,
    canvas: @Composable (Modifier) -> Unit,
    onRowToggled: (StructureId) -> Unit,
    onRowSelected: (StructureSummary) -> Unit,
    modifier: Modifier = Modifier,
    latinOnly: Boolean = false,
) {
    val type = LocalAnatomyType.current
    Column(modifier = modifier.fillMaxSize()) {
        canvas(Modifier.fillMaxWidth().weight(1f))
        // The prototype's sheet: what is selected, named in the serif, over the list.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedName != null) {
                Text(selectedName, style = type.title.copy(fontSize = 22.sp, lineHeight = 26.sp), color = TextPrimary, modifier = Modifier.weight(1f))
                Badge(stringResource(Res.string.atlas_selected), color = HighlightOutline, outline = AccentMuted)
            } else {
                Text(stringResource(Res.string.atlas_select_prompt), style = type.body, color = TextTertiary, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        when {
            state.isLoading -> Message(stringResource(Res.string.atlas_loading))
            state.error != null -> Message(stringResource(Res.string.atlas_load_failed))
            state.rows.isEmpty() -> Message(stringResource(Res.string.atlas_empty))
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(state.rows, key = { it.summary.id.value }) { row ->
                    TaxonomyRow(
                        row = row,
                        isSelected = highlighted == row.summary.id,
                        onToggle = { onRowToggled(row.summary.id) },
                        onSelect = { onRowSelected(row.summary) },
                        latinOnly = latinOnly,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaxonomyRow(
    row: AtlasRow,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    latinOnly: Boolean,
) {
    val type = LocalAnatomyType.current
    // Selected is a filled row and an accent name; the name also heads the sheet above.
    val background = if (isSelected) SurfaceRaised else Ground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onSelect)
            // §12 asks for 44dp targets; a tree row is the densest thing on this screen.
            .heightIn(min = 44.dp)
            .padding(start = (12 + row.depth * 16).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clickable(enabled = row.summary.hasChildren, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (row.summary.hasChildren) {
                Text(if (row.expanded) "–" else "+", style = type.value, color = TextSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (latinOnly) row.summary.latinName ?: row.summary.name else row.summary.name,
                    // A group reads as a heading; Latin, wherever it leads, is in the serif.
                    style = if (latinOnly || row.summary.latinName == row.summary.name) {
                        type.latin.copy(fontSize = 15.sp, fontWeight = if (row.summary.isGroup) FontWeight.SemiBold else FontWeight.Normal)
                    } else {
                        type.body.copy(fontWeight = if (row.summary.isGroup) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    color = if (isSelected) HighlightOutline else TextPrimary,
                )
                SideBadge(row.summary.laterality)
            }
            if (!latinOnly) {
                row.summary.latinName
                    ?.takeIf { it != row.summary.name }
                    ?.let { Text(it, style = type.note.copy(fontFamily = type.latin.fontFamily), color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalAnatomyType.current.caption, color = TextTertiary)
    }
}

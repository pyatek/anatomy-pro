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
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.model.StructureId

/**
 * Browse the taxonomy and highlight what is selected.
 *
 * A pure function of [state]: every interaction leaves through a callback, so the screen
 * can be driven in a test without a database or a renderer behind it (spec §15).
 */
@Composable
fun AtlasScreen(
    state: AtlasUiState,
    canvas: @Composable (Modifier) -> Unit,
    onRowToggled: (StructureId) -> Unit,
    onRowSelected: (StructureSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        canvas(Modifier.fillMaxWidth().weight(1f))

        Text(
            text = state.selectedName ?: "select a structure",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.isLoading -> Message("Loading atlas…")
            state.error != null -> Message(state.error)
            state.rows.isEmpty() -> Message("No structures installed")
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(state.rows, key = { it.summary.id.value }) { row ->
                    TaxonomyRow(
                        row = row,
                        isSelected = state.selected == row.summary.id,
                        onToggle = { onRowToggled(row.summary.id) },
                        onSelect = { onRowSelected(row.summary) },
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
) {
    val background =
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface

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
                Text(if (row.expanded) "–" else "+", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.summary.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.summary.isGroup) FontWeight.SemiBold else FontWeight.Normal,
            )
            row.summary.latinName
                ?.takeIf { it != row.summary.name }
                ?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

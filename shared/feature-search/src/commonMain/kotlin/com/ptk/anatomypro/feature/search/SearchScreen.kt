package com.ptk.anatomypro.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary

/** Prototype screen 06: one field, every language at once, the match badged. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onHitSelected: (SearchHit) -> Unit,
    onRecentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            singleLine = true,
            label = { Text("Szukaj") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        Text(
            text = when {
                !state.hasQuery -> "SZUKA W LAT · POL · ENG"
                state.isSearching -> "Szukanie…"
                else -> "${state.hits.size} wyników"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        if (!state.hasQuery) {
            if (state.recent.isNotEmpty()) {
                Text("OSTATNIE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                for (term in state.recent) {
                    Text(
                        text = term,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clickable { onRecentSelected(term) }.padding(top = 12.dp),
                    )
                }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.hits, key = { it.summary.id.value }) { hit ->
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable { onHitSelected(hit) }.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = hit.summary.latinName ?: hit.summary.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (hit.summary.isGroup) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = "MATCH · ${hit.matchedLocale.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent,
                        )
                    }
                    if (hit.summary.name != hit.summary.latinName) {
                        Text(
                            text = hit.summary.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

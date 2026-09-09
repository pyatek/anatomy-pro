package com.ptk.anatomypro.feature.atlas

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary

/** Prototype screen 05: read about one structure. */
@Composable
fun StructureDetailScreen(
    state: StructureDetailUiState,
    onBack: () -> Unit,
    onAncestorSelected: (com.ptk.anatomypro.core.model.StructureId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "‹",
                fontSize = 24.sp,
                color = Accent,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onBack).padding(horizontal = 8.dp),
            )
            SectionLabel("STRUKTURA")
        }

        when {
            state.isLoading -> Note("Wczytywanie…")
            state.error != null -> Note(state.error)
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.title?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }

                if (state.otherNames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((locale, name) in state.otherNames) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = locale.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    modifier = Modifier.heightIn(min = 20.dp),
                                )
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                state.detail?.definition?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("DEFINICJA")
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                val ancestors = state.detail?.ancestors.orEmpty()
                if (ancestors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("POZYCJA W HIERARCHII")
                        ancestors.forEachIndexed { depth, ancestor ->
                            Text(
                                text = ancestor.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clickable { onAncestorSelected(ancestor.id) }
                                    .padding(start = (depth * 12).dp, top = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}

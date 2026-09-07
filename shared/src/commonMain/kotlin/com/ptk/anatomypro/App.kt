package com.ptk.anatomypro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.core.model.StructureId

/**
 * Phase 0 harness. Throwaway.
 *
 * It exists to answer one question on a real device: does a glTF load, draw, and pick
 * through Filament (spec §16). It is replaced by feature-atlas in Phase 1.
 */
@Composable
fun App() {
    AnatomyTheme {
        var picked: StructureId? by remember { mutableStateOf(null) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Anatomy Pro — Phase 0", style = MaterialTheme.typography.headlineSmall)
                Text(getPlatform().name, style = MaterialTheme.typography.bodySmall)

                AnatomyCanvas(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onPicked = { picked = it },
                )

                Text(
                    text = picked?.value ?: "tap a shape",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

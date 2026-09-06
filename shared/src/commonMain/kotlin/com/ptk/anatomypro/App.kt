package com.ptk.anatomypro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.renderer.filament.createAnatomyRenderer

/**
 * Phase 0 harness. Throwaway.
 *
 * Its only job is to prove the module graph links and runs on both platforms. It is
 * replaced by feature-atlas in Phase 1 (spec §16).
 */
@Composable
fun App() {
    AnatomyTheme {
        val rendererStatus = remember {
            runCatching { createAnatomyRenderer() }
                .fold(
                    onSuccess = { "Renderer ready: ${it::class.simpleName}" },
                    onFailure = { "Renderer not implemented yet — this is Phase 0's job" },
                )
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Anatomy Pro", style = MaterialTheme.typography.headlineMedium)
                Text(getPlatform().name, style = MaterialTheme.typography.bodyMedium)
                Text(rendererStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

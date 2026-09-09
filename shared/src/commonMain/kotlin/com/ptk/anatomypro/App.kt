package com.ptk.anatomypro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.navigation.AnatomyBottomBar
import com.ptk.anatomypro.navigation.AnatomyDestination

/**
 * The app shell: five top-level destinations behind the prototype's bottom bar.
 *
 * Only Atlas is built. The other four are named placeholders rather than hidden tabs,
 * because the shape of the product is a decision already made in the prototype and a bar
 * that grows tabs later would relayout under the user.
 */
@Composable
fun App() {
    AnatomyTheme {
        var destination by rememberSaveable { mutableStateOf(AnatomyDestination.Atlas) }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (destination) {
                        AnatomyDestination.Atlas -> AtlasTab()
                        else -> Placeholder(destination)
                    }
                }
                AnatomyBottomBar(
                    selected = destination,
                    onSelect = { destination = it },
                )
            }
        }
    }
}

@Composable
private fun Placeholder(destination: AnatomyDestination) =
    Placeholder(text = "${destination.label} — jeszcze nie zbudowane")

@Composable
private fun Placeholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

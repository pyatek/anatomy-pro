package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptk.anatomypro.core.model.StructureId

/**
 * Draws the Phase 0 toy pack and reports taps.
 *
 * The platform entry point owns the surface (spec §4.1), so this is the one place the
 * shared module has to know which platform it is on. Everything above it deals in
 * [StructureId] and knows nothing about Metal, layers, or frames.
 */
@Composable
expect fun AnatomyCanvas(
    modifier: Modifier,
    onPicked: (StructureId?) -> Unit,
)

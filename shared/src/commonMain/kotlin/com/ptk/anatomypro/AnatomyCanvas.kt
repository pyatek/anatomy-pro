package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ptk.anatomypro.core.model.StructureId

/**
 * What the Phase 0 harness needs to show to be worth running.
 *
 * §16's gate is a frame rate against a structure count, so the harness has to display
 * both or looking at it tells you nothing.
 */
data class CanvasStats(
    val fps: Int = 0,
    val structures: Int = 0,
    val pack: String = "",
    /** The GPU's own time for the last frame. Distinguishes GPU-bound from paced. */
    val gpuMillis: Float = 0f,
    /** The display's refresh rate, which is the ceiling the frame rate is measured against. */
    val refreshHz: Int = 0,
)

/**
 * Draws the Phase 0 pack and reports taps.
 *
 * The platform entry point owns the surface (spec §4.1), so this is the one place the
 * shared module has to know which platform it is on. Everything above it deals in
 * [StructureId] and knows nothing about Metal, layers, or frames.
 */
@Composable
expect fun AnatomyCanvas(
    modifier: Modifier,
    highlighted: StructureId?,
    onPicked: (StructureId?) -> Unit,
    onStats: (CanvasStats) -> Unit,
)

package com.ptk.anatomypro.renderer.api

import kotlin.jvm.JvmInline

/** Where a pack's glTF payload can be read from, already resolved to a concrete location. */
@JvmInline
value class MeshSource(val uri: String) {
    init { require(uri.isNotBlank()) { "MeshSource uri must not be blank" } }
}

/** Whether an outline is drawn solid or dashed. A non-colour channel for state. */
enum class OutlineStyle { SOLID, DASHED }

/**
 * How a highlighted structure is drawn.
 *
 * Carries four channels — outline colour, outline width, outline style, and a luminance
 * shift — precisely so that no state is ever distinguished by hue alone (spec §12). The
 * design relies on this: a correct answer is a solid outline and a wrong one is dashed, and
 * that difference survives any form of colour blindness. Concrete token values live in
 * core-designsystem; this module owns only the shape.
 */
data class HighlightStyle(
    val outlineArgb: Int,
    val outlineWidthDp: Float,
    val outlineStyle: OutlineStyle,
    val fillArgb: Int,
    val fillLuminanceShift: Float,
) {
    init {
        require(outlineWidthDp > 0f) { "outlineWidthDp must be positive" }
        require(fillLuminanceShift in -1f..1f) { "fillLuminanceShift must be in -1..1" }
    }
}

/** Camera position in orbit terms around a focus point. */
data class CameraPose(
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val distance: Float,
    val azimuthDeg: Float,
    val elevationDeg: Float,
) {
    init { require(distance > 0f) { "distance must be positive" } }
}

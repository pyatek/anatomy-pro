package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.HighlightStyle

/**
 * Structures outlined alike: one mask, one colour, one width.
 *
 * The colour is the style's bytes as fractions, unconverted. The outline is written to the
 * frame without tone mapping, so these are the bytes the frame ends up holding.
 */
data class OutlineGroup(
    val structures: Set<StructureId>,
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val widthPx: Float,
)

/**
 * A highlight map as the outlines to draw.
 *
 * In shared code, like [HighlightPaint], so both renderers are handed the same groups and
 * the same numbers. Styles that share an outline colour and width share a group, whatever
 * their fill: a group is a mask, and a mask costs a render target.
 */
object OutlinePlan {

    const val MIN_WIDTH_PX = 1f

    /** The material samples two rings; far beyond this a ring steps over what it should find. */
    const val MAX_WIDTH_PX = 32f

    fun of(styles: Map<StructureId, HighlightStyle>, pixelsPerDp: Float): List<OutlineGroup> {
        // A host that cannot say its density must not make the outline vanish or fill the screen.
        val density = if (pixelsPerDp.isFinite() && pixelsPerDp > 0f) pixelsPerDp else 1f
        return styles.entries
            .groupBy({ it.value.outlineArgb to it.value.outlineWidthDp }, { it.key })
            .map { (outline, structures) ->
                val (argb, widthDp) = outline
                OutlineGroup(
                    structures = structures.toSet(),
                    red = ((argb shr 16) and 0xFF) / 255f,
                    green = ((argb shr 8) and 0xFF) / 255f,
                    blue = (argb and 0xFF) / 255f,
                    alpha = ((argb ushr 24) and 0xFF) / 255f,
                    widthPx = (widthDp * density).coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX),
                )
            }
    }
}

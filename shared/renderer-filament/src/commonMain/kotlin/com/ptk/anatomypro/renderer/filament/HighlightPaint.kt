package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.HighlightStyle

/**
 * A highlight as the two material parameters the renderers set: `baseColorFactor` and
 * `emissiveFactor`.
 *
 * In shared code so both renderers are handed the same numbers for a style, and both read
 * the base colour as sRGB, so the same style is the same colour on the two. The colour is
 * the style's outline colour, as it has been on both platforms; the outline itself is drawn
 * by the outline pass, from [OutlinePlan].
 *
 * A positive luminance shift is light the structure gives off, in its own colour. A
 * negative one darkens the tint. Spec §12 forbids telling two states apart by hue alone,
 * so the shift has to survive in both directions.
 */
data class HighlightPaint(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val emissiveRed: Float,
    val emissiveGreen: Float,
    val emissiveBlue: Float,
) {
    companion object {
        fun of(style: HighlightStyle): HighlightPaint {
            val argb = style.outlineArgb
            val alpha = ((argb ushr 24) and 0xFF) / 255f
            val red = ((argb shr 16) and 0xFF) / 255f
            val green = ((argb shr 8) and 0xFF) / 255f
            val blue = (argb and 0xFF) / 255f

            val shift = style.fillLuminanceShift
            val lift = shift.coerceAtLeast(0f)
            val keep = (1f + shift.coerceAtMost(0f)).coerceAtLeast(0f)
            return HighlightPaint(
                red = red * keep,
                green = green * keep,
                blue = blue * keep,
                alpha = alpha,
                emissiveRed = red * lift,
                emissiveGreen = green * lift,
                emissiveBlue = blue * lift,
            )
        }
    }
}

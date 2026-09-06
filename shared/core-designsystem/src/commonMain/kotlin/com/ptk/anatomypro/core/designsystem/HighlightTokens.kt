package com.ptk.anatomypro.core.designsystem

import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.OutlineStyle

/**
 * The concrete highlight values the renderer draws.
 *
 * Colours and outline styles come from the design; the luminance shifts are chosen here,
 * since the design states the rule rather than a number. Spec §12 forbids conveying state by
 * hue alone, and `HighlightTokensTest` enforces that mechanically — add a token without a
 * luminance shift, or one indistinguishable from an existing token to a colour-blind user,
 * and the build fails.
 */
object HighlightTokens {

    /** The structure the user tapped. */
    val Selected = HighlightStyle(
        outlineArgb = HighlightOutline.toArgbInt(),
        outlineWidthDp = 2f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = HighlightFill.toArgbInt(),
        fillLuminanceShift = 0.25f,
    )

    /** A correct answer. Always paired with a ✓ glyph and a solid outline. */
    val Correct = HighlightStyle(
        outlineArgb = CorrectGreen.toArgbInt(),
        outlineWidthDp = 3f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = CorrectGreen.toArgbInt(),
        fillLuminanceShift = 0.35f,
    )

    /** The learner's wrong answer. Always paired with a ✕ glyph and a dashed outline. */
    val Incorrect = HighlightStyle(
        outlineArgb = IncorrectAmber.toArgbInt(),
        outlineWidthDp = 3f,
        outlineStyle = OutlineStyle.DASHED,
        fillArgb = IncorrectAmber.toArgbInt(),
        fillLuminanceShift = -0.20f,
    )
}

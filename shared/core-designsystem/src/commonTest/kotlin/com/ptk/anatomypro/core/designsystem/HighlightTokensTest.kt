package com.ptk.anatomypro.core.designsystem

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class HighlightTokensTest {

    private val tokens = listOf(
        "Selected" to HighlightTokens.Selected,
        "Correct" to HighlightTokens.Correct,
        "Incorrect" to HighlightTokens.Incorrect,
    )

    @Test
    fun every_token_shifts_luminance_so_state_survives_colour_blindness() {
        for ((name, style) in tokens) {
            assertTrue(
                abs(style.fillLuminanceShift) >= 0.15f,
                "$name relies on hue alone; spec §12 requires a luminance shift too",
            )
        }
    }

    @Test
    fun tokens_are_distinguishable_from_each_other_without_hue() {
        for (i in tokens.indices) {
            for (j in i + 1 until tokens.size) {
                val (nameA, a) = tokens[i]
                val (nameB, b) = tokens[j]
                val luminanceDelta = abs(relativeLuminance(a.outlineArgb) - relativeLuminance(b.outlineArgb))
                val shiftDelta = abs(a.fillLuminanceShift - b.fillLuminanceShift)
                val widthDelta = abs(a.outlineWidthDp - b.outlineWidthDp)
                val styleDiffers = a.outlineStyle != b.outlineStyle
                assertTrue(
                    styleDiffers || luminanceDelta >= 0.10f || shiftDelta >= 0.15f || widthDelta >= 1f,
                    "$nameA and $nameB differ only in hue and would be identical to a " +
                        "colour-blind user; spec §12",
                )
            }
        }
    }

    @Test
    fun relative_luminance_is_zero_for_black_and_one_for_white() {
        assertTrue(relativeLuminance(0xFF000000.toInt()) < 0.01f)
        assertTrue(relativeLuminance(0xFFFFFFFF.toInt()) > 0.99f)
    }
}

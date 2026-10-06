package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.OutlineStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightPaintTest {

    private fun style(argb: Long, shift: Float) = HighlightStyle(
        outlineArgb = argb.toInt(),
        outlineWidthDp = 2f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = argb.toInt(),
        fillLuminanceShift = shift,
    )

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.001f, "expected $expected, was $actual")

    @Test
    fun with_no_shift_the_tint_is_the_colour_and_nothing_glows() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = 0f))

        assertNear(0x80 / 255f, paint.red)
        assertNear(0x40 / 255f, paint.green)
        assertNear(0x20 / 255f, paint.blue)
        assertEquals(0f, paint.emissiveRed)
        assertEquals(0f, paint.emissiveGreen)
        assertEquals(0f, paint.emissiveBlue)
    }

    @Test
    fun a_positive_shift_makes_the_structure_glow_in_its_own_colour() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = 0.5f))

        assertNear(0x80 / 255f, paint.red)
        assertNear(0x80 / 255f * 0.5f, paint.emissiveRed)
        assertNear(0x40 / 255f * 0.5f, paint.emissiveGreen)
        assertNear(0x20 / 255f * 0.5f, paint.emissiveBlue)
    }

    @Test
    fun a_negative_shift_darkens_the_tint_instead_of_being_thrown_away() {
        // §12: never by hue alone. The wrong answer's token asks to be darker, and until
        // now both renderers clamped that request to nothing.
        val paint = HighlightPaint.of(style(0xFF804020, shift = -0.25f))

        assertNear(0x80 / 255f * 0.75f, paint.red)
        assertNear(0x40 / 255f * 0.75f, paint.green)
        assertNear(0x20 / 255f * 0.75f, paint.blue)
        assertEquals(0f, paint.emissiveRed)
    }

    @Test
    fun a_shift_of_minus_one_is_black_not_a_negative_colour() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = -1f))

        assertEquals(0f, paint.red)
        assertEquals(0f, paint.green)
        assertEquals(0f, paint.blue)
    }

    @Test
    fun alpha_is_carried_and_never_shifted() {
        assertNear(0x80 / 255f, HighlightPaint.of(style(0x80804020, shift = -0.5f)).alpha)
        assertNear(1f, HighlightPaint.of(style(0xFF804020, shift = 0.5f)).alpha)
    }
}

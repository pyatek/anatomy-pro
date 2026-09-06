package com.ptk.anatomypro.core.designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

class ColorContrastTest {

    private fun assertReadable(name: String, foreground: Int, background: Int) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= 4.5f, "$name has contrast ${ratio}:1, below the 4.5:1 minimum")
    }

    @Test
    fun dark_palette_text_clears_the_minimum_on_the_surface_it_sits_on() {
        assertReadable("TextPrimary on Ground", TextPrimary.toArgbInt(), Ground.toArgbInt())
        assertReadable("TextSecondary on Ground", TextSecondary.toArgbInt(), Ground.toArgbInt())
        assertReadable("TextTertiary on Ground", TextTertiary.toArgbInt(), Ground.toArgbInt())
        assertReadable("TextTertiary on Surface", TextTertiary.toArgbInt(), Surface.toArgbInt())
        assertReadable("CanvasAnnotation on Ground", CanvasAnnotation.toArgbInt(), Ground.toArgbInt())
    }

    @Test
    fun light_palette_text_clears_the_minimum_on_the_surface_it_sits_on() {
        assertReadable("LightTextPrimary on LightGround", LightTextPrimary.toArgbInt(), LightGround.toArgbInt())
        assertReadable("LightTextTertiary on LightGround", LightTextTertiary.toArgbInt(), LightGround.toArgbInt())
        assertReadable("AccentOnLight on its surface", AccentOnLight.toArgbInt(), AccentOnLightSurface.toArgbInt())
    }
}

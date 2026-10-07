package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertEquals

class FramePixelsTest {

    private fun frame(vararg rgba: Int) = ByteArray(rgba.size) { rgba[it].toByte() }

    @Test
    fun counts_pixels_of_the_colour_whatever_their_alpha() {
        val pixels = frame(
            255, 0, 255, 255,
            255, 0, 255, 0,
            0, 0, 0, 255,
        )

        assertEquals(2, FramePixels.countMatching(pixels, argb = 0xFFFF00FF.toInt()))
    }

    @Test
    fun allows_each_channel_to_be_off_by_the_tolerance_and_no_more() {
        val pixels = frame(
            253, 2, 254, 255,
            252, 0, 255, 255,
        )

        assertEquals(1, FramePixels.countMatching(pixels, argb = 0xFFFF00FF.toInt(), tolerance = 2))
    }

    @Test
    fun an_empty_frame_has_none_and_a_ragged_tail_is_ignored() {
        assertEquals(0, FramePixels.countMatching(ByteArray(0), argb = 0xFFFF00FF.toInt()))
        assertEquals(1, FramePixels.countMatching(frame(255, 0, 255, 255, 255, 0), argb = 0xFFFF00FF.toInt()))
    }
}

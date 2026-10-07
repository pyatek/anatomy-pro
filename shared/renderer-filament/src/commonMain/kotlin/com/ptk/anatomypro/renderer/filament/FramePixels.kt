package com.ptk.anatomypro.renderer.filament

import kotlin.math.abs

/** Reading a frame the renderer handed back, for tests. Four bytes a pixel: R, G, B, A. */
object FramePixels {

    /**
     * How many pixels are [argb]'s colour, each channel within [tolerance].
     *
     * Alpha is not compared: what a swap chain keeps in it differs by platform, and the
     * question a test asks is what colour was drawn.
     */
    fun countMatching(rgba: ByteArray, argb: Int, tolerance: Int = 2): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        var count = 0
        var index = 0
        while (index + 3 < rgba.size) {
            if (
                abs((rgba[index].toInt() and 0xFF) - red) <= tolerance &&
                abs((rgba[index + 1].toInt() and 0xFF) - green) <= tolerance &&
                abs((rgba[index + 2].toInt() and 0xFF) - blue) <= tolerance
            ) {
                count++
            }
            index += 4
        }
        return count
    }
}

package com.ptk.anatomypro.renderer.filament

import kotlin.math.abs

/**
 * What the middle of the toy fixture's centre cube looks like, in frame bytes.
 *
 * Measured, not derived: the numbers are what the iOS renderer drew on 2026-10-07. Their
 * worth is that both platforms' tests name the same ones. With nothing highlighted the two
 * renderers already drew the identical pixel, so a difference under a tint or a ghost is a
 * difference in how the colour was read, not in lighting or tone mapping — and it was one:
 * Android passed the colour through unconverted where iOS read it as sRGB, and drew a
 * mid-grey tint at 155 where iOS drew 96.
 */
object ReferenceColours {

    /** No highlight, no ghost. */
    val PLAIN = intArrayOf(188, 186, 184)

    /** Tinted `FF808080` with no luminance shift. */
    val MID_GREY_TINT = intArrayOf(96, 97, 98)

    /** Ghosted at alpha 0.5, over the black background. */
    val HALF_GHOST = intArrayOf(76, 74, 73)

    /** Two backends round differently; a colour-space mistake is off by dozens. */
    const val TOLERANCE = 6

    /** The pixel in the middle of a [width] by [height] frame of R, G, B, A bytes. */
    fun centreOf(rgba: ByteArray, width: Int, height: Int): IntArray {
        val index = ((height / 2) * width + width / 2) * 4
        return IntArray(3) { rgba[index + it].toInt() and 0xFF }
    }

    fun matches(actual: IntArray, expected: IntArray): Boolean =
        actual.indices.all { abs(actual[it] - expected[it]) <= TOLERANCE }
}

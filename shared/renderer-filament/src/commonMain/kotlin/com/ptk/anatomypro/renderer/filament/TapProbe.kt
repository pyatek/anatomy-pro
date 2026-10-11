package com.ptk.anatomypro.renderer.filament

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a tap looks, and what it takes from what it finds.
 *
 * A tap is a finger, not a pixel. Vessels and nerves are drawn at their own radius, half a
 * millimetre for most, which is a pixel or two on a phone (spec §38): asking only the pixel
 * under the finger made every one of them all but impossible to select, and each is a
 * possible "find the structure" answer. So a tap asks the pixel under it first, and when
 * nothing is there, rings of pixels around it, taking the nearest thing found.
 *
 * What is under the finger always wins. The reach only turns a miss into a near hit; it
 * never takes a tap away from the structure it landed on.
 */
internal object TapProbe {

    /** How far from the tap is still the tap, in dp: about a quarter of a 44 dp target. */
    const val REACH_DP = 12f

    private const val RINGS = 2
    private const val PER_RING = 8

    /**
     * Offsets from the tap, in pixels, nearest first: the tap, then [RINGS] rings out to
     * [reachPx]. Alternate rings are turned half a step, so between them they look in
     * sixteen directions.
     */
    fun offsets(reachPx: Float): List<Pair<Float, Float>> = buildList {
        add(0f to 0f)
        if (reachPx <= 0f) return@buildList
        for (ring in 1..RINGS) {
            val radius = reachPx * ring / RINGS
            val turn = if (ring % 2 == 0) PI / PER_RING else 0.0
            for (step in 0 until PER_RING) {
                val angle = 2 * PI * step / PER_RING + turn
                add((radius * cos(angle)).toFloat() to (radius * sin(angle)).toFloat())
            }
        }
    }

    /** The verdict of a tap, from what each probe found in the order of [offsets]. */
    fun <T : Any> nearest(found: List<T?>): T? = found.firstOrNull { it != null }
}

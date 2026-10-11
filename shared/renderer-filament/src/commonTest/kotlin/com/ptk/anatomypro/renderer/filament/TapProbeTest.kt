package com.ptk.anatomypro.renderer.filament

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapProbeTest {

    @Test
    fun the_tap_itself_is_asked_first() {
        assertEquals(0f to 0f, TapProbe.offsets(reachPx = 30f).first())
    }

    @Test
    fun no_probe_is_further_than_the_reach_and_some_are_at_it() {
        val distances = TapProbe.offsets(reachPx = 30f).map { (x, y) -> hypot(x, y) }

        assertTrue(distances.all { it <= 30.01f }, "a probe beyond the reach: $distances")
        assertTrue(distances.any { it > 29f }, "nothing probes the edge of the reach: $distances")
    }

    @Test
    fun probes_are_in_order_of_distance_so_the_first_hit_is_the_nearest() {
        val distances = TapProbe.offsets(reachPx = 30f).map { (x, y) -> hypot(x, y) }

        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun a_ring_looks_in_every_direction() {
        // A vessel can pass the finger on any side.
        val outer = TapProbe.offsets(reachPx = 30f).filter { (x, y) -> hypot(x, y) > 29f }

        assertTrue(outer.any { it.first > 20f } && outer.any { it.first < -20f })
        assertTrue(outer.any { it.second > 20f } && outer.any { it.second < -20f })
    }

    @Test
    fun no_reach_is_one_probe_at_the_tap() {
        assertEquals(listOf(0f to 0f), TapProbe.offsets(reachPx = 0f))
    }

    @Test
    fun what_is_under_the_finger_wins_over_anything_nearby() {
        assertEquals("bone", TapProbe.nearest(listOf("bone", "vessel", null, "vessel")))
    }

    @Test
    fun a_tap_on_nothing_takes_the_nearest_thing_within_reach() {
        // Half a millimetre of vessel is a pixel or two: the finger lands beside it.
        assertEquals("vessel", TapProbe.nearest(listOf(null, null, "vessel", "nerve")))
    }

    @Test
    fun a_tap_with_nothing_within_reach_is_a_tap_on_nothing() {
        assertNull(TapProbe.nearest(listOf<String?>(null, null, null)))
    }
}

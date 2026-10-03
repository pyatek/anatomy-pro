package com.ptk.anatomypro.renderer.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraFramingTest {

    private fun near(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 1e-4f, "expected $expected, was $actual")

    private val unitCube = WorldBox(Vec3(0f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))

    @Test
    fun framing_matches_the_formula_both_platforms_already_use_for_the_whole_model() {
        val shot = CameraFraming.frame(unitCube)
        val radius = sqrt(0.75f)
        val distance = (radius / tan(22.5 * PI / 180) * 1.6).toFloat()

        near(distance, shot.eye.z)
        near(0f, shot.target.x)
        assertEquals(distance * 0.01, shot.near, 1e-4)
        assertEquals(distance * 10.0, shot.far, 1e-3)
    }

    @Test
    fun framing_looks_at_the_box_centre_from_in_front() {
        val shot = CameraFraming.frame(WorldBox(Vec3(-1.5f, 2f, 0f), Vec3(0.5f, 0.5f, 0.5f)))

        assertEquals(Vec3(-1.5f, 2f, 0f), shot.target)
        near(-1.5f, shot.eye.x)
        near(2f, shot.eye.y)
        assertTrue(shot.eye.z > 0f)
    }

    @Test
    fun a_translation_moves_the_centre_and_leaves_the_extent() {
        // Column-major, as TransformManager.getWorldTransform returns it.
        val translate = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 3f, 4f, 5f, 1f)

        val moved = WorldBox.transformed(unitCube, translate)

        assertEquals(Vec3(3f, 4f, 5f), moved.center)
        assertEquals(Vec3(0.5f, 0.5f, 0.5f), moved.halfExtent)
    }

    @Test
    fun a_quarter_turn_about_z_swaps_the_x_and_y_extents() {
        val box = WorldBox(Vec3(0f, 0f, 0f), Vec3(2f, 1f, 0.5f))
        // 90° about z: x' = -y, y' = x.
        val turn = floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        val turned = WorldBox.transformed(box, turn)

        near(1f, turned.halfExtent.x)
        near(2f, turned.halfExtent.y)
        near(0.5f, turned.halfExtent.z)
    }

    @Test
    fun the_union_of_two_boxes_contains_both() {
        val left = WorldBox(Vec3(-1.5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))
        val right = WorldBox(Vec3(1.5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))

        val both = left.union(right)

        assertEquals(Vec3(-2f, -0.5f, -0.5f), both.lower)
        assertEquals(Vec3(2f, 0.5f, 0.5f), both.upper)
    }

    @Test
    fun a_zero_duration_flight_lands_at_once() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))
        val flight = CameraFlight(from, to, durationNanos = 0L)

        assertEquals(to, flight.at(123L))
        assertTrue(flight.finished)
    }

    @Test
    fun a_headless_frame_time_of_zero_lands_at_once_because_it_carries_no_clock() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))

        assertEquals(to, CameraFlight(from, to, durationNanos = 600_000_000L).at(0L))
    }

    @Test
    fun the_clock_starts_at_the_first_frame_and_eases_through_the_midpoint() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(4f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))
        val flight = CameraFlight(from, to, durationNanos = 1_000L)

        assertEquals(from.target, flight.at(10_000L).target)
        // Smoothstep is exactly half way at half time.
        near(2f, flight.at(10_500L).target.x)
        assertFalse(flight.finished)
        assertEquals(to, flight.at(11_000L))
        assertTrue(flight.finished)
    }
}

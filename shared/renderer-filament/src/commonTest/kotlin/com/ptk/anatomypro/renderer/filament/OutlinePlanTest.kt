package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.OutlineStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlinePlanTest {

    private val rib = StructureId("rib")
    private val sternum = StructureId("sternum")
    private val clavicle = StructureId("clavicle")

    private fun style(
        argb: Long = 0xFFFF00FF,
        widthDp: Float = 2f,
        fill: Long = 0xFF000000,
        shift: Float = 0f,
        outline: OutlineStyle = OutlineStyle.SOLID,
    ) = HighlightStyle(argb.toInt(), widthDp, outline, fill.toInt(), shift)

    @Test
    fun nothing_highlighted_is_no_groups() {
        assertTrue(OutlinePlan.of(emptyMap(), pixelsPerDp = 2f).isEmpty())
    }

    @Test
    fun structures_with_the_same_outline_share_a_group() {
        val groups = OutlinePlan.of(mapOf(rib to style(), sternum to style()), pixelsPerDp = 1f)

        assertEquals(listOf(setOf(rib, sternum)), groups.map { it.structures })
    }

    @Test
    fun a_different_fill_or_dash_does_not_split_a_group() {
        val groups = OutlinePlan.of(
            mapOf(
                rib to style(fill = 0xFF112233, shift = 0.25f),
                sternum to style(fill = 0xFF445566, shift = -0.2f, outline = OutlineStyle.DASHED),
            ),
            pixelsPerDp = 1f,
        )

        assertEquals(1, groups.size)
    }

    @Test
    fun a_different_colour_or_width_is_a_group_of_its_own_in_the_order_first_seen() {
        val groups = OutlinePlan.of(
            mapOf(
                rib to style(argb = 0xFFFF00FF, widthDp = 2f),
                sternum to style(argb = 0xFF00FFFF, widthDp = 2f),
                clavicle to style(argb = 0xFFFF00FF, widthDp = 3f),
            ),
            pixelsPerDp = 1f,
        )

        assertEquals(listOf(setOf(rib), setOf(sternum), setOf(clavicle)), groups.map { it.structures })
    }

    @Test
    fun the_colour_is_the_styles_bytes_as_fractions_unconverted() {
        val group = OutlinePlan.of(mapOf(rib to style(argb = 0x80FF0033)), pixelsPerDp = 1f).single()

        assertEquals(1f, group.red)
        assertEquals(0f, group.green)
        assertEquals(0x33 / 255f, group.blue)
        assertEquals(0x80 / 255f, group.alpha)
    }

    @Test
    fun the_width_is_dp_times_the_density() {
        val group = OutlinePlan.of(mapOf(rib to style(widthDp = 2f)), pixelsPerDp = 2.625f).single()

        assertEquals(5.25f, group.widthPx)
    }

    @Test
    fun a_density_that_is_not_a_positive_number_counts_as_one() {
        for (density in listOf(0f, -3f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val group = OutlinePlan.of(mapOf(rib to style(widthDp = 4f)), pixelsPerDp = density).single()

            assertEquals(4f, group.widthPx, "density $density")
        }
    }

    @Test
    fun the_width_stays_between_one_and_thirty_two_pixels() {
        val thin = OutlinePlan.of(mapOf(rib to style(widthDp = 0.1f)), pixelsPerDp = 1f).single()
        val huge = OutlinePlan.of(mapOf(rib to style(widthDp = 500f)), pixelsPerDp = 3f).single()

        assertEquals(OutlinePlan.MIN_WIDTH_PX, thin.widthPx)
        assertEquals(OutlinePlan.MAX_WIDTH_PX, huge.widthPx)
    }
}

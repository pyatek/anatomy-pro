package com.ptk.anatomypro.core.designsystem

import com.ptk.anatomypro.core.model.Laterality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SideLabelTest {

    @Test
    fun a_median_structure_has_no_side_to_name() {
        assertNull(Laterality.MEDIAN.sideResource())
    }

    @Test
    fun left_and_right_are_named_and_named_differently() {
        val left = assertNotNull(Laterality.LEFT.sideResource())
        val right = assertNotNull(Laterality.RIGHT.sideResource())

        assertNotEquals(left, right)
    }

    @Test
    fun a_name_is_spoken_with_its_side_after_it() {
        // A left and a right first rib were read out with the same words.
        assertEquals("Costa prima, left", withSide("Costa prima", "left"))
        assertEquals("Costa prima, strona prawa", withSide("Costa prima", "strona prawa"))
    }

    @Test
    fun a_name_with_no_side_is_spoken_as_it_is() {
        assertEquals("Sternum", withSide("Sternum", null))
    }
}

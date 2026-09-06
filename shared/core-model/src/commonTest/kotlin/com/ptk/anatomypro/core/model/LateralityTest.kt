package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LateralityTest {

    @Test
    fun left_and_right_are_opposites() {
        assertEquals(Laterality.RIGHT, Laterality.LEFT.opposite())
        assertEquals(Laterality.LEFT, Laterality.RIGHT.opposite())
    }

    @Test
    fun median_has_no_opposite() {
        assertNull(Laterality.MEDIAN.opposite())
    }
}

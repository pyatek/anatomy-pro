package com.ptk.anatomypro.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PacksTest {

    @Test
    fun a_download_reports_a_fraction_screen_03_can_draw() {
        assertEquals(0.25f, PackStatus.Downloading(bytesDone = 25, bytesTotal = 100).fraction)
    }

    @Test
    fun a_download_that_has_not_learned_its_total_reports_zero_rather_than_dividing_by_it() {
        assertEquals(0f, PackStatus.Downloading(bytesDone = 0, bytesTotal = 0).fraction)
    }
}

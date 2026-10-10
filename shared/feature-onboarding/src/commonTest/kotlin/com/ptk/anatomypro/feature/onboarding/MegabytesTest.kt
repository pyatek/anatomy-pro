package com.ptk.anatomypro.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class MegabytesTest {

    @Test
    fun a_size_is_shown_to_one_decimal_place() {
        assertEquals("48.3", megabytes(48_300_000, "."))
    }

    @Test
    fun the_separator_is_the_callers_because_languages_differ() {
        assertEquals("48,3", megabytes(48_300_000, ","))
    }

    @Test
    fun a_whole_number_keeps_its_zero_so_the_figure_does_not_change_width_as_it_grows() {
        assertEquals("12.0", megabytes(12_000_000, "."))
    }

    @Test
    fun less_than_a_tenth_rounds_down_to_zero_rather_than_up_to_progress_that_has_not_happened() {
        assertEquals("0.0", megabytes(99_999, "."))
    }

    @Test
    fun a_negative_count_is_shown_as_nothing_transferred() {
        assertEquals("0.0", megabytes(-5, "."))
    }
}

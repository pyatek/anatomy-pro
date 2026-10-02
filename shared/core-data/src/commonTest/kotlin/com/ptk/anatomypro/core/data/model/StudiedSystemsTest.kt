package com.ptk.anatomypro.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StudiedSystemsTest {

    @Test
    fun a_set_of_systems_round_trips_through_the_preference_row() {
        assertEquals(setOf("skeletal", "muscular"), decodeStudiedSystems(encodeStudiedSystems(setOf("skeletal", "muscular"))))
    }

    @Test
    fun an_empty_set_round_trips_to_an_empty_set_not_a_set_holding_one_empty_string() {
        assertEquals(emptySet(), decodeStudiedSystems(encodeStudiedSystems(emptySet())))
    }

    @Test
    fun the_encoding_is_stable_so_an_unchanged_setting_writes_no_row() {
        assertEquals(encodeStudiedSystems(setOf("muscular", "skeletal")), encodeStudiedSystems(setOf("skeletal", "muscular")))
    }

    @Test
    fun the_default_is_empty_because_a_student_has_not_chosen_yet() {
        assertEquals(emptySet(), AppSettings().studiedSystems)
    }
}

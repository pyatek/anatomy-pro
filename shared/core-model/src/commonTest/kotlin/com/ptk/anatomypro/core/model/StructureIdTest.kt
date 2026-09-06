package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StructureIdTest {

    @Test
    fun accepts_a_lowercase_kebab_slug() {
        assertEquals("scapula-left", StructureId("scapula-left").value)
    }

    @Test
    fun rejects_blank() {
        assertFailsWith<IllegalArgumentException> { StructureId("") }
    }

    @Test
    fun rejects_uppercase_because_ids_are_stable_slugs_and_case_drift_would_split_them() {
        assertFailsWith<IllegalArgumentException> { StructureId("Scapula") }
    }

    @Test
    fun rejects_spaces_and_underscores() {
        assertFailsWith<IllegalArgumentException> { StructureId("scapula left") }
        assertFailsWith<IllegalArgumentException> { StructureId("scapula_left") }
    }
}

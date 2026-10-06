package com.ptk.anatomypro.feature.atlas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefinitionCreditTest {

    @Test
    fun a_wikipedia_address_is_credited_to_wikipedia() {
        assertEquals("Wikipedia", definitionSourceName("https://en.wikipedia.org/wiki/Femur", "CC BY-SA 3.0"))
    }

    @Test
    fun licensed_text_with_no_address_is_the_atlas_own() {
        // The pipeline gives the atlas's licence to text that came with no link.
        assertEquals("Z-Anatomy", definitionSourceName(null, "CC BY-SA 4.0"))
    }

    @Test
    fun any_other_address_is_credited_by_its_host() {
        assertEquals("example.org", definitionSourceName("https://example.org/a/b", null))
    }

    @Test
    fun text_from_a_pack_that_recorded_nothing_has_no_credit_to_give() {
        assertNull(definitionSourceName(null, null))
    }
}

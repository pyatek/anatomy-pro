package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixtureTest {

    @Test
    fun every_structure_is_named_in_all_three_languages() {
        val missing = AtlasFixture.all.filter { structure ->
            setOf("la", "pl", "en").any { it !in structure.names }
        }

        assertEquals(emptyList(), missing, "structures missing a name: ${missing.map { it.id.value }}")
    }

    @Test
    fun the_ribs_are_a_real_sibling_set_so_the_hard_tier_has_distractors() {
        assertEquals(12, AtlasFixture.childrenOf(StructureId("costae")).size)
    }

    @Test
    fun the_cervical_vertebrae_are_a_second_sibling_set() {
        assertEquals(7, AtlasFixture.childrenOf(StructureId("vertebrae-cervicales")).size)
    }

    @Test
    fun at_least_one_structure_is_unverified_so_the_quiz_gate_is_exercised() {
        val unverified = AtlasFixture.all.filter {
            it.verification["la"] != VerificationState.VERIFIED
        }

        assertTrue(unverified.isNotEmpty(), "the fixture must contain an unverified structure")
    }

    @Test
    fun a_detail_carries_its_ancestors_root_first() {
        val detail = AtlasFixture.detail(StructureId("costa-vii"), "pl")

        assertEquals(listOf("skeletal", "costae"), detail?.ancestors?.map { it.id.value })
    }
}

package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The mesh naming convention is the only thing tying geometry to structures before
 * `core-data` exists, so Phase 0 can identify a picked node with no content database at
 * all (model sourcing spec §2.2, design spec §20.2).
 */
class StructureNodeTest {

    @Test
    fun parses_a_lateral_node_into_a_structure_and_a_side() {
        val node = StructureNode.parse("A02_4_01_001__scapula__L")

        assertEquals(StructureId("a02-4-01-001-scapula-left"), node?.structure)
        assertEquals(Laterality.LEFT, node?.laterality)
        assertEquals("A02_4_01_001", node?.taCode)
        assertEquals("scapula", node?.latinSlug)
    }

    @Test
    fun distinguishes_the_two_sides_of_one_structure() {
        val left = StructureNode.parse("A02_4_01_001__scapula__L")
        val right = StructureNode.parse("A02_4_01_001__scapula__R")

        assertEquals(Laterality.RIGHT, right?.laterality)
        assertEquals(left?.taCode, right?.taCode)
        assertEquals(false, left?.structure == right?.structure)
    }

    @Test
    fun parses_a_median_node_with_a_multi_word_latin_name() {
        val node = StructureNode.parse("A02_2_00_000__columna_vertebralis__M")

        assertEquals(StructureId("a02-2-00-000-columna-vertebralis-median"), node?.structure)
        assertEquals(Laterality.MEDIAN, node?.laterality)
    }

    @Test
    fun maps_several_nodes_of_one_structure_onto_the_same_id() {
        // Z-Anatomy models some structures as more than one object, and glTF node names
        // must be unique. A trailing discriminator keeps the names distinct without
        // splitting the structure — MeshRef is a list per structure for exactly this.
        val first = StructureNode.parse("1837__acetabular_fossa__M__s")
        val second = StructureNode.parse("1837__acetabular_fossa__M__i")

        assertEquals(StructureId("1837-acetabular-fossa-median"), first?.structure)
        assertEquals(first?.structure, second?.structure)
        assertEquals("s", first?.discriminator)
        assertEquals("i", second?.discriminator)
    }

    @Test
    fun a_node_without_a_discriminator_has_none() {
        assertNull(StructureNode.parse("4205__aorta_abdominalis__M")?.discriminator)
    }

    @Test
    fun parses_a_structure_that_terminologia_anatomica_does_not_carry() {
        // Z-Anatomy marks these with parentheses; the pipeline gives them a ZAN code so
        // they stay distinguishable from TA2-backed identifiers forever.
        val node = StructureNode.parse("ZAN__adductor_minimus__L")

        assertEquals(StructureId("zan-adductor-minimus-left"), node?.structure)
        assertEquals("ZAN", node?.taCode)
    }

    @Test
    fun rejects_a_name_that_does_not_follow_the_convention() {
        // A malformed export must surface as an unidentifiable node rather than a crash,
        // so a naming mistake in the pipeline shows up as picks that resolve to nothing.
        assertNull(StructureNode.parse("Cube.001"))
        assertNull(StructureNode.parse("A02_4_01_001__scapula"))
        assertNull(StructureNode.parse("A02_4_01_001__scapula__X"))
        assertNull(StructureNode.parse("A02_4_01_001__scapula__L__"))
        assertNull(StructureNode.parse(""))
    }
}

package com.ptk.anatomypro.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val MANIFEST = """
{
  "pack_id": "skeletal-trunk",
  "structures": [
    {
      "structure_id": "1168-clavicula-left",
      "ta2_id": "1168",
      "english": "Clavicle",
      "latin": "Clavicula",
      "definition": "The collarbone.",
      "system": "skeletal-system",
      "region": "trunk",
      "parent_id": "361-cingulum-pectorale-median",
      "laterality": "L",
      "nodes": ["1168__clavicula__L"],
      "triangles": 4200
    },
    {
      "structure_id": "361-cingulum-pectorale-median",
      "ta2_id": "361",
      "english": "Pectoral girdle",
      "latin": "Cingulum pectorale",
      "definition": null,
      "system": "skeletal-system",
      "region": "trunk",
      "parent_id": null,
      "laterality": "M",
      "nodes": ["361__cingulum_pectorale__M__a", "361__cingulum_pectorale__M__b"],
      "triangles": 900
    }
  ]
}
"""

class PackIngestTest {

    private val rows = PackIngest.parse(MANIFEST, version = 3, meshUri = "file:///p/mesh.glb")

    @Test
    fun reads_identity_and_hierarchy() {
        val clavicle = rows.structures.single { it.id == "1168-clavicula-left" }
        assertEquals("1168", clavicle.taCode)
        assertEquals("skeletal-system", clavicle.systemId)
        assertEquals("trunk", clavicle.regionId)
        assertEquals("L", clavicle.laterality)
        assertEquals("361-cingulum-pectorale-median", clavicle.parentId)
        assertEquals("skeletal-trunk", clavicle.packId)
    }

    @Test
    fun writes_one_text_row_per_locale() {
        // Latin is the canonical key and English a display locale (spec §13). Polish has
        // no source yet; the schema holds it, nothing fills it.
        val locales = rows.text.filter { it.structureId == "1168-clavicula-left" }.map { it.locale }
        assertEquals(listOf("en", "la"), locales.sorted())
        assertEquals("Clavicula", rows.text.single { it.locale == "la" && it.structureId == "1168-clavicula-left" }.name)
    }

    @Test
    fun keeps_every_node_of_a_structure_as_a_separate_mesh_ref() {
        val refs = rows.meshRefs.filter { it.structureId == "361-cingulum-pectorale-median" }
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.packId == "skeletal-trunk" })
    }

    @Test
    fun hashes_the_text_so_a_later_edit_is_detectable() {
        val original = rows.text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }
        val edited = PackIngest.parse(
            MANIFEST.replace("The collarbone.", "The clavicle."),
            version = 3,
            meshUri = null,
        ).text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }

        assertTrue(original.contentHash != edited.contentHash, "hash ignored a definition change")
    }

    @Test
    fun indexes_names_for_search_case_and_accent_folded() {
        val terms = rows.search.filter { it.structureId == "1168-clavicula-left" }.map { it.normalised }
        assertTrue("clavicula" in terms, "expected folded latin name, got $terms")
        assertTrue("clavicle" in terms, "expected folded english name, got $terms")
    }

    @Test
    fun records_the_pack_itself() {
        assertEquals("skeletal-trunk", rows.pack.id)
        assertEquals(3L, rows.pack.version)
        assertEquals("file:///p/mesh.glb", rows.pack.meshUri)
    }

    @Test
    fun a_structure_without_a_definition_still_gets_a_name_row() {
        val text = rows.text.single { it.structureId == "361-cingulum-pectorale-median" && it.locale == "la" }
        assertEquals("Cingulum pectorale", text.name)
        assertNull(text.definition)
    }
}

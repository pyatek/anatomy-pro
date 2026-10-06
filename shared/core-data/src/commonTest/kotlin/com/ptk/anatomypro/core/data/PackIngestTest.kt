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
      "definition_source": "https://en.wikipedia.org/wiki/Clavicle",
      "definition_licence": "CC BY-SA 3.0",
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
    },
    {
      "structure_id": "1105-costae-median",
      "ta2_id": "1105",
      "english": "Ribs",
      "latin": "Costae",
      "definition": null,
      "system": "skeletal-system",
      "region": "trunk",
      "parent_id": null,
      "laterality": "M",
      "is_group": true,
      "nodes": [],
      "triangles": 0
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
    fun a_name_edit_changes_the_hash_a_reviewer_approved() {
        val original = rows.text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }
        val edited = PackIngest.parse(
            MANIFEST.replace("\"Clavicle\"", "\"Collar bone\""),
            version = 3,
            meshUri = null,
        ).text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }

        assertTrue(original.contentHash != edited.contentHash, "hash ignored a name change")
    }

    @Test
    fun a_definition_edit_leaves_the_hash_alone() {
        // A reviewer approves that this mesh is this name. The definition is an encyclopedia
        // extract hundreds of words long; folding it into the hash made verifying a name mean
        // approving an article, and let any edit to the article un-verify the name.
        val original = rows.text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }
        val edited = PackIngest.parse(
            MANIFEST.replace("The collarbone.", "The clavicle."),
            version = 3,
            meshUri = null,
        ).text.single { it.structureId == "1168-clavicula-left" && it.locale == "en" }

        assertEquals(original.contentHash, edited.contentHash)
    }

    @Test
    fun indexes_names_for_search_case_and_accent_folded() {
        val terms = rows.search.filter { it.structureId == "1168-clavicula-left" }.map { it.normalised }
        assertTrue("clavicula" in terms, "expected folded latin name, got $terms")
        assertTrue("clavicle" in terms, "expected folded english name, got $terms")
    }

    @Test
    fun marks_a_grouping_collection_as_a_group() {
        // Groups are navigable and readable but draw nothing, so they must be
        // distinguishable from a structure whose pack merely is not installed.
        val ribs = rows.structures.single { it.id == "1105-costae-median" }
        assertTrue(ribs.isGroup)
        assertTrue(rows.meshRefs.none { it.structureId == "1105-costae-median" })
        assertTrue(rows.structures.single { it.id == "1168-clavicula-left" }.isGroup.not())
    }

    @Test
    fun a_group_is_still_searchable_and_readable() {
        // The encyclopedia entry for "the ribs" is exactly this row.
        assertTrue(rows.search.any { it.structureId == "1105-costae-median" && it.normalised == "costae" })
        assertEquals("Ribs", rows.text.single { it.structureId == "1105-costae-median" && it.locale == "en" }.name)
    }

    @Test
    fun records_the_pack_itself() {
        assertEquals("skeletal-trunk", rows.pack.id)
        assertEquals(3L, rows.pack.version)
        assertEquals("file:///p/mesh.glb", rows.pack.meshUri)
    }

    @Test
    fun stores_a_definition_once_in_the_language_it_is_written_in() {
        // The source's definitions are English. Copying one onto the Latin row as well
        // stored it twice and presented English text as Latin.
        val definition = rows.definitions.single { it.structureId == "1168-clavicula-left" }
        assertEquals("en", definition.locale)
        assertEquals("The collarbone.", definition.text)
    }

    @Test
    fun keeps_where_a_definition_came_from() {
        // Share-alike text has to be attributed where it is shown (spec section 29.3).
        val definition = rows.definitions.single { it.structureId == "1168-clavicula-left" }
        assertEquals("https://en.wikipedia.org/wiki/Clavicle", definition.sourceUrl)
        assertEquals("CC BY-SA 3.0", definition.licence)
    }

    @Test
    fun a_structure_without_a_definition_gets_a_name_row_and_no_definition() {
        val text = rows.text.single { it.structureId == "361-cingulum-pectorale-median" && it.locale == "la" }
        assertEquals("Cingulum pectorale", text.name)
        assertTrue(rows.definitions.none { it.structureId == "361-cingulum-pectorale-median" })
    }

    @Test
    fun a_manifest_written_before_sources_were_recorded_still_reads() {
        // Packs generated before the pipeline emitted a source carry the text alone.
        val old = MANIFEST.lines()
            .filterNot { "definition_source" in it || "definition_licence" in it }
            .joinToString("\n")
        val definition = PackIngest.parse(old, version = 3, meshUri = null).definitions.single()
        assertEquals("The collarbone.", definition.text)
        assertNull(definition.sourceUrl)
        assertNull(definition.licence)
    }
}

package com.ptk.anatomypro.core.data

import com.ptk.anatomypro.core.data.entity.StructureVerificationEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises Room against real SQLite, not the ingest mapping in isolation.
 *
 * Every query here has been type-checked by KSP and never executed. Twice in this project
 * a tested unit sat behind a path nothing ran, so the queries are run against a real file
 * with real rows before anything is built on them.
 */
class DatabaseTest {

    private val database = openTemporaryDatabase()
    private val dao = database.structures()

    @AfterTest
    fun close() = database.close()

    private suspend fun install() =
        PackInstaller(database).install(SAMPLE_MANIFEST, version = 1, meshUri = "file:///m.glb")

    /**
     * A manifest is a megabyte of JSON for a thousand structures. Parsed where `install` was
     * called, which is the main thread at launch, it held the first frame back by up to a
     * second and a half (design spec §40).
     */
    @Test
    fun parses_the_manifest_on_its_own_context_not_the_caller_s() = runTest {
        val parsing = CountingDispatcher()

        PackInstaller(database, parseContext = parsing).install(SAMPLE_MANIFEST, version = 1, meshUri = null)

        assertTrue(parsing.dispatched > 0, "the manifest was parsed without leaving the caller's context")
        assertNotNull(dao.structure("1168-clavicula-left"))
    }

    @Test
    fun installs_a_pack_and_reads_a_structure_back() = runTest {
        install()

        val clavicle = assertNotNull(dao.structure("1168-clavicula-left"))
        assertEquals("1168", clavicle.taCode)
        assertEquals("trunk", clavicle.regionId)
        assertEquals("Clavicula", assertNotNull(dao.text("1168-clavicula-left", "la")).name)
        assertEquals(1, dao.meshRefs("1168-clavicula-left").size)
        assertEquals("file:///m.glb", assertNotNull(dao.pack("skeletal-trunk")).meshUri)
    }

    @Test
    fun walks_the_taxonomy_from_a_leaf_to_its_group() = runTest {
        install()

        val clavicle = assertNotNull(dao.structure("1168-clavicula-left"))
        val parent = assertNotNull(dao.structure(assertNotNull(clavicle.parentId)))
        assertTrue(parent.isGroup)
        assertEquals("Cingulum pectorale", assertNotNull(dao.text(parent.id, "la")).name)
        assertEquals(
            listOf("1168-clavicula-left", "1169-scapula-left"),
            dao.children(parent.id).map { it.id },
        )
    }

    @Test
    fun finds_the_siblings_a_hard_quiz_question_draws_from() = runTest {
        install()

        // §8.1's hard tier: wrong answers from under the same parent.
        val siblings = dao.siblings("361-cingulum-pectorale-median", exclude = "1168-clavicula-left", limit = 10)
        assertEquals(listOf("1169-scapula-left"), siblings.map { it.id })
    }

    @Test
    fun searches_every_language_at_once_and_reports_which_matched() = runTest {
        install()

        // "clavic" is a prefix of both Clavicula and Clavicle, so the same structure is
        // found twice — once per language — and each hit says which one it was.
        val hits = dao.searchHits(normalisedQuery = "clavic", limit = 10)
        assertEquals(setOf("la", "en"), hits.map { it.locale }.toSet())
        assertEquals(setOf("1168-clavicula-left"), hits.map { it.structureId }.toSet())

        // A term that exists in only one language is found in only that one.
        assertEquals(listOf("en"), dao.searchHits("pectoral", 10).map { it.locale })
    }

    @Test
    fun a_prefix_matching_nothing_finds_nothing() = runTest {
        install()
        assertEquals(emptyList(), dao.searchHits("zzz", 10))
    }

    @Test
    fun a_verification_stops_counting_when_the_text_it_approved_changes() = runTest {
        install()
        val approved = assertNotNull(dao.text("1168-clavicula-left", "en"))
        dao.upsertVerification(
            StructureVerificationEntity(
                structureId = "1168-clavicula-left",
                locale = "en",
                state = "VERIFIED",
                verifiedAt = 1_000L,
                verifiedTextHash = approved.contentHash,
            )
        )
        assertNotNull(dao.currentVerification("1168-clavicula-left", "en"))

        // The pack is updated and the name corrected.
        PackInstaller(database).install(
            SAMPLE_MANIFEST.replace("\"Clavicle\"", "\"Collar bone\""),
            version = 2,
            meshUri = "file:///m.glb",
        )

        assertNull(
            dao.currentVerification("1168-clavicula-left", "en"),
            "a stale approval still authorised a quiz answer",
        )
        assertNotNull(
            dao.anyVerification("1168-clavicula-left", "en"),
            "the reviewer's work was destroyed rather than marked stale",
        )
    }

    @Test
    fun a_verification_survives_an_edit_to_the_definition() = runTest {
        install()
        val approved = assertNotNull(dao.text("1168-clavicula-left", "en"))
        dao.upsertVerification(
            StructureVerificationEntity(
                structureId = "1168-clavicula-left",
                locale = "en",
                state = "VERIFIED",
                verifiedAt = 1_000L,
                verifiedTextHash = approved.contentHash,
            )
        )

        PackInstaller(database).install(
            SAMPLE_MANIFEST.replace("The collarbone.", "A long bone of the shoulder girdle."),
            version = 2,
            meshUri = "file:///m.glb",
        )

        assertEquals(
            "A long bone of the shoulder girdle.",
            assertNotNull(dao.definition("1168-clavicula-left", "en")).text,
        )
        assertNotNull(
            dao.currentVerification("1168-clavicula-left", "en"),
            "editing an encyclopedia extract un-verified a name",
        )
    }

    @Test
    fun a_definition_dropped_by_a_pack_update_is_gone() = runTest {
        install()
        assertNotNull(dao.definition("1168-clavicula-left", "en"))

        PackInstaller(database).install(
            SAMPLE_MANIFEST.replace("\"The collarbone.\"", "null"),
            version = 2,
            meshUri = "file:///m.glb",
        )

        assertNull(dao.definition("1168-clavicula-left", "en"))
    }

    @Test
    fun the_detail_screen_gets_the_definition_with_its_source() = runTest {
        install()
        val repository = com.ptk.anatomypro.core.data.repository.RoomAtlasRepository(database)

        // Asked for in Latin, which has no definition of its own: the English one is
        // returned and says that it is English.
        val detail = assertNotNull(
            repository.detail(com.ptk.anatomypro.core.model.StructureId("1168-clavicula-left"), "la")
        )
        assertEquals("The collarbone.", detail.definition)
        assertEquals("en", detail.definitionLocale)
        assertEquals("https://en.wikipedia.org/wiki/Clavicle", detail.definitionSource)
        assertEquals("CC BY-SA 3.0", detail.definitionLicence)
    }

    @Test
    fun reinstalling_a_pack_does_not_duplicate_rows() = runTest {
        install()
        install()
        assertEquals(3, dao.structuresInPack("skeletal-trunk").size)
        assertEquals(1, dao.meshRefs("1168-clavicula-left").size)
    }

    @Test
    fun lists_the_systems_and_what_belongs_to_each() = runTest {
        PackInstaller(database).install(TWO_SYSTEMS, version = 1, meshUri = "file:///m.glb")
        val repository = com.ptk.anatomypro.core.data.repository.RoomAtlasRepository(database)

        assertEquals(
            listOf("muscular-system", "skeletal-system"),
            repository.systems().map { it.value },
        )
        assertEquals(
            setOf("1168-clavicula-left"),
            repository.structuresIn(com.ptk.anatomypro.core.model.SystemId("skeletal-system")).map { it.value }.toSet(),
        )
        assertEquals(
            setOf("1168-clavicula-left", "2001-musculus-subclavius-left"),
            repository.allStructures().map { it.value }.toSet(),
        )
    }
}

private const val TWO_SYSTEMS = """
{
  "pack_id": "two-systems",
  "structures": [
    {
      "structure_id": "1168-clavicula-left", "ta2_id": "1168",
      "english": "Clavicle", "latin": "Clavicula", "definition": null,
      "system": "skeletal-system", "region": "trunk",
      "parent_id": null, "laterality": "L",
      "is_group": false, "nodes": ["1168__clavicula__L"], "triangles": 900
    },
    {
      "structure_id": "2001-musculus-subclavius-left", "ta2_id": "2001",
      "english": "Subclavius", "latin": "Musculus subclavius", "definition": null,
      "system": "muscular-system", "region": "trunk",
      "parent_id": null, "laterality": "L",
      "is_group": false, "nodes": ["2001__musculus_subclavius__L"], "triangles": 600
    }
  ]
}
"""

/** Runs on the default dispatcher, and counts what it was handed. */
private class CountingDispatcher : CoroutineDispatcher() {
    var dispatched = 0
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatched++
        Dispatchers.Default.dispatch(context, block)
    }
}

package com.ptk.anatomypro.backend

import com.ptk.anatomypro.backend.content.Content
import com.ptk.anatomypro.core.data.quiz.SAMPLE_QUIZ_NODES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Backend spec §10: the server reads what the app reads, by the same rules. */
class ContentTest {

    private val content = Content(TestDatabase.fresh())

    private fun verified(locale: String = "la") =
        content.nodes(SampleContent.PACK, locale).filter { it.verified }.map { it.id.value }.toSet()

    @Test
    fun an_ingested_pack_is_the_graph_the_generator_was_given_with_nothing_yet_reviewed() {
        content.ingest(SampleContent.manifest())

        val nodes = content.nodes(SampleContent.PACK, "la").associateBy { it.id }
        assertEquals(SAMPLE_QUIZ_NODES.map { it.id }.toSet(), nodes.keys)
        for (expected in SAMPLE_QUIZ_NODES) {
            assertEquals(expected.copy(verified = false), nodes.getValue(expected.id))
        }
    }

    @Test
    fun a_review_counts_for_the_name_it_was_made_of_in_the_language_it_was_made_in() {
        content.ingest(SampleContent.manifest())
        content.verify(SampleContent.verifications())

        assertEquals(SAMPLE_QUIZ_NODES.filter { it.verified }.map { it.id.value }.toSet(), verified("la"))
        // A reviewed Latin name says nothing about the English one (design §7).
        assertEquals(emptySet(), verified("en"))
    }

    @Test
    fun a_review_of_a_name_that_has_since_changed_no_longer_counts() {
        content.ingest(SampleContent.manifest())
        content.verify(SampleContent.verifications())

        val renamed = SAMPLE_QUIZ_NODES.map { if (it.id.value == "vertebra-t1") it.copy(name = "Vertebra thoracica I") else it }
        content.ingest(SampleContent.manifest(renamed))

        assertFalse("vertebra-t1" in verified())
        assertTrue("vertebra-t2" in verified(), "a re-ingest threw away reviews of names that did not change")
    }

    @Test
    fun a_reingest_replaces_the_pack_rather_than_adding_to_it() {
        content.ingest(SampleContent.manifest())

        content.ingest(SampleContent.manifest(SAMPLE_QUIZ_NODES.filterNot { it.id.value == "musculus-4" }))

        assertFalse(content.nodes(SampleContent.PACK, "la").any { it.id.value == "musculus-4" })
        assertEquals(SAMPLE_QUIZ_NODES.size - 1, content.nodes(SampleContent.PACK, "la").size)
    }

    @Test
    fun another_pack_is_left_alone_and_read_apart() {
        content.ingest(SampleContent.manifest())
        content.ingest(SampleContent.manifest(SAMPLE_QUIZ_NODES.take(1).map { it.copy(id = com.ptk.anatomypro.core.model.StructureId("elsewhere")) }, pack = "other"))

        assertEquals(SAMPLE_QUIZ_NODES.size, content.nodes(SampleContent.PACK, "la").size)
        assertEquals(1, content.nodes("other", "la").size)
    }

    @Test
    fun a_review_that_is_not_an_approval_does_not_count() {
        content.ingest(SampleContent.manifest())

        content.verify(SampleContent.verifications().replace("\"VERIFIED\"", "\"DISPUTED\""))

        assertEquals(emptySet(), verified())
    }

    @Test
    fun a_later_review_of_the_same_name_replaces_the_earlier_one() {
        content.ingest(SampleContent.manifest())
        content.verify(SampleContent.verifications())

        content.verify(SampleContent.verifications().replace("\"VERIFIED\"", "\"DISPUTED\""))

        assertEquals(emptySet(), verified())
    }

    @Test
    fun a_review_may_arrive_before_the_pack_it_is_about() {
        content.verify(SampleContent.verifications())
        content.ingest(SampleContent.manifest())

        assertTrue("vertebra-t1" in verified())
    }

    @Test
    fun a_review_file_that_cannot_be_read_changes_nothing() {
        content.ingest(SampleContent.manifest())
        content.verify(SampleContent.verifications())

        assertFailsWith<Exception> { content.verify("""{"verifications":[{"structureId":"vertebra-t1"}]}""") }

        assertTrue("vertebra-t1" in verified())
    }
}

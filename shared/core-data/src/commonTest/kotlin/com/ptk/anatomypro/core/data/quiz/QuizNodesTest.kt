package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.entity.StructureEntity
import com.ptk.anatomypro.core.data.entity.StructureTextEntity
import com.ptk.anatomypro.core.model.Laterality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizNodesTest {

    private fun structure(id: String, parent: String? = "costae", side: String = "M", group: Boolean = false) =
        StructureEntity(id, taCode = null, parentId = parent, systemId = "skeletal-system", regionId = "trunk", laterality = side, packId = "p", isGroup = group)

    private fun text(id: String, locale: String, name: String) = StructureTextEntity(id, locale, name, contentHash = "h")

    private val structures = listOf(structure("costa-1", side = "L"), structure("costa-2", side = "R"), structure("nameless"))
    private val texts = listOf(
        text("costa-1", "la", "Costa prima"),
        text("costa-1", "pl", "Żebro pierwsze"),
        text("costa-2", "la", "Costa secunda"),
    )

    private fun nodes(locale: String, verified: Set<Pair<String, String>> = emptySet()) =
        quizNodes(structures, texts, verified, locale).associateBy { it.id.value }

    @Test
    fun a_structure_is_named_in_the_language_asked_for_and_in_latin_where_that_is_missing() {
        val polish = nodes("pl")

        assertEquals("Żebro pierwsze", polish.getValue("costa-1").name)
        assertEquals("pl", polish.getValue("costa-1").nameLocale)
        assertEquals("Costa prima", polish.getValue("costa-1").latinName)
        assertEquals("Costa secunda", polish.getValue("costa-2").name)
        assertEquals("la", polish.getValue("costa-2").nameLocale)
    }

    @Test
    fun a_structure_with_no_name_at_all_cannot_be_asked_about() {
        assertFalse("nameless" in nodes("pl"))
    }

    @Test
    fun verified_means_the_name_that_is_shown() {
        // §7. A reviewed Latin name says nothing about the Polish one a student would read.
        val latinOnly = nodes("pl", verified = setOf("costa-1" to "la", "costa-2" to "la"))

        assertFalse(latinOnly.getValue("costa-1").verified, "shown in Polish, reviewed only in Latin")
        assertTrue(latinOnly.getValue("costa-2").verified, "shown in Latin, reviewed in Latin")
        assertTrue(nodes("pl", verified = setOf("costa-1" to "pl")).getValue("costa-1").verified)
    }

    @Test
    fun the_side_and_the_place_in_the_graph_are_carried_over() {
        val node = nodes("la").getValue("costa-1")

        assertEquals(Laterality.LEFT, node.laterality)
        assertEquals(Laterality.RIGHT, nodes("la").getValue("costa-2").laterality)
        assertEquals("costae", node.parentId?.value)
        assertEquals("skeletal-system", node.systemId)
        assertEquals("trunk", node.regionId)
    }
}

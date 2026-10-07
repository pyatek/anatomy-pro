package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.model.StructureDetail
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtlasQuizSourceTest {

    // The fake atlas stands in for an installed one: same interface, known contents.
    private val source = AtlasQuizSource(FakeAtlasRepository())

    @Test
    fun every_group_with_enough_drawable_children_is_a_topic() = runTest {
        val topics = source.topics("la").map { it.id.value }.toSet()

        // Ribs (12), cervical vertebrae (7) and thoracic muscles (4) can each fill four
        // options. The two systems hold only groups, so neither is a topic.
        assertEquals(setOf("costae", "vertebrae-cervicales", "musculi-thoracis"), topics)
    }

    @Test
    fun a_topic_takes_its_system_from_what_it_contains() = runTest {
        // Real packs leave a group's own system empty; its children know.
        val topics = source.topics("la").associateBy { it.id.value }

        assertEquals(SystemId("skeletal-system"), topics.getValue("costae").system)
        assertEquals(SystemId("muscular-system"), topics.getValue("musculi-thoracis").system)
    }

    @Test
    fun a_topic_is_titled_in_the_locale_asked_for() = runTest {
        assertEquals("Żebra", source.topics("pl").first { it.id.value == "costae" }.title)
    }

    @Test
    fun only_drawable_structures_are_answers() = runTest {
        val answers = source.answerable(QuizTopicId("costae"), "la")

        assertEquals(12, answers.size)
        assertTrue(answers.none { it.isGroup })
    }

    @Test
    fun a_repository_over_this_source_asks_about_the_atlas() = runTest {
        val repository = FakeQuizRepository(source = source)

        val session = repository.startSession(
            QuizTopicId("vertebrae-cervicales"), QuizFormat.NAME_THE_HIGHLIGHTED, questionCount = 3, seed = 7, locale = "la",
        )

        val asked = session.questions.map { (it as QuizQuestion.NameTheHighlighted).highlighted }
        assertEquals(3, asked.size)
        assertTrue(asked.all { it.value.startsWith("vertebra-cervicalis-") })
    }

    @Test
    fun a_name_with_no_translation_says_it_is_latin() = runTest {
        assertEquals("pl", source.nameLocale(StructureId("costa-i"), "pl"))
        assertEquals("la", source.nameLocale(StructureId("costa-i"), "xx"))
    }

    @Test
    fun the_mirror_of_the_expected_structure_is_the_same_answer() = runTest {
        val paired = AtlasQuizSource(PairedAtlas())

        assertTrue(paired.sameAnswer(StructureId("femur-left"), StructureId("femur-right"), "la"))
        assertTrue(paired.sameAnswer(StructureId("femur-left"), StructureId("femur-left"), "la"))
    }

    @Test
    fun a_structure_with_another_name_is_not_the_same_answer() = runTest {
        val paired = AtlasQuizSource(PairedAtlas())

        assertFalse(paired.sameAnswer(StructureId("femur-left"), StructureId("tibia"), "la"))
    }

    @Test
    fun the_same_name_under_another_group_is_not_the_same_answer() = runTest {
        val paired = AtlasQuizSource(PairedAtlas())

        assertFalse(paired.sameAnswer(StructureId("femur-left"), StructureId("femur-elsewhere"), "la"))
    }

    @Test
    fun the_fixture_source_accepts_only_the_structure_itself() = runTest {
        assertTrue(FixtureQuizSource.sameAnswer(StructureId("costa-i"), StructureId("costa-i"), "la"))
        assertFalse(FixtureQuizSource.sameAnswer(StructureId("costa-i"), StructureId("costa-ii"), "la"))
    }
}

/**
 * The smallest atlas with a paired structure: "bones" holds two groups; "limb" has five
 * distinct names, one carried by a left and a right femur, and "rest" has a femur of its own.
 */
internal class PairedAtlas : AtlasRepository {

    private class Node(val id: String, val name: String, val parent: String?, val isGroup: Boolean = false)

    private val nodes = listOf(
        Node("bones", "Bones", null, isGroup = true),
        Node("limb", "Limb", "bones", isGroup = true),
        Node("rest", "Rest", "bones", isGroup = true),
        Node("femur-left", "Femur", "limb"),
        Node("femur-right", "Femur", "limb"),
        Node("tibia", "Tibia", "limb"),
        Node("fibula", "Fibula", "limb"),
        Node("patella", "Patella", "limb"),
        Node("femur-elsewhere", "Femur", "rest"),
    )

    private fun Node.summary() = StructureSummary(
        id = StructureId(id),
        name = name,
        latinName = name,
        laterality = when {
            id.endsWith("-left") -> Laterality.LEFT
            id.endsWith("-right") -> Laterality.RIGHT
            else -> Laterality.MEDIAN
        },
        isGroup = isGroup,
        hasChildren = nodes.any { it.parent == id },
    )

    private fun find(id: StructureId) = nodes.firstOrNull { it.id == id.value }

    override suspend fun roots(locale: String) = nodes.filter { it.parent == null }.map { it.summary() }

    override suspend fun children(parent: StructureId, locale: String) =
        nodes.filter { it.parent == parent.value }.map { it.summary() }

    override suspend fun summary(id: StructureId, locale: String) = find(id)?.summary()

    override suspend fun detail(id: StructureId, locale: String): StructureDetail? {
        val node = find(id) ?: return null
        val ancestors = generateSequence(node.parent) { parent -> nodes.first { it.id == parent }.parent }
            .map { parent -> nodes.first { it.id == parent }.summary() }
            .toList()
            .reversed()
        return StructureDetail(
            id = id,
            names = mapOf("la" to node.name),
            definition = null,
            definitionLocale = null,
            definitionSource = null,
            definitionLicence = null,
            systemId = "skeletal-system",
            regionId = null,
            laterality = node.summary().laterality,
            isGroup = node.isGroup,
            ancestors = ancestors,
        )
    }

    override suspend fun search(query: String, limit: Int): List<SearchHit> = emptyList()

    override suspend fun systems() = emptyList<SystemId>()

    override suspend fun structuresIn(system: SystemId) = emptySet<StructureId>()

    override suspend fun allStructures() = nodes.map { StructureId(it.id) }.toSet()
}

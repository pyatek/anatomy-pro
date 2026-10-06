package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

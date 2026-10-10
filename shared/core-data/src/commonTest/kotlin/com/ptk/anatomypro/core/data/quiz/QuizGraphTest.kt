package com.ptk.anatomypro.core.data.quiz

import com.ptk.anatomypro.core.data.model.Difficulty
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.StructureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Design spec §15: distractor tiering, laterality handling, verified-only filtering, and a
 * golden test pinning a set for a fixed seed.
 */
class QuizGraphTest {

    private val graph = QuizGraph(SAMPLE_QUIZ_NODES)
    private val ribs = QuizTopicId("costae")

    private fun node(id: String) = SAMPLE_QUIZ_NODES.first { it.id.value == id }

    private fun named(seed: Long, count: Int = 9, topic: QuizTopicId = ribs, on: QuizGraph = graph) =
        on.questions(topic, QuizFormat.NAME_THE_HIGHLIGHTED, count, seed).map { it as QuizQuestion.NameTheHighlighted }

    // --- topics -----------------------------------------------------------------------

    @Test
    fun a_topic_is_a_group_with_enough_verified_answers_to_fill_a_question() {
        val topics = graph.topics().associateBy { it.id.value }

        assertEquals(setOf("costae", "vertebrae-thoracicae", "musculi-thoracis"), topics.keys)
        // Ten verified ribs drawn, five names: a left and a right rib are one answer.
        assertEquals(5, topics.getValue("costae").structureCount)
        assertEquals("skeletal-system", topics.getValue("costae").system.value)
        assertEquals("Costae", topics.getValue("costae").title)
    }

    @Test
    fun a_group_with_too_few_verified_answers_is_not_a_topic() {
        // Three verified bones and one unverified: four drawn, three that may be asked.
        assertFalse(graph.topics().any { it.id.value == "ossa-carpi" })
        assertFailsWith<IllegalArgumentException> {
            graph.questions(QuizTopicId("ossa-carpi"), QuizFormat.NAME_THE_HIGHLIGHTED, 3, seed = 1)
        }
    }

    // --- §7: verified only ------------------------------------------------------------

    @Test
    fun an_unverified_structure_is_never_the_expected_answer() {
        for (seed in 0L until 200L) {
            for (format in QuizFormat.entries) {
                val expected = graph.questions(ribs, format, 20, seed).map {
                    when (it) {
                        is QuizQuestion.NameTheHighlighted -> it.correct.id
                        is QuizQuestion.TapTheStructure -> it.target
                    }
                }
                assertTrue(expected.all { node(it.value).verified }, "seed $seed asked about an unverified structure")
            }
        }
    }

    @Test
    fun an_unverified_structure_is_never_offered_as_an_option_either() {
        // A wrong name among the options teaches as surely as a wrong expected answer.
        for (seed in 0L until 200L) {
            val offered = named(seed).flatMap { it.options }.map { it.id.value }
            assertTrue(offered.all { node(it).verified }, "seed $seed offered an unverified structure")
        }
    }

    // --- §8.1: distractor distance ----------------------------------------------------

    @Test
    fun hard_distractors_are_siblings_medium_share_the_region_and_easy_come_from_another_system() {
        for (seed in 0L until 50L) {
            for (question in named(seed)) {
                val target = node(question.correct.id.value)
                val distractors = question.options.filter { it.id != target.id }.map { node(it.id.value) }
                assertEquals(3, distractors.size)
                when (question.difficulty) {
                    Difficulty.HARD -> assertTrue(distractors.all { it.parentId == target.parentId })
                    Difficulty.MEDIUM -> assertTrue(
                        distractors.all {
                            it.systemId == target.systemId && it.regionId == target.regionId && it.parentId != target.parentId
                        },
                    )
                    Difficulty.EASY -> assertTrue(distractors.all { it.systemId != target.systemId })
                }
            }
        }
    }

    @Test
    fun a_session_climbs_from_easy_to_hard() {
        val difficulties = named(seed = 7, count = 5).map { it.difficulty }

        assertEquals(difficulties.sorted(), difficulties)
        assertEquals(Difficulty.EASY, difficulties.first())
        assertEquals(Difficulty.HARD, difficulties.last())
        assertTrue(Difficulty.MEDIUM in difficulties)
    }

    @Test
    fun a_tier_with_nothing_to_draw_from_falls_back_to_the_nearest_one_and_says_so() {
        // An atlas of ribs alone: no other system, no other group in the region.
        val ribsOnly = QuizGraph(SAMPLE_QUIZ_NODES.filter { it.id.value == "costae" || it.parentId?.value == "costae" })

        val questions = named(seed = 3, count = 5, on = ribsOnly)

        assertEquals(5, questions.size)
        assertTrue(questions.all { it.difficulty == Difficulty.HARD })
    }

    // --- laterality -------------------------------------------------------------------

    @Test
    fun no_two_options_read_the_same_and_the_mirror_of_the_answer_is_never_a_wrong_option() {
        for (seed in 0L until 100L) {
            for (question in named(seed)) {
                val names = question.options.map { it.name }
                assertEquals(names.distinct(), names, "seed $seed offered one name twice")
            }
        }
    }

    @Test
    fun a_session_asks_about_each_name_once_whichever_side_it_draws() {
        for (seed in 0L until 100L) {
            val asked = named(seed, count = 20).map { it.correct.name }
            assertEquals(asked.distinct(), asked)
            assertEquals(5, asked.size) // capped at what the topic has
        }
    }

    @Test
    fun both_sides_are_asked_about_over_many_sessions() {
        val sides = (0L until 100L).flatMap { seed -> named(seed).map { node(it.correct.id.value).laterality } }.toSet()

        assertEquals(setOf(Laterality.LEFT, Laterality.RIGHT), sides)
    }

    @Test
    fun the_mirror_of_the_expected_structure_is_the_same_answer() {
        assertTrue(graph.sameAnswer(StructureId("costa-2-left"), StructureId("costa-2-right")))
        assertTrue(graph.sameAnswer(StructureId("costa-2-left"), StructureId("costa-2-left")))
        assertFalse(graph.sameAnswer(StructureId("costa-2-left"), StructureId("costa-3-left")))
    }

    @Test
    fun the_same_name_under_another_group_is_a_different_structure() {
        // "Caput" of a rib is not "Caput" of a carpal bone.
        assertFalse(graph.sameAnswer(StructureId("costa-1-left"), StructureId("os-carpi-homonym")))
    }

    @Test
    fun a_group_is_never_an_answer() {
        assertFalse(graph.sameAnswer(StructureId("costae"), StructureId("costae-homonym-group")))
        assertTrue(named(seed = 1).flatMap { it.options }.none { it.isGroup })
    }

    // --- §8.2: seeded and reproducible ------------------------------------------------

    @Test
    fun the_same_seed_gives_the_same_questions_and_another_seed_does_not() {
        assertEquals(named(seed = 42), named(seed = 42))
        assertNotEquals(named(seed = 42), named(seed = 43))
    }

    @Test
    fun the_order_the_atlas_lists_its_structures_in_does_not_change_a_seeded_set() {
        assertEquals(named(seed = 42), named(seed = 42, on = QuizGraph(SAMPLE_QUIZ_NODES.reversed())))
    }

    @Test
    fun a_fixed_seed_is_pinned_so_a_change_to_generation_is_a_decision() {
        // Golden. The daily set must come out the same wherever it is generated (§9.1); a
        // diff here means every earlier seed now names a different set.
        val questions = named(seed = 20261010, count = 3)

        assertEquals(GOLDEN_OPTIONS, questions.map { q -> q.options.map { it.id.value } })
        assertEquals(GOLDEN_TARGETS, questions.map { it.correct.id.value })
    }

    // --- the two formats --------------------------------------------------------------

    @Test
    fun a_tap_question_prompts_with_the_name_and_says_which_language_it_is_really_in() {
        val questions = graph.questions(ribs, QuizFormat.TAP_THE_STRUCTURE, 5, seed = 5).map { it as QuizQuestion.TapTheStructure }

        for (question in questions) {
            val target = node(question.target.value)
            assertEquals(target.name, question.prompt)
            assertEquals(target.nameLocale, question.promptLocale)
        }
    }

    @Test
    fun question_ids_are_distinct_within_a_session() {
        val ids = named(seed = -9).map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    // --- what screen 12 explains ------------------------------------------------------

    @Test
    fun the_shared_ancestor_is_the_lowest_group_both_structures_sit_under() {
        assertEquals("costae", graph.sharedAncestor(StructureId("costa-1-left"), StructureId("costa-4-right"))?.id?.value)
        assertEquals("skeleton-thoracis", graph.sharedAncestor(StructureId("costa-1-left"), StructureId("vertebra-t1"))?.id?.value)
        assertNull(graph.sharedAncestor(StructureId("costa-1-left"), StructureId("musculus-1")))
    }

    private companion object {
        val GOLDEN_TARGETS = listOf("costa-4-left", "costa-1-right", "costa-2-right")
        val GOLDEN_OPTIONS = listOf(
            listOf("musculus-1", "musculus-3", "musculus-2", "costa-4-left"),
            listOf("vertebra-t2", "costa-1-right", "vertebra-t3", "vertebra-t1"),
            listOf("costa-5-left", "costa-2-right", "costa-1-left", "costa-3-left"),
        )
    }
}

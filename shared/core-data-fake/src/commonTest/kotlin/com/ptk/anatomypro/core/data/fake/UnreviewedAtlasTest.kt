package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.quiz.GeneratedQuizRepository
import com.ptk.anatomypro.core.data.quiz.QuizGraphSource
import com.ptk.anatomypro.core.data.quiz.QuizNode
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreviewedAtlasTest {

    private fun node(id: String, parent: String?, group: Boolean) = QuizNode(
        id = StructureId(id), parentId = parent?.let(::StructureId), systemId = "skeletal-system", regionId = "trunk",
        laterality = Laterality.MEDIAN, isGroup = group, name = id, nameLocale = "la", latinName = id, verified = false,
    )

    private val unreviewed = QuizGraphSource {
        listOf(node("costae", null, group = true)) + (1..4).map { node("costa-$it", "costae", group = false) }
    }

    @Test
    fun the_real_generator_has_nothing_to_ask_about_an_atlas_nobody_has_reviewed() = runTest {
        assertTrue(GeneratedQuizRepository(unreviewed).topics("la").isEmpty())
    }

    @Test
    fun the_harness_lets_a_debug_build_ask_about_it_anyway() = runTest {
        val topics = GeneratedQuizRepository(UnreviewedAtlas(unreviewed)).topics("la")

        assertEquals(listOf("costae"), topics.map { it.id.value })
    }
}

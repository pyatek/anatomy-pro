package com.ptk.anatomypro.feature.quiz

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizQuestion
import com.ptk.anatomypro.core.model.QuizTopicId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizSceneTest {

    private val atlas = FakeAtlasRepository()
    private val quiz = FakeQuizRepository()

    private suspend fun ribs(format: QuizFormat = QuizFormat.NAME_THE_HIGHLIGHTED) =
        quiz.startSession(QuizTopicId("costae"), format, questionCount = 3, seed = 4, locale = "la")

    @Test
    fun a_session_about_bones_hides_every_other_system_so_nothing_stands_in_front_of_a_bone() = runTest {
        // On the all-systems pack the body's surface covered the rib a question asked about.
        val skeleton = atlas.structuresIn(SystemId("skeletal-system"))

        val hidden = structuresHiddenFor(atlas, ribs())

        assertEquals(atlas.allStructures() - skeleton, hidden)
        assertTrue(hidden.isNotEmpty(), "the fixture has more than one system")
    }

    @Test
    fun nothing_a_question_asks_about_is_hidden() = runTest {
        for (format in QuizFormat.entries) {
            val session = ribs(format)
            val asked = session.questions.map {
                when (it) {
                    is QuizQuestion.NameTheHighlighted -> it.correct.id
                    is QuizQuestion.TapTheStructure -> it.target
                }
            }

            val hidden = structuresHiddenFor(atlas, session)

            assertTrue(asked.none { it in hidden })
        }
    }

    @Test
    fun an_atlas_that_cannot_be_read_hides_nothing_rather_than_stopping_the_quiz() = runTest {
        val session = ribs()
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no atlas") }))

        assertEquals(emptySet(), structuresHiddenFor(broken, session))
    }

    @Test
    fun a_session_with_no_questions_hides_nothing() = runTest {
        assertEquals(emptySet(), structuresHiddenFor(atlas, ribs().copy(questions = emptyList())))
    }

    @Test
    fun a_question_s_time_runs_only_while_the_app_is_in_front_and_the_model_is_drawn() {
        // The clock used to start with the screen, and ran through the seconds the canvas
        // was still black: a first question could run out before there was anything to see.
        assertTrue(quizClockRuns(resumed = true, canvasReady = true))
        assertFalse(quizClockRuns(resumed = true, canvasReady = false))
        assertFalse(quizClockRuns(resumed = false, canvasReady = true))
    }

    @Test
    fun the_systems_asked_about_are_found_in_one_question_to_the_atlas_not_one_per_structure() = runTest {
        // A structure's whole detail was read for each question, only for its system: ten
        // rounds of queries before the first question could hide what stood in front of it.
        var details = 0
        var lookups = 0
        val counting = object : AtlasRepository by atlas {
            override suspend fun detail(id: StructureId, locale: String) = atlas.detail(id, locale).also { details++ }
            override suspend fun systemsOf(ids: Set<StructureId>) = atlas.systemsOf(ids).also { lookups++ }
        }

        val hidden = structuresHiddenFor(counting, ribs())

        assertEquals(0, details)
        assertEquals(1, lookups)
        assertEquals(atlas.allStructures() - atlas.structuresIn(SystemId("skeletal-system")), hidden)
    }
}

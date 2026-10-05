package com.ptk.anatomypro.feature.atlas.tree

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.AtlasError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StructureTreeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model() = StructureTreeViewModel(FakeAtlasRepository(), locale = "en")

    private fun StructureTreeViewModel.names() = state.value.items.map { it.name }

    @Test
    fun starts_at_the_top_level_with_nothing_focused() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(listOf("Skeletal system", "Muscular system"), model.names())
        assertEquals(1, model.state.value.level)
        assertNull(model.state.value.focused)
    }

    @Test
    fun entering_a_structure_lists_its_children_and_announces_the_level() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onEnter(1) // Muscular system
        advanceUntilIdle()
        model.onEnter(0) // Thoracic muscles
        advanceUntilIdle()

        assertEquals(4, model.state.value.items.size)
        assertEquals(3, model.state.value.level)
        assertEquals(listOf("Muscular system", "Thoracic muscles"), model.state.value.path.map { it.name })
        assertEquals(TreeAnnouncement.Level(level = 3, name = "Thoracic muscles", count = 4), model.state.value.announcement)
    }

    @Test
    fun focusing_announces_the_name_and_the_position() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()
        model.onEnter(0)
        advanceUntilIdle()

        model.onFocus(0)

        assertEquals("Pectoralis major", model.state.value.focused?.name)
        assertEquals(TreeAnnouncement.Focused(name = "Pectoralis major", position = 1, total = 4), model.state.value.announcement)
    }

    @Test
    fun a_leaf_cannot_be_entered() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()
        model.onEnter(0)
        advanceUntilIdle()

        model.onEnter(0) // a muscle: no children
        advanceUntilIdle()

        assertEquals(3, model.state.value.level)
    }

    @Test
    fun going_up_returns_to_the_parent_level_with_the_parent_focused() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()

        model.onUp()
        advanceUntilIdle()

        assertEquals(1, model.state.value.level)
        assertEquals("Muscular system", model.state.value.focused?.name)
    }

    @Test
    fun up_at_the_top_does_nothing() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onUp()
        advanceUntilIdle()

        assertEquals(1, model.state.value.level)
    }

    @Test
    fun a_failing_atlas_is_an_error_not_an_empty_level() = runTest(dispatcher) {
        val model = StructureTreeViewModel(
            FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") })),
            locale = "en",
        )
        advanceUntilIdle()

        assertEquals(AtlasError.LoadFailed, model.state.value.error)
    }

    // An empty initialPath is the case every test above already covers (the first one).

    @Test
    fun an_initial_path_opens_at_that_level_in_the_given_locale() = runTest(dispatcher) {
        val ids = listOf(StructureId("muscular"), StructureId("musculi-thoracis"))

        val en = StructureTreeViewModel(FakeAtlasRepository(), locale = "en", initialPath = ids)
        advanceUntilIdle()
        assertEquals(listOf("Muscular system", "Thoracic muscles"), en.state.value.path.map { it.name })
        assertEquals(4, en.state.value.items.size)
        assertEquals(3, en.state.value.level)

        val pl = StructureTreeViewModel(FakeAtlasRepository(), locale = "pl", initialPath = ids)
        advanceUntilIdle()
        assertEquals(listOf("Układ mięśniowy", "Mięśnie klatki piersiowej"), pl.state.value.path.map { it.name })
        assertEquals("Mięsień piersiowy większy", pl.state.value.items.first().name)
    }

    @Test
    fun an_initial_path_with_an_unknown_id_falls_back_to_the_top_level() = runTest(dispatcher) {
        val model = StructureTreeViewModel(
            FakeAtlasRepository(),
            locale = "en",
            initialPath = listOf(StructureId("muscular"), StructureId("no-such-structure")),
        )
        advanceUntilIdle()

        assertEquals(1, model.state.value.level)
        assertEquals(listOf("Skeletal system", "Muscular system"), model.names())
        assertEquals(false, model.state.value.isLoading)
        assertNull(model.state.value.error)
    }

    @Test
    fun the_last_navigation_wins_when_an_earlier_load_finishes_late() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val inner = FakeAtlasRepository()
        val repository = object : AtlasRepository by inner {
            override suspend fun children(parent: StructureId, locale: String): List<StructureSummary> {
                if (parent == StructureId("muscular")) gate.await()
                return inner.children(parent, locale)
            }
        }
        val model = StructureTreeViewModel(repository, locale = "en")
        advanceUntilIdle()

        model.onEnter(1) // Muscular system: blocks on the gate
        advanceUntilIdle()
        model.onEnter(0) // Skeletal system: completes
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Skeletal system"), model.state.value.path.map { it.name })
        assertNull(model.state.value.error)
    }
}

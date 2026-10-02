package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtlasViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = StructureId("skeletal")
    private val ribs = StructureId("costae")
    private val seventhRib = StructureId("costa-vii")
    private val repository = FakeAtlasRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AtlasViewModel(repository, locale = "en")

    @Test
    fun starts_at_the_roots_with_nothing_expanded() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Skeletal system"), model.state.value.rows.map { it.summary.name })
        assertEquals(false, model.state.value.isLoading)
    }

    @Test
    fun expanding_a_node_reveals_its_children_indented() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onRowToggled(skeletal)
        advanceUntilIdle()

        assertEquals(
            listOf("Skeletal system", "Ribs", "Cervical vertebrae"),
            model.state.value.rows.map { it.summary.name },
        )
        assertEquals(listOf(0, 1, 1), model.state.value.rows.map { it.depth })
    }

    @Test
    fun children_are_fetched_once_and_reused_when_collapsed_and_reopened() = runTest(dispatcher) {
        // The whole taxonomy is thousands of rows; re-querying on every toggle is how a
        // tree screen becomes slow on a device.
        val model = viewModel()
        advanceUntilIdle()

        repeat(3) { model.onRowToggled(skeletal); advanceUntilIdle() }

        assertEquals(1, repository.childrenCalls)
    }

    @Test
    fun selecting_a_leaf_marks_it_for_highlighting() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onRowToggled(skeletal)
        advanceUntilIdle()
        model.onRowToggled(ribs)
        advanceUntilIdle()

        model.onRowSelected(model.state.value.rows.single { it.summary.id == seventhRib }.summary)

        assertEquals(seventhRib, model.state.value.selected)
        assertEquals("Rib VII", model.state.value.selectedName)
    }

    @Test
    fun selecting_a_group_names_it_but_highlights_nothing() = runTest(dispatcher) {
        // A group has no geometry of its own, so there is nothing to outline.
        val model = viewModel()
        advanceUntilIdle()

        model.onRowSelected(model.state.value.rows.first().summary)

        assertNull(model.state.value.selected)
        assertEquals("Skeletal system", model.state.value.selectedName)
    }

    @Test
    fun a_pick_in_the_model_selects_the_same_structure_the_tree_would() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onPickedInModel(seventhRib)
        advanceUntilIdle()

        assertEquals(seventhRib, model.state.value.selected)
        assertEquals("Rib VII", model.state.value.selectedName)
    }

    @Test
    fun a_miss_in_the_model_clears_the_selection() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onPickedInModel(seventhRib)
        advanceUntilIdle()

        model.onPickedInModel(null)
        advanceUntilIdle()

        assertNull(model.state.value.selected)
    }

    @Test
    fun a_failing_repository_surfaces_as_an_error_rather_than_an_empty_tree() = runTest(dispatcher) {
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))
        val model = AtlasViewModel(broken, "en")
        advanceUntilIdle()

        assertTrue(model.state.value.error != null)
        assertEquals(false, model.state.value.isLoading)
    }
}

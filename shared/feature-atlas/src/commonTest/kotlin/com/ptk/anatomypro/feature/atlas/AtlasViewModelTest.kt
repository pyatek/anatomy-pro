package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.Laterality
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

private fun summary(id: String, name: String, group: Boolean = false, children: Boolean = false) =
    StructureSummary(StructureId(id), name, name, Laterality.MEDIAN, group, children)

private class FakeAtlasRepository(
    private val tree: Map<String, List<StructureSummary>>,
    private val roots: List<StructureSummary>,
) : AtlasRepository {
    var childrenCalls = 0
        private set

    override suspend fun roots(locale: String) = roots
    override suspend fun children(parent: StructureId, locale: String): List<StructureSummary> {
        childrenCalls++
        return tree[parent.value].orEmpty()
    }
    override suspend fun summary(id: StructureId, locale: String) =
        (roots + tree.values.flatten()).firstOrNull { it.id == id }
    override suspend fun detail(id: StructureId, locale: String) = null
    override suspend fun search(query: String, limit: Int) = emptyList<com.ptk.anatomypro.core.data.model.SearchHit>()
}

class AtlasViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val ribs = summary("1105-costae-median", "Ribs", group = true, children = true)
    private val first = summary("1107-costa-prima-left", "First rib")
    private val repository = FakeAtlasRepository(
        roots = listOf(ribs),
        tree = mapOf("1105-costae-median" to listOf(first)),
    )

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AtlasViewModel(repository, locale = "en")

    @Test
    fun starts_at_the_roots_with_nothing_expanded() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Ribs"), model.state.value.rows.map { it.summary.name })
        assertEquals(false, model.state.value.isLoading)
    }

    @Test
    fun expanding_a_node_reveals_its_children_indented() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onRowToggled(ribs.id)
        advanceUntilIdle()

        assertEquals(listOf("Ribs", "First rib"), model.state.value.rows.map { it.summary.name })
        assertEquals(listOf(0, 1), model.state.value.rows.map { it.depth })
    }

    @Test
    fun children_are_fetched_once_and_reused_when_collapsed_and_reopened() = runTest(dispatcher) {
        // The whole taxonomy is thousands of rows; re-querying on every toggle is how a
        // tree screen becomes slow on a device.
        val model = viewModel()
        advanceUntilIdle()

        repeat(3) { model.onRowToggled(ribs.id); advanceUntilIdle() }

        assertEquals(1, repository.childrenCalls)
    }

    @Test
    fun selecting_a_leaf_marks_it_for_highlighting() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onRowToggled(ribs.id)
        advanceUntilIdle()

        model.onRowSelected(model.state.value.rows.last().summary)

        assertEquals(first.id, model.state.value.selected)
        assertEquals("First rib", model.state.value.selectedName)
    }

    @Test
    fun selecting_a_group_names_it_but_highlights_nothing() = runTest(dispatcher) {
        // A group has no geometry of its own, so there is nothing to outline.
        val model = viewModel()
        advanceUntilIdle()

        model.onRowSelected(model.state.value.rows.first().summary)

        assertNull(model.state.value.selected)
        assertEquals("Ribs", model.state.value.selectedName)
    }

    @Test
    fun a_pick_in_the_model_selects_the_same_structure_the_tree_would() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onPickedInModel(first.id)
        advanceUntilIdle()

        assertEquals(first.id, model.state.value.selected)
        assertEquals("First rib", model.state.value.selectedName)
    }

    @Test
    fun a_miss_in_the_model_clears_the_selection() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onPickedInModel(first.id)
        advanceUntilIdle()

        model.onPickedInModel(null)
        advanceUntilIdle()

        assertNull(model.state.value.selected)
    }

    @Test
    fun a_failing_repository_surfaces_as_an_error_rather_than_an_empty_tree() = runTest(dispatcher) {
        val broken = object : AtlasRepository {
            override suspend fun roots(locale: String): List<StructureSummary> = error("no database")
            override suspend fun children(parent: StructureId, locale: String) = emptyList<StructureSummary>()
            override suspend fun summary(id: StructureId, locale: String): StructureSummary? = null
            override suspend fun detail(id: StructureId, locale: String) = null
            override suspend fun search(query: String, limit: Int) = emptyList<com.ptk.anatomypro.core.data.model.SearchHit>()
        }
        val model = AtlasViewModel(broken, "en")
        advanceUntilIdle()

        assertTrue(model.state.value.error != null)
        assertEquals(false, model.state.value.isLoading)
    }
}

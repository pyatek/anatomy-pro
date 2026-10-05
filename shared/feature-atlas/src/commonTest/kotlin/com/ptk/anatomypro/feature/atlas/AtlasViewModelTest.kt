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
import kotlin.test.assertTrue

class AtlasViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = StructureId("skeletal")
    private val repository = FakeAtlasRepository()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AtlasViewModel(repository, locale = "en")

    @Test
    fun starts_at_the_roots_with_nothing_expanded() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Skeletal system", "Muscular system"), model.state.value.rows.map { it.summary.name })
        assertEquals(false, model.state.value.isLoading)
    }

    @Test
    fun expanding_a_node_reveals_its_children_indented() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onRowToggled(skeletal)
        advanceUntilIdle()

        assertEquals(
            listOf("Skeletal system", "Ribs", "Cervical vertebrae", "Muscular system"),
            model.state.value.rows.map { it.summary.name },
        )
        assertEquals(listOf(0, 1, 1, 0), model.state.value.rows.map { it.depth })
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

    // Selection is the scene's: see AtlasSceneViewModelTest.

    @Test
    fun a_failing_repository_surfaces_as_an_error_rather_than_an_empty_tree() = runTest(dispatcher) {
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") }))
        val model = AtlasViewModel(broken, "en")
        advanceUntilIdle()

        assertTrue(model.state.value.error != null)
        assertEquals(false, model.state.value.isLoading)
    }
}

package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.model.StructureDetail
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AtlasSceneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val muscle = SystemId("muscular-system")
    private val bone = SystemId("skeletal-system")
    private val seventhRib = StructureId("costa-vii")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model() = AtlasSceneViewModel(FakeAtlasRepository())

    @Test
    fun everything_starts_visible_with_one_row_per_system() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(setOf(bone, muscle), model.panel.value.rows.map { it.system }.toSet())
        assertTrue(model.panel.value.rows.all { it.mode == LayerMode.Visible })
        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun rows_follow_the_display_order_from_the_body_surface_inwards() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        // Muscle before bone, as the prototype lists them.
        assertEquals(listOf(muscle, bone), model.panel.value.rows.map { it.system })
    }

    @Test
    fun hiding_a_system_hides_its_structures_and_only_them() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setLayer(muscle, LayerMode.Hidden)
        advanceUntilIdle()

        assertTrue(StructureId("musculus-subclavius") in model.render.value.hidden)
        assertTrue(seventhRib !in model.render.value.hidden)
        assertEquals(LayerMode.Hidden, model.panel.value.rows.single { it.system == muscle }.mode)
    }

    @Test
    fun ghosting_a_system_uses_the_panels_opacity() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setLayer(bone, LayerMode.Ghosted)
        advanceUntilIdle()

        assertTrue(seventhRib in model.render.value.ghosted)
        assertEquals(0.3f, model.render.value.ghostAlpha)
    }

    @Test
    fun isolating_the_selection_ghosts_its_siblings_and_hides_the_rest() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        val render = model.render.value
        assertTrue(StructureId("costa-vi") in render.ghosted)
        assertTrue(StructureId("musculus-subclavius") in render.hidden)
        assertTrue(seventhRib !in render.hidden && seventhRib !in render.ghosted)
    }

    @Test
    fun isolating_frames_the_isolated_structure() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        assertEquals(seventhRib, model.cameraFocus.value?.structure)
    }

    @Test
    fun isolation_with_nothing_selected_changes_nothing() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setIsolation(true)
        advanceUntilIdle()

        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun the_opacity_is_clamped_to_what_still_reads_as_a_shell() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setGhostPercent(95)
        advanceUntilIdle()
        assertEquals(MAX_GHOST_PERCENT, model.panel.value.ghostPercent)

        model.setGhostPercent(0)
        advanceUntilIdle()
        assertEquals(MIN_GHOST_PERCENT, model.panel.value.ghostPercent)
    }

    @Test
    fun reset_shows_everything_and_ends_isolation() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.setLayer(muscle, LayerMode.Hidden)
        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        model.resetLayers()
        advanceUntilIdle()

        assertEquals(RenderState.None, model.render.value)
        assertEquals(false, model.panel.value.isolate)
    }

    @Test
    fun reset_returns_the_camera_to_the_whole_model() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()
        val isolated = model.cameraFocus.value

        model.resetLayers()
        advanceUntilIdle()

        val request = assertNotNull(model.cameraFocus.value)
        assertNull(request.structure)
        assertEquals(FOCUS_DURATION_MS, request.durationMs)
        assertNotEquals(isolated?.serial, request.serial)
    }

    @Test
    fun turning_isolation_off_returns_the_camera_to_the_whole_model() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        model.setIsolation(false)
        advanceUntilIdle()

        val request = assertNotNull(model.cameraFocus.value)
        assertNull(request.structure)
        assertEquals(FOCUS_DURATION_MS, request.durationMs)
    }

    @Test
    fun turning_isolation_off_when_it_was_not_on_leaves_the_camera_alone() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setIsolation(false)
        advanceUntilIdle()

        assertNull(model.cameraFocus.value)
    }

    @Test
    fun deselecting_while_isolated_returns_the_camera_to_the_whole_model() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        model.onStructureSelected(null)
        advanceUntilIdle()

        val request = assertNotNull(model.cameraFocus.value)
        assertNull(request.structure)
        assertEquals(FOCUS_DURATION_MS, request.durationMs)
    }

    @Test
    fun deselecting_without_isolation_leaves_the_camera_alone() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onStructureSelected(seventhRib)
        advanceUntilIdle()

        model.onStructureSelected(null)
        advanceUntilIdle()

        assertNull(model.cameraFocus.value)
    }

    @Test
    fun asking_twice_for_the_same_structure_is_two_requests() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.focusCamera(seventhRib)
        val first = model.cameraFocus.value
        model.focusCamera(seventhRib)

        assertNotEquals(first, model.cameraFocus.value)
        assertEquals(FOCUS_DURATION_MS, model.cameraFocus.value?.durationMs)
    }

    @Test
    fun selecting_does_not_move_the_camera_by_itself() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        advanceUntilIdle()

        assertNull(model.cameraFocus.value)
    }

    @Test
    fun a_slower_earlier_resolve_never_overwrites_the_latest_state() = runTest(dispatcher) {
        val gated = GatedRepository(FakeAtlasRepository())
        val model = AtlasSceneViewModel(gated)
        advanceUntilIdle()
        model.onStructureSelected(seventhRib)
        advanceUntilIdle()

        model.setIsolation(true) // its resolve blocks on the repository
        advanceUntilIdle()
        model.setIsolation(false) // a later, quick resolve
        advanceUntilIdle()
        gated.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun a_failing_atlas_leaves_an_empty_panel_rather_than_crashing() = runTest(dispatcher) {
        // This model is created for every tab; an exception escaping it takes the app down.
        val model = AtlasSceneViewModel(FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") })))
        advanceUntilIdle()

        assertEquals(false, model.panel.value.isLoading)
        assertTrue(model.panel.value.rows.isEmpty())
        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun a_failure_while_isolating_keeps_what_was_drawn() = runTest(dispatcher) {
        val inner = FakeAtlasRepository()
        val model = AtlasSceneViewModel(
            object : AtlasRepository by inner {
                override suspend fun detail(id: StructureId, locale: String): StructureDetail? =
                    throw IllegalStateException("no database")
            },
        )
        advanceUntilIdle()
        model.setLayer(muscle, LayerMode.Hidden)
        advanceUntilIdle()
        val before = model.render.value

        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        assertEquals(before, model.render.value)
        assertTrue(model.panel.value.isolate)
    }

    private class GatedRepository(private val delegate: AtlasRepository) : AtlasRepository by delegate {
        val gate = CompletableDeferred<Unit>()
        override suspend fun detail(id: StructureId, locale: String): StructureDetail? {
            gate.await()
            return delegate.detail(id, locale)
        }
    }
}

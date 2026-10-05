package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.AnatomyRendererContract
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec §15's contract, run against Filament on a real GPU.
 *
 * This is the Phase 0 gate in test form. It is the same class the fake is held to, so a
 * behaviour that passes here and passes there is genuinely shared — which is the only way
 * screen tests built on the fake can be trusted to predict the device.
 */
class FilamentAnatomyRendererContractTest : AnatomyRendererContract() {

    override val pack = PackId("phase0-toy")
    override val source get() = MeshSource("file://${Phase0ToyAsset.path}")

    /** The cube at the origin, which sits under the centre of a square viewport. */
    override val hitStructure = StructureId("a02-2-00-000-columna-vertebralis-median")

    override suspend fun createRenderer(): AnatomyRenderer =
        FilamentAnatomyRenderer().apply { attachHeadless(VIEWPORT, VIEWPORT) }

    override suspend fun disposeRenderer(renderer: AnatomyRenderer) {
        (renderer as FilamentAnatomyRenderer).dispose()
    }

    override suspend fun pickHit(renderer: AnatomyRenderer) {
        (renderer as FilamentAnatomyRenderer).pickAt(VIEWPORT / 2f, VIEWPORT / 2f)
    }

    override suspend fun pickMiss(renderer: AnatomyRenderer) {
        // A corner of the viewport: the row of cubes is framed centrally, so nothing is there.
        (renderer as FilamentAnatomyRenderer).pickAt(4f, 4f)
    }

    /** The left cube of the toy row, 1.5 units left of centre. */
    override val offCentreStructure = StructureId("a02-4-01-001-scapula-left")

    override suspend fun pickCentre(renderer: AnatomyRenderer) {
        (renderer as FilamentAnatomyRenderer).pickAt(VIEWPORT / 2f, VIEWPORT / 2f)
    }

    /**
     * Picking results are produced by the GPU some frames after the query, so the contract
     * cannot assert on them until frames have actually been drawn and completed.
     */
    override suspend fun settle(renderer: AnatomyRenderer) {
        val filament = renderer as FilamentAnatomyRenderer
        repeat(FRAMES_TO_SETTLE) {
            filament.renderFrame()
            // Filament refuses a frame while too many are already in flight, so a tight
            // loop would skip most of them and the readback would never complete.
            filament.waitForGpu()
        }
    }

    @Test fun centres_a_focused_structure() = runBlocking { verifyFocusingTheCameraCentresAStructure() }
    @Test fun returns_to_the_whole_model() = runBlocking { verifyFramingAllReturnsTheCentreToTheWholeModel() }
    @Test fun signals_ready_then_pack_loaded() = runBlocking { verifyLoadingAPackSignalsReadyThenLoaded() }
    @Test fun reports_a_pick() = runBlocking { verifyPickingAStructureReportsIt() }
    @Test fun reports_a_miss() = runBlocking { verifyPickingEmptySpaceReportsNothing() }
    @Test fun honours_disabled_picking() = runBlocking { verifyPickingCanBeDisabled() }
    @Test fun accepts_a_highlight() = runBlocking { verifyHighlightingALoadedStructureIsAccepted() }
    @Test fun forgets_an_unloaded_pack() = runBlocking { verifyUnloadingAPackForgetsIt() }
    @Test fun hides_a_structure_from_picking() = runBlocking { verifyHidingAStructureRemovesItFromPicking() }
    @Test fun shows_a_hidden_structure_again() = runBlocking { verifyShowingAHiddenStructureRestoresPicking() }
    @Test fun keeps_a_ghosted_structure_pickable() = runBlocking { verifyAGhostedStructureStaysPickable() }
    @Test fun does_not_fault_when_highlight_and_ghost_interleave() = runBlocking { verifyDoesNotFaultWhenHighlightAndGhostInterleave() }

    /**
     * The surface is the host's, and it arrives and changes size on the host's schedule: a
     * canvas is told where to look as soon as its pack has loaded, which can be before it
     * has anything to draw into. Framing the whole model again on every attach sent the
     * camera back from wherever the app had just put it.
     */
    @Test fun keeps_a_focused_camera_when_the_surface_arrives_afterwards() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.loadPack(pack, source)
            renderer.focusCamera(offCentreStructure, durationMs = 0)
            renderer.attachHeadless(VIEWPORT, VIEWPORT)

            assertEquals(offCentreStructure, structureAtCentre(renderer, VIEWPORT))
        } finally {
            renderer.dispose()
        }
    }

    @Test fun keeps_a_camera_flight_when_the_surface_arrives_afterwards() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.loadPack(pack, source)
            renderer.focusCamera(offCentreStructure, durationMs = FLIGHT_MS)
            renderer.attachHeadless(VIEWPORT, VIEWPORT)
            // Two frames further apart than the flight is long, so it has landed.
            renderer.renderFrame(FLIGHT_START_NANOS)
            renderer.waitForGpu()
            renderer.renderFrame(FLIGHT_START_NANOS + 2 * FLIGHT_MS * 1_000_000L)
            renderer.waitForGpu()

            assertEquals(offCentreStructure, structureAtCentre(renderer, VIEWPORT))
        } finally {
            renderer.dispose()
        }
    }

    @Test fun keeps_a_focused_camera_when_the_surface_changes_size() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.attachHeadless(VIEWPORT, VIEWPORT)
            renderer.loadPack(pack, source)
            renderer.focusCamera(offCentreStructure, durationMs = 0)
            renderer.attachHeadless(VIEWPORT, VIEWPORT / 2)

            assertEquals(offCentreStructure, structureAtCentre(renderer, VIEWPORT / 2))
        } finally {
            renderer.dispose()
        }
    }

    @Test fun frames_the_whole_model_when_the_surface_arrives_and_nothing_was_asked() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.loadPack(pack, source)
            renderer.attachHeadless(VIEWPORT, VIEWPORT)

            assertEquals(hitStructure, structureAtCentre(renderer, VIEWPORT))
        } finally {
            renderer.dispose()
        }
    }

    /** What a pick at the middle of a [VIEWPORT]-wide, [height]-tall surface reports. */
    private suspend fun structureAtCentre(renderer: FilamentAnatomyRenderer, height: Int): StructureId? {
        settle(renderer)
        renderer.pickAt(VIEWPORT / 2f, height / 2f)
        settle(renderer)
        val picked = withTimeout(PICK_TIMEOUT_MS) { renderer.events.first { it is RendererEvent.Picked } }
        return (picked as RendererEvent.Picked).structure
    }

    private companion object {
        const val VIEWPORT = 512
        const val FRAMES_TO_SETTLE = 4
        const val FLIGHT_MS = 600
        const val FLIGHT_START_NANOS = 1_000_000_000L
        const val PICK_TIMEOUT_MS = 15_000L
    }
}

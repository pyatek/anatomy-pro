package com.ptk.anatomypro.renderer.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.AnatomyRendererContract
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §15's contract, run against Filament on a real GPU.
 *
 * The same class the fake and the iOS renderer are held to. Three implementations passing
 * one suite is the only thing that makes "the same interface behaviour on both platforms"
 * a fact rather than an intention.
 */
@RunWith(AndroidJUnit4::class)
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
        (renderer as FilamentAnatomyRenderer).pickAt(4f, 4f)
    }

    /** The left cube of the toy row, 1.5 units left of centre. */
    override val offCentreStructure = StructureId("a02-4-01-001-scapula-left")

    override suspend fun pickCentre(renderer: AnatomyRenderer) {
        (renderer as FilamentAnatomyRenderer).pickAt(VIEWPORT / 2f, VIEWPORT / 2f)
    }

    /**
     * Filament refuses a frame while too many are in flight, so a tight loop would skip
     * most of them and the picking readback would never complete. Learned on iOS; the
     * engine is the same one here.
     */
    override suspend fun settle(renderer: AnatomyRenderer) {
        val filament = renderer as FilamentAnatomyRenderer
        repeat(FRAMES_TO_SETTLE) {
            filament.renderFrame()
            filament.waitForGpu()
        }
    }

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
    @Test fun centres_a_focused_structure() = runBlocking { verifyFocusingTheCameraCentresAStructure() }
    @Test fun returns_to_the_whole_model() = runBlocking { verifyFramingAllReturnsTheCentreToTheWholeModel() }

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

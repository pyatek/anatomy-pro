package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.AnatomyRendererContract
import com.ptk.anatomypro.renderer.api.MeshSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

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

    @Test fun signals_ready_then_pack_loaded() = runBlocking { verifyLoadingAPackSignalsReadyThenLoaded() }
    @Test fun reports_a_pick() = runBlocking { verifyPickingAStructureReportsIt() }
    @Test fun reports_a_miss() = runBlocking { verifyPickingEmptySpaceReportsNothing() }
    @Test fun honours_disabled_picking() = runBlocking { verifyPickingCanBeDisabled() }
    @Test fun accepts_a_highlight() = runBlocking { verifyHighlightingALoadedStructureIsAccepted() }
    @Test fun forgets_an_unloaded_pack() = runBlocking { verifyUnloadingAPackForgetsIt() }
    @Test fun hides_a_structure_from_picking() = runBlocking { verifyHidingAStructureRemovesItFromPicking() }
    @Test fun shows_a_hidden_structure_again() = runBlocking { verifyShowingAHiddenStructureRestoresPicking() }
    @Test fun keeps_a_ghosted_structure_pickable() = runBlocking { verifyAGhostedStructureStaysPickable() }
    @Test fun resolves_highlight_over_ghost() = runBlocking { verifyHighlightAndGhostResolveInEitherOrder() }

    private companion object {
        const val VIEWPORT = 512
        const val FRAMES_TO_SETTLE = 4
    }
}

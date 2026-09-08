package com.ptk.anatomypro.renderer.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.AnatomyRendererContract
import com.ptk.anatomypro.renderer.api.MeshSource
import kotlinx.coroutines.runBlocking
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

    private companion object {
        const val VIEWPORT = 512
        const val FRAMES_TO_SETTLE = 4
    }
}

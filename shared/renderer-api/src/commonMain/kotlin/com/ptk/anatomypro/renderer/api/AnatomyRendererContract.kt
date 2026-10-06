package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The behaviour every [AnatomyRenderer] must show, whatever draws the pixels.
 *
 * Spec §15 asks for contract tests "asserting the same interface behaviour on both
 * platforms — load, pick, highlight, unload". This is that suite, and it lives in
 * commonMain for the same reason [FakeAnatomyRenderer] does: Kotlin Multiplatform has no
 * working test fixtures, so a contract confined to a test source set could not be applied
 * to another module's implementation (spec §20.3).
 *
 * It deliberately carries no `kotlin.test` dependency — that would follow the module into
 * the release binary. Subclasses in test source sets supply the `@Test` annotations and
 * the coroutine builder; this class supplies the assertions.
 */
abstract class AnatomyRendererContract {

    /** A fresh renderer, already attached to whatever surface it needs. */
    protected abstract suspend fun createRenderer(): AnatomyRenderer

    /** Releases a renderer created by [createRenderer]. */
    protected abstract suspend fun disposeRenderer(renderer: AnatomyRenderer)

    protected abstract val pack: PackId

    /** A pack payload containing [hitStructure]. */
    protected abstract val source: MeshSource

    /** A structure the fixture contains, which [pickHit] resolves to. */
    protected abstract val hitStructure: StructureId

    /** Drives the implementation to pick [hitStructure]. */
    protected abstract suspend fun pickHit(renderer: AnatomyRenderer)

    /** Drives the implementation to pick empty space. */
    protected abstract suspend fun pickMiss(renderer: AnatomyRenderer)

    /** A structure the fixture contains that is **not** under the viewport centre at first. */
    protected abstract val offCentreStructure: StructureId

    /** Drives the implementation to pick the exact centre of the viewport. */
    protected abstract suspend fun pickCentre(renderer: AnatomyRenderer)

    /**
     * Called between an action and the assertion that follows it.
     *
     * A GPU-backed renderer only resolves picking once further frames have been drawn, so
     * the real implementation pumps frames here. The fake does nothing.
     */
    protected open suspend fun settle(renderer: AnatomyRenderer) = Unit

    suspend fun verifyLoadingAPackSignalsReadyThenLoaded() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        awaitEvent(renderer) { it is RendererEvent.Ready }
        val loaded = awaitEvent(renderer) { it is RendererEvent.PackLoaded }
        assertEquals(pack, (loaded as RendererEvent.PackLoaded).pack, "loaded pack")
    }

    suspend fun verifyPickingAStructureReportsIt() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(hitStructure, (picked as RendererEvent.Picked).structure, "picked structure")
    }

    suspend fun verifyPickingEmptySpaceReportsNothing() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        pickMiss(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(null, (picked as RendererEvent.Picked).structure, "picked structure")
    }

    suspend fun verifyPickingCanBeDisabled() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setPickingEnabled(false)
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEventOrNull(renderer) { it is RendererEvent.Picked }
        assertEquals(null, picked, "a pick was reported while picking was disabled")
    }

    suspend fun verifyHighlightingALoadedStructureIsAccepted() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // Highlighting is asserted through survival, not pixels: the contract cannot see
        // colour, and a renderer that faults on a valid structure fails here.
        renderer.highlight(setOf(hitStructure), HIGHLIGHT)
        settle(renderer)
        renderer.highlight(emptySet(), HIGHLIGHT)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported during highlight")
    }

    suspend fun verifySeveralHighlightsAtOnceAreAccepted() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // Survival again, not pixels. Two structures in two styles, with a third the pack
        // does not contain; then one of them restyled; then nothing.
        val absent = StructureId("no-such-structure")
        renderer.highlight(
            mapOf(hitStructure to HIGHLIGHT, offCentreStructure to SECOND_HIGHLIGHT, absent to HIGHLIGHT)
        )
        settle(renderer)
        renderer.highlight(mapOf(hitStructure to SECOND_HIGHLIGHT))
        settle(renderer)
        renderer.highlight(emptyMap())
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported highlighting several structures")
    }

    suspend fun verifyHidingAStructureRemovesItFromPicking() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setVisibility(setOf(hitStructure), visible = false)
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            null,
            (picked as RendererEvent.Picked).structure,
            "a hidden structure was picked",
        )
    }

    suspend fun verifyShowingAHiddenStructureRestoresPicking() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setVisibility(setOf(hitStructure), visible = false)
        settle(renderer)
        renderer.setVisibility(setOf(hitStructure), visible = true)
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            hitStructure,
            (picked as RendererEvent.Picked).structure,
            "picked structure after being shown again",
        )
    }

    suspend fun verifyAGhostedStructureStaysPickable() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        settle(renderer)

        // The pick happens while the structure is still ghosted, which is the whole point:
        // Filament drops blended renderables out of picking unless transparent picking is
        // switched on, and §26.2 needs a ghosted neighbour to stay tappable — it is what the
        // learner taps to navigate. Picking after the ghost was reversed would prove nothing.
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            hitStructure,
            (picked as RendererEvent.Picked).structure,
            "a ghosted structure must stay pickable",
        )

        // Reversal is exercised but only weakly asserted: the contract cannot read a
        // primitive's material back, so "the original was restored" has no observable
        // channel here. A second pick cannot help — `events` replays, so awaiting another
        // Picked would re-match the one above.
        renderer.setOpacity(setOf(hitStructure), alpha = 1.0f)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported while ghosting")
    }

    suspend fun verifyDoesNotFaultWhenHighlightAndGhostInterleave() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // This test detects crashes and wiring failures only: it does not verify material state
        // (colour, opacity, or other visual attributes), which have no observable channel
        // through this interface. It exercises interleaving to ensure the renderer does not
        // fault when calls are made in arbitrary order.
        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        renderer.highlight(setOf(hitStructure), HIGHLIGHT)
        settle(renderer)

        renderer.highlight(emptySet(), HIGHLIGHT)
        renderer.setOpacity(setOf(hitStructure), alpha = 1.0f)
        renderer.highlight(setOf(hitStructure), HIGHLIGHT)
        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported resolving highlight over ghost")
    }

    suspend fun verifyUnloadingAPackForgetsIt() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)
        awaitEvent(renderer) { it is RendererEvent.PackLoaded }

        renderer.unloadPack(pack)
        settle(renderer)

        val unloaded = awaitEvent(renderer) { it is RendererEvent.PackUnloaded }
        assertEquals(pack, (unloaded as RendererEvent.PackUnloaded).pack, "unloaded pack")
    }

    /**
     * Focusing frames a structure in the middle of the view. Proven by picking: the centre
     * of the viewport starts on [hitStructure] and must report [offCentreStructure] once
     * the camera has moved to it. Zero duration, so no frame clock is involved.
     */
    suspend fun verifyFocusingTheCameraCentresAStructure() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.focusCamera(offCentreStructure, durationMs = 0)
        settle(renderer)
        pickCentre(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(offCentreStructure, (picked as RendererEvent.Picked).structure, "structure at the centre after focusing")
    }

    /**
     * Framing all returns the camera to the whole model, as on load. Proven by picking: the
     * camera first moves to [offCentreStructure], and the centre must report [hitStructure]
     * again once the whole model is framed. Zero durations, so no frame clock is involved.
     */
    suspend fun verifyFramingAllReturnsTheCentreToTheWholeModel() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.focusCamera(offCentreStructure, durationMs = 0)
        settle(renderer)
        renderer.frameAll(durationMs = 0)
        settle(renderer)
        pickCentre(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(hitStructure, (picked as RendererEvent.Picked).structure, "structure at the centre after framing all")
    }

    private suspend fun withRenderer(block: suspend (AnatomyRenderer) -> Unit) {
        val renderer = createRenderer()
        try {
            block(renderer)
        } finally {
            disposeRenderer(renderer)
        }
    }

    /**
     * Waits for an event matching [predicate].
     *
     * Relies on [AnatomyRenderer.events] replaying recent events, which the interface
     * requires: a subscriber attaching after an action still has to see it, or the §4
     * recovery-by-replay story does not hold either.
     */
    private suspend fun awaitEvent(
        renderer: AnatomyRenderer,
        predicate: (RendererEvent) -> Boolean,
    ): RendererEvent = withTimeout(TIMEOUT_MS) { renderer.events.first(predicate) }

    /**
     * Waits briefly for an event that should never arrive, and returns null when it does not.
     *
     * The wait is short because [settle] has already given the implementation every chance
     * to produce one; anything arriving later would be arriving too late to matter.
     */
    private suspend fun awaitEventOrNull(
        renderer: AnatomyRenderer,
        predicate: (RendererEvent) -> Boolean,
    ): RendererEvent? = withTimeoutOrNull(ABSENCE_TIMEOUT_MS) { renderer.events.first(predicate) }

    private fun assertEquals(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) {
            throw AssertionError("$what: expected <$expected> but was <$actual>")
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val ABSENCE_TIMEOUT_MS = 2_000L

        val HIGHLIGHT = HighlightStyle(
            outlineArgb = 0xFFFFD3CB.toInt(),
            outlineWidthDp = 2f,
            outlineStyle = OutlineStyle.SOLID,
            fillArgb = 0xFFF07C69.toInt(),
            fillLuminanceShift = 0.25f,
        )

        /** Darker and a different hue: the wrong answer beside the right one. */
        val SECOND_HIGHLIGHT = HighlightStyle(
            outlineArgb = 0xFFD89B3C.toInt(),
            outlineWidthDp = 3f,
            outlineStyle = OutlineStyle.DASHED,
            fillArgb = 0xFFD89B3C.toInt(),
            fillLuminanceShift = -0.20f,
        )
    }
}

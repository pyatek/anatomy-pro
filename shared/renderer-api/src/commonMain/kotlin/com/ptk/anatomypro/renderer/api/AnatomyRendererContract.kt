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

    suspend fun verifyGhostingIsReversible() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // Like highlight, ghosting is asserted through survival and through picking, which
        // the contract can see; colour, which it cannot see, is left to the eye.
        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        settle(renderer)
        renderer.setOpacity(setOf(hitStructure), alpha = 1.0f)
        pickHit(renderer)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported while ghosting")

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            hitStructure,
            (picked as RendererEvent.Picked).structure,
            "a ghosted structure must stay pickable",
        )
    }

    suspend fun verifyHighlightAndGhostResolveInEitherOrder() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

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
    }
}

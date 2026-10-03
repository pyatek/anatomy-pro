package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.FakeAnatomyRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The renderer's verbs are per call — setVisibility(x, false) then setVisibility(y, false)
 * hides both — so moving from one RenderState to the next is a diff. These tests hold the
 * fake to the end state, whatever the start.
 */
class ApplyRenderStateTest {

    private val a = StructureId("a")
    private val b = StructureId("b")
    private val c = StructureId("c")

    private fun assertShows(renderer: FakeAnatomyRenderer, state: RenderState) {
        assertEquals(state.hidden, renderer.hidden, "hidden")
        assertEquals(state.ghosted, renderer.ghosted, "ghosted")
        if (state.ghosted.isNotEmpty()) assertEquals(state.ghostAlpha, renderer.ghostAlpha, "alpha")
    }

    @Test
    fun from_nothing_to_a_state() {
        val renderer = FakeAnatomyRenderer()
        val next = RenderState(hidden = setOf(a), ghosted = setOf(b), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, next)

        assertShows(renderer, next)
    }

    @Test
    fun a_structure_moving_from_hidden_to_ghosted_is_shown_and_then_ghosted() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = setOf(a, b), ghosted = emptySet(), ghostAlpha = 0.3f)
        val second = RenderState(hidden = setOf(b), ghosted = setOf(a, c), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, second)

        assertShows(renderer, second)
    }

    @Test
    fun a_changed_alpha_is_resent_even_when_the_set_is_unchanged() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = emptySet(), ghosted = setOf(a), ghostAlpha = 0.3f)
        val second = first.copy(ghostAlpha = 0.5f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, second)

        assertEquals(0.5f, renderer.ghostAlpha)
    }

    @Test
    fun back_to_nothing_clears_everything() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = setOf(a), ghosted = setOf(b), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, RenderState.None)

        assertShows(renderer, RenderState.None)
    }
}

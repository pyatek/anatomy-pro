package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeAnatomyRendererTest {

    private val pack = PackId("skeletal-thorax")
    private val scapula = StructureId("scapula-left")

    @Test
    fun signals_ready_then_pack_loaded_when_a_pack_is_loaded() = runTest {
        val renderer = FakeAnatomyRenderer()

        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        assertEquals(
            listOf(RendererEvent.Ready, RendererEvent.PackLoaded(pack)),
            renderer.emitted,
        )
    }

    @Test
    fun reports_a_pick_as_an_event_so_the_quiz_loop_is_testable_without_a_gpu() = runTest {
        val renderer = FakeAnatomyRenderer()
        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        renderer.emitPick(scapula)

        assertEquals(RendererEvent.Picked(scapula), renderer.emitted.last())
    }

    @Test
    fun reports_a_miss_as_a_pick_of_nothing() = runTest {
        val renderer = FakeAnatomyRenderer()

        renderer.emitPick(null)

        assertEquals(RendererEvent.Picked(null), renderer.emitted.last())
    }

    @Test
    fun holds_isolation_and_highlight_state_so_a_lost_surface_can_be_replayed() = runTest {
        val renderer = FakeAnatomyRenderer()
        val style = HighlightStyle(
            outlineArgb = 0xFFFFD3CB.toInt(),
            outlineWidthDp = 2f,
            outlineStyle = OutlineStyle.SOLID,
            fillArgb = 0xFFF07C69.toInt(),
            fillLuminanceShift = 0.25f,
        )

        renderer.isolate(scapula, ghostNeighbours = true)
        renderer.highlight(setOf(scapula), style)

        assertEquals(scapula, renderer.isolated)
        assertEquals(setOf(scapula), renderer.highlighted)
    }

    @Test
    fun forgets_a_pack_state_on_unload() = runTest {
        val renderer = FakeAnatomyRenderer()
        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        renderer.unloadPack(pack)

        assertTrue(renderer.loadedPacks.isEmpty())
    }
}

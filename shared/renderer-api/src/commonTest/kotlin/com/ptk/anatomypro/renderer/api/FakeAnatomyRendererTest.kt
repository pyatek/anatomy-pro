package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fake is held to the same contract as the Filament renderer.
 *
 * That is the point of it: if the fake could pass tests the device would fail, every
 * screen test built on it (spec §15) would be measuring nothing.
 */
class FakeAnatomyRendererContractTest : AnatomyRendererContract() {

    override val pack = PackId("skeletal-thorax")
    override val source = MeshSource("file:///packs/skeletal-thorax.glb")
    override val hitStructure = StructureId("a02-4-01-001-scapula-left")

    override suspend fun createRenderer(): AnatomyRenderer = FakeAnatomyRenderer()
    override suspend fun disposeRenderer(renderer: AnatomyRenderer) = Unit

    override suspend fun pickHit(renderer: AnatomyRenderer) {
        (renderer as FakeAnatomyRenderer).emitPick(hitStructure)
    }

    override suspend fun pickMiss(renderer: AnatomyRenderer) {
        (renderer as FakeAnatomyRenderer).emitPick(null)
    }

    override val offCentreStructure = StructureId("a02-2-00-000-columna-vertebralis-median")

    /** The fake's centre is whatever it was last asked to frame, or the hit structure. */
    override suspend fun pickCentre(renderer: AnatomyRenderer) {
        val fake = renderer as FakeAnatomyRenderer
        fake.emitPick(fake.focused ?: hitStructure)
    }

    @Test fun centres_a_focused_structure() = runTest { verifyFocusingTheCameraCentresAStructure() }
    @Test fun returns_to_the_whole_model() = runTest { verifyFramingAllReturnsTheCentreToTheWholeModel() }
    @Test fun signals_ready_then_pack_loaded() = runTest { verifyLoadingAPackSignalsReadyThenLoaded() }
    @Test fun reports_a_pick() = runTest { verifyPickingAStructureReportsIt() }
    @Test fun reports_a_miss() = runTest { verifyPickingEmptySpaceReportsNothing() }
    @Test fun honours_disabled_picking() = runTest { verifyPickingCanBeDisabled() }
    @Test fun accepts_a_highlight() = runTest { verifyHighlightingALoadedStructureIsAccepted() }
    @Test fun accepts_several_highlights_at_once() = runTest { verifySeveralHighlightsAtOnceAreAccepted() }
    @Test fun forgets_an_unloaded_pack() = runTest { verifyUnloadingAPackForgetsIt() }
    @Test fun hides_a_structure_from_picking() = runTest { verifyHidingAStructureRemovesItFromPicking() }
    @Test fun shows_a_hidden_structure_again() = runTest { verifyShowingAHiddenStructureRestoresPicking() }
    @Test fun keeps_a_ghosted_structure_pickable() = runTest { verifyAGhostedStructureStaysPickable() }
    @Test fun does_not_fault_when_highlight_and_ghost_interleave() = runTest { verifyDoesNotFaultWhenHighlightAndGhostInterleave() }
}

/** Behaviour that belongs to the fake specifically, rather than to the interface. */
class FakeAnatomyRendererTest {

    private val pack = PackId("skeletal-thorax")
    private val source = MeshSource("file:///packs/skeletal-thorax.glb")
    private val rib = StructureId("costa-vii")
    private val vertebra = StructureId("vertebra-c7")

    private fun style(argb: Long) = HighlightStyle(
        outlineArgb = argb.toInt(),
        outlineWidthDp = 3f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = argb.toInt(),
        fillLuminanceShift = 0f,
    )

    @Test
    fun each_structure_keeps_its_own_style() {
        // Screen 12: the expected answer and the chosen one, on the model together.
        val renderer = FakeAnatomyRenderer()
        val expected = style(0xFF57B37C)
        val chosen = style(0xFFD89B3C)

        renderer.highlight(mapOf(rib to expected, vertebra to chosen))

        assertEquals(expected, renderer.highlights[rib])
        assertEquals(chosen, renderer.highlights[vertebra])
    }

    @Test
    fun a_new_highlight_replaces_the_last_one_rather_than_adding_to_it() {
        // Review Focus 1. The renderer is a projection of what it was last told (§4).
        val renderer = FakeAnatomyRenderer()
        renderer.highlight(mapOf(rib to style(0xFF57B37C), vertebra to style(0xFFD89B3C)))

        renderer.highlight(mapOf(rib to style(0xFFD89B3C)))

        assertEquals(mapOf(rib to style(0xFFD89B3C)), renderer.highlights)
    }

    @Test
    fun an_empty_map_clears_every_highlight() {
        // Review Focus 3.
        val renderer = FakeAnatomyRenderer()
        renderer.highlight(mapOf(rib to style(0xFF57B37C)))

        renderer.highlight(emptyMap())

        assertTrue(renderer.highlights.isEmpty())
    }

    @Test
    fun the_one_style_form_gives_every_structure_that_style() {
        val renderer = FakeAnatomyRenderer()
        val selected = style(0xFFFFD3CB)

        renderer.highlight(setOf(rib, vertebra), selected)

        assertEquals(mapOf(rib to selected, vertebra to selected), renderer.highlights)
    }

    @Test
    fun holds_the_three_declared_sets_so_a_lost_surface_can_be_replayed() = runTest {
        val renderer = FakeAnatomyRenderer()
        val femur = StructureId("a02-5-04-001-femur-left")
        val tibia = StructureId("a02-5-06-001-tibia-left")

        renderer.setVisibility(setOf(tibia), visible = false)
        renderer.setOpacity(setOf(femur), alpha = 0.25f)

        assertEquals(setOf(tibia), renderer.hidden)
        assertEquals(setOf(femur), renderer.ghosted)
        assertEquals(0.25f, renderer.ghostAlpha)
    }

    @Test
    fun showing_a_structure_removes_it_from_the_hidden_set() = runTest {
        val renderer = FakeAnatomyRenderer()
        val tibia = StructureId("a02-5-06-001-tibia-left")

        renderer.setVisibility(setOf(tibia), visible = false)
        renderer.setVisibility(setOf(tibia), visible = true)

        assertEquals(emptySet(), renderer.hidden)
    }

    @Test
    fun opacity_of_one_clears_the_ghost_set() = runTest {
        val renderer = FakeAnatomyRenderer()
        val femur = StructureId("a02-5-04-001-femur-left")

        renderer.setOpacity(setOf(femur), alpha = 0.25f)
        renderer.setOpacity(setOf(femur), alpha = 1.0f)

        assertEquals(emptySet(), renderer.ghosted)
    }

    @Test
    fun un_ghosting_one_structure_leaves_the_rest_at_their_original_alpha() = runTest {
        val renderer = FakeAnatomyRenderer()
        val femur = StructureId("a02-5-04-001-femur-left")
        val fibula = StructureId("a02-5-07-001-fibula-left")

        renderer.setOpacity(setOf(femur, fibula), alpha = 0.25f)
        renderer.setOpacity(setOf(fibula), alpha = 1.0f)

        assertEquals(setOf(femur), renderer.ghosted)
        assertEquals(0.25f, renderer.ghostAlpha)
    }

    @Test
    fun tracks_loaded_packs_so_screens_can_assert_on_them_without_a_gpu() = runTest {
        val renderer = FakeAnatomyRenderer()

        renderer.loadPack(pack, source)
        assertEquals(setOf(pack), renderer.loadedPacks)

        renderer.unloadPack(pack)
        assertTrue(renderer.loadedPacks.isEmpty())
    }
}

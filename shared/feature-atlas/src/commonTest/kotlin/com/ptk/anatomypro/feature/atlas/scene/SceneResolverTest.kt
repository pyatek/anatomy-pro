package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.Isolation
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneResolverTest {

    private val skin = SystemId("regions-of-human-body")
    private val muscle = SystemId("muscular-system")
    private val bone = SystemId("skeletal-system")
    private val epigastric = StructureId("regio-epigastrica")
    private val pectoralis = StructureId("musculus-pectoralis-major")
    private val clavicle = StructureId("clavicula-left")
    private val scapula = StructureId("scapula-left")
    private val membership = mapOf(
        skin to setOf(epigastric),
        muscle to setOf(pectoralis),
        bone to setOf(clavicle, scapula),
    )

    @Test
    fun nothing_set_shows_everything() {
        assertEquals(RenderState.None, SceneResolver.resolve(emptyMap(), membership, isolation = null, ghostAlpha = 0.3f))
    }

    @Test
    fun a_hidden_system_hides_its_structures_and_a_ghosted_one_ghosts_them() {
        val state = SceneResolver.resolve(
            layers = mapOf(skin to LayerMode.Hidden, muscle to LayerMode.Ghosted, bone to LayerMode.Visible),
            membership = membership,
            isolation = null,
            ghostAlpha = 0.3f,
        )

        assertEquals(setOf(epigastric), state.hidden)
        assertEquals(setOf(pectoralis), state.ghosted)
        assertEquals(0.3f, state.ghostAlpha)
    }

    @Test
    fun isolation_overrides_the_layers_because_it_is_the_narrower_request() {
        val state = SceneResolver.resolve(
            layers = mapOf(bone to LayerMode.Hidden),
            membership = membership,
            isolation = Isolation(focus = clavicle, ghosted = setOf(scapula), hidden = setOf(epigastric, pectoralis)),
            ghostAlpha = 0.34f,
        )

        assertEquals(setOf(epigastric, pectoralis), state.hidden)
        assertEquals(setOf(scapula), state.ghosted)
        assertEquals(0.34f, state.ghostAlpha)
    }

    @Test
    fun an_isolation_without_a_focus_falls_back_to_the_layers() {
        val state = SceneResolver.resolve(
            layers = mapOf(skin to LayerMode.Hidden),
            membership = membership,
            isolation = Isolation(focus = null, ghosted = emptySet(), hidden = emptySet()),
            ghostAlpha = 0.3f,
        )

        assertEquals(setOf(epigastric), state.hidden)
    }
}

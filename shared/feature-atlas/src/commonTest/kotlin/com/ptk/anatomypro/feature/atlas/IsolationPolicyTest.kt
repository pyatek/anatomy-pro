package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.model.StructureId
import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationPolicyTest {

    private val femur = StructureId("a02-5-04-001-femur-left")
    private val tibia = StructureId("a02-5-06-001-tibia-left")
    private val fibula = StructureId("a02-5-07-001-fibula-left")
    private val scapula = StructureId("a02-4-01-001-scapula-left")
    private val everything = setOf(femur, tibia, fibula, scapula)

    @Test
    fun ghosts_the_siblings_and_hides_everything_else() {
        val isolation = IsolationPolicy.resolve(
            focus = tibia,
            siblings = setOf(femur, fibula),
            everything = everything,
        )

        assertEquals(tibia, isolation.focus)
        assertEquals(setOf(femur, fibula), isolation.ghosted)
        assertEquals(setOf(scapula), isolation.hidden)
    }

    @Test
    fun never_ghosts_or_hides_the_focus_itself() {
        val isolation = IsolationPolicy.resolve(
            focus = tibia,
            siblings = setOf(tibia, femur),
            everything = everything,
        )

        assertEquals(setOf(femur), isolation.ghosted)
        assertEquals(setOf(fibula, scapula), isolation.hidden)
    }

    @Test
    fun clearing_the_focus_shows_everything() {
        val isolation = IsolationPolicy.resolve(
            focus = null,
            siblings = emptySet(),
            everything = everything,
        )

        assertEquals(null, isolation.focus)
        assertEquals(emptySet(), isolation.ghosted)
        assertEquals(emptySet(), isolation.hidden)
    }

    @Test
    fun a_focus_with_no_siblings_hides_the_rest_and_ghosts_nothing() {
        val isolation = IsolationPolicy.resolve(
            focus = scapula,
            siblings = emptySet(),
            everything = everything,
        )

        assertEquals(emptySet(), isolation.ghosted)
        assertEquals(setOf(femur, tibia, fibula), isolation.hidden)
    }
}

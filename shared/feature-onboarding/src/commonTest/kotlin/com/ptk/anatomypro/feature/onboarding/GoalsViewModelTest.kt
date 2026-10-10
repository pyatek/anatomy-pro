package com.ptk.anatomypro.feature.onboarding

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.core.data.fake.FakeSettingsRepository
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val skeletal = SystemId("skeletal-system")
    private val muscular = SystemId("muscular-system")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nothing_is_selected_until_the_student_chooses() = runTest(dispatcher) {
        // §9: "none selected" is a state the screen must have, not a moment before loading ends.
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        assertFalse(model.state.value.isLoading)
        assertEquals(emptySet(), model.state.value.selected)
    }

    @Test
    fun the_systems_offered_are_the_ones_the_atlas_has() = runTest(dispatcher) {
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        assertEquals(setOf(skeletal, muscular), model.state.value.systems.toSet())
    }

    @Test
    fun choosing_a_system_selects_it_and_writes_it_to_settings() = runTest(dispatcher) {
        // §4.8: the choice is a preference. It is saved as it is made, not on Continue, so
        // leaving the screen by any route keeps it.
        val settings = FakeSettingsRepository()
        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("skeletal-system"), model.state.value.selected)
        assertEquals(setOf("skeletal-system"), settings.settings.first().studiedSystems)
    }

    @Test
    fun choosing_a_selected_system_again_deselects_it() = runTest(dispatcher) {
        val model = GoalsViewModel(FakeSettingsRepository(), FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        model.onToggle(muscular)
        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("muscular-system"), model.state.value.selected)
    }

    @Test
    fun an_earlier_choice_is_shown_when_the_screen_is_opened_again() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(studiedSystems = setOf("muscular-system")))

        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        assertEquals(setOf("muscular-system"), model.state.value.selected)
    }

    @Test
    fun an_atlas_that_cannot_list_its_systems_leaves_an_empty_list_not_a_spinner() = runTest(dispatcher) {
        // Review Focus 1. A broken install must not hold a new user on step two for ever.
        val broken = FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no atlas") }))

        val model = GoalsViewModel(FakeSettingsRepository(), broken)
        advanceUntilIdle()

        assertFalse(model.state.value.isLoading)
        assertTrue(model.state.value.systems.isEmpty())
    }

    @Test
    fun a_stored_system_the_atlas_no_longer_offers_is_kept_when_another_is_toggled() = runTest(dispatcher) {
        // Review Focus 5. A pack can be removed; the goal it carried is the student's, not ours to drop.
        val settings = FakeSettingsRepository(AppSettings(studiedSystems = setOf("nervous-system")))
        val model = GoalsViewModel(settings, FakeAtlasRepository())
        advanceUntilIdle()

        model.onToggle(skeletal)
        advanceUntilIdle()

        assertEquals(setOf("nervous-system", "skeletal-system"), settings.settings.first().studiedSystems)
    }
}

package com.ptk.anatomypro.feature.settings

import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class InMemorySettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemorySettings()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun the_two_languages_move_independently() = runTest(dispatcher) {
        // The point of §13: reading the interface in Polish while being examined in Latin
        // is the expected case, so changing one must never move the other.
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        model.onInterfaceLocale("en")
        advanceUntilIdle()

        assertEquals("en", model.state.value.settings.interfaceLocale)
        assertEquals("la", model.state.value.settings.examinationLocale)
    }

    @Test
    fun latin_is_always_offered_for_examination() = runTest(dispatcher) {
        // It is the canonical key every pack carries; a build with no translations must
        // still be examinable.
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        assertTrue(model.state.value.examinationOptions.any { it.code == "la" })
    }

    @Test
    fun accessibility_toggles_persist_through_the_repository() = runTest(dispatcher) {
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        model.onQuizTimer(false)
        model.onPatternsNotColour(true)
        model.onStructureTreeMode(true)
        advanceUntilIdle()

        val settings = model.state.value.settings
        assertEquals(false, settings.quizTimerEnabled)
        assertTrue(settings.patternsNotColour)
        assertTrue(settings.structureTreeMode)
    }

    @Test
    fun name_display_changes_are_observed_not_polled() = runTest(dispatcher) {
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        model.onNameDisplay(NameDisplay.LatinOnly)
        advanceUntilIdle()

        assertEquals(NameDisplay.LatinOnly, model.state.value.settings.nameDisplay)
    }

    @Test
    fun finishing_onboarding_is_a_separate_decision_from_choosing_a_language() = runTest(dispatcher) {
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        model.onInterfaceLocale("en")
        advanceUntilIdle()
        assertEquals(false, model.state.value.settings.onboarded)

        model.onOnboardingComplete()
        advanceUntilIdle()
        assertTrue(model.state.value.settings.onboarded)
    }

    @Test
    fun defaults_are_the_prototype_defaults() = runTest(dispatcher) {
        val model = SettingsViewModel(repository)
        advanceUntilIdle()

        val settings = model.state.value.settings
        assertEquals("pl", settings.interfaceLocale)
        assertEquals("la", settings.examinationLocale)
        assertEquals(NameDisplay.LatinAndDisplay, settings.nameDisplay)
    }
}

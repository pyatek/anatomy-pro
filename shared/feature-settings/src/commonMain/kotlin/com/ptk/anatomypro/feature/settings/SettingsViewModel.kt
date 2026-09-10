package com.ptk.anatomypro.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A language the interface can be read in, or the examination conducted in. */
data class LocaleOption(val code: String, val name: String, val note: String)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
) {
    val interfaceOptions: List<LocaleOption> get() = INTERFACE_LOCALES
    val examinationOptions: List<LocaleOption> get() = EXAMINATION_LOCALES

    companion object {
        /**
         * The prototype offers Polish and English with "+4 more languages" behind them.
         * Only these two are listed because only these two exist; a picker that offers a
         * language no pack can render would be a promise the content cannot keep.
         */
        val INTERFACE_LOCALES = listOf(
            LocaleOption("pl", "Polski", "Polish"),
            LocaleOption("en", "English", "English"),
        )

        /** Latin is always available: it is the canonical key every pack carries (§13). */
        val EXAMINATION_LOCALES = listOf(
            LocaleOption("la", "Latina", "Terminologia Anatomica"),
            LocaleOption("en", "English", "Answers in English"),
        )
    }
}

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.value = SettingsUiState(settings = settings, isLoading = false)
            }
        }
    }

    fun onInterfaceLocale(code: String) = edit { it.copy(interfaceLocale = code) }
    fun onExaminationLocale(code: String) = edit { it.copy(examinationLocale = code) }
    fun onNameDisplay(display: NameDisplay) = edit { it.copy(nameDisplay = display) }
    fun onQuizTimer(enabled: Boolean) = edit { it.copy(quizTimerEnabled = enabled) }
    fun onStructureTreeMode(enabled: Boolean) = edit { it.copy(structureTreeMode = enabled) }
    fun onPatternsNotColour(enabled: Boolean) = edit { it.copy(patternsNotColour = enabled) }

    /** Ends onboarding. Separate from the language setters so the choice is explicit. */
    fun onOnboardingComplete() = edit { it.copy(onboarded = true) }

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}

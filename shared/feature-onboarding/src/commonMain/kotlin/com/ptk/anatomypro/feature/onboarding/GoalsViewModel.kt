package com.ptk.anatomypro.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prototype screen 02: which systems the student is studying.
 *
 * [selected] is everything stored, including a system [systems] does not offer: the choice
 * is the student's and survives a pack being removed.
 */
data class GoalsUiState(
    val systems: List<SystemId> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

class GoalsViewModel(
    private val settings: SettingsRepository,
    private val atlas: AtlasRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // A failed listing is an empty list. The step can still be passed, which matters
            // more on a first run than explaining what went wrong with the install.
            val systems = try {
                atlas.systems()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(systems = systems, isLoading = false) }
        }
        viewModelScope.launch {
            settings.settings.collect { stored ->
                _state.update { it.copy(selected = stored.studiedSystems) }
            }
        }
    }

    /** Saved as it is made rather than on Continue, so no way of leaving the screen loses it. */
    fun onToggle(system: SystemId) {
        viewModelScope.launch {
            settings.update { current ->
                val id = system.value
                val next = if (id in current.studiedSystems) current.studiedSystems - id else current.studiedSystems + id
                current.copy(studiedSystems = next)
            }
        }
    }
}

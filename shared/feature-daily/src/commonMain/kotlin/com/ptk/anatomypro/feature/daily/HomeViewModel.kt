package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A null [streak] is not known: still loading, or the store could not be read. */
data class HomeUiState(val streak: StreakState? = null)

/**
 * Prototype screen 14's own data: the streak.
 *
 * The day's quiz is the other half of that screen and is [DailyViewModel]'s, because the
 * lobby shows the same day and the two must not disagree.
 */
class HomeViewModel(progress: ProgressRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            progress.streak
                // An unreadable streak leaves the card out; the daily is still there.
                .catch { }
                .collect { _state.value = HomeUiState(streak = it) }
        }
    }
}

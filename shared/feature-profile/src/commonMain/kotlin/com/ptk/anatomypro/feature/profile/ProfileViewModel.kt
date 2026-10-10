package com.ptk.anatomypro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.NotBuiltPackRepository
import com.ptk.anatomypro.core.data.NotBuiltProgressRepository
import com.ptk.anatomypro.core.data.model.DayActivity
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.model.StreakState
import com.ptk.anatomypro.core.data.model.SystemMastery
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.data.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Five weeks: enough to see a habit, few enough to fit one screen. */
const val CALENDAR_DAYS = 35

/** One square of the calendar. A null [activity] is a day the store said nothing about. */
data class CalendarDay(val date: LocalDate, val activity: DayActivity?)

data class ProfileUiState(
    val streak: StreakState? = null,
    val calendar: List<CalendarDay> = emptyList(),
    val mastery: List<SystemMastery> = emptyList(),
    val installedPacks: Int = 0,
    val installedBytes: Long = 0,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Prototype screen 17: the profile.
 *
 * [today] is the user's own date. The calendar is in the user's days, never UTC: it is
 * their habit, and the daily quiz's day is a different thing (all-screens spec §4.2).
 */
class ProfileViewModel(
    private val progress: ProgressRepository,
    private val packs: PackRepository,
    private val locale: String,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            // Unknown rather than zero if it cannot be read: the rest of the screen stands.
            progress.streak.catch { }.collect { streak -> _state.update { it.copy(streak = streak) } }
        }
        viewModelScope.launch {
            packs.packs.catch { }.collect { all ->
                val installed = all.filter { it.status == PackStatus.Installed }
                _state.update { it.copy(installedPacks = installed.size, installedBytes = installed.sumOf { p -> p.byteSize }) }
            }
        }
    }

    fun onRetry() {
        if (_state.value.isLoading) return
        load()
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, failed = false) }
        viewModelScope.launch {
            try {
                val end = today()
                val start = LocalDate.fromEpochDays(end.toEpochDays() - (CALENDAR_DAYS - 1))
                val activity = progress.activity(start, end)
                val mastery = progress.masteryBySystem(locale)
                val calendar = (0 until CALENDAR_DAYS).map { offset ->
                    val date = LocalDate.fromEpochDays(start.toEpochDays() + offset)
                    CalendarDay(date, activity[date])
                }
                _state.update { it.copy(calendar = calendar, mastery = mastery, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Not an empty calendar: that would say nothing had been studied.
                _state.update { it.copy(calendar = emptyList(), mastery = emptyList(), isLoading = false, failed = true) }
            }
        }
    }
}

/**
 * Whether there is a profile to show.
 *
 * Production has no ProgressRepository or PackRepository yet; the ones it is given throw on
 * first use, by design (all-screens spec §6). There the Profile tab opens settings.
 */
fun profileIsBuilt(progress: ProgressRepository, packs: PackRepository): Boolean =
    progress !is NotBuiltProgressRepository && packs !is NotBuiltPackRepository

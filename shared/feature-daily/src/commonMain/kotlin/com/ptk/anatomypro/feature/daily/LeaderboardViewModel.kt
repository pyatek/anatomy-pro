package com.ptk.anatomypro.feature.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.Leaderboard
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.LeaderboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** v1's two boards (design spec §9.3). */
enum class Board { DAILY, STREAK }

data class LeaderboardUiState(
    val subscribed: Boolean = false,
    val board: Board = Board.DAILY,
    val leaderboard: Leaderboard? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    /** The board is there and the user is not on it: they have not played, or played untimed. */
    val unranked: Boolean get() = leaderboard != null && leaderboard.me == null
}

/**
 * Prototype screen 16.
 *
 * [today] gives the date the daily board is asked for. The daily quiz's day is UTC (design
 * spec §9.1), so the caller supplies a UTC date.
 */
class LeaderboardViewModel(
    private val leaderboard: LeaderboardRepository,
    private val entitlements: EntitlementRepository,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            entitlements.entitlements.collect { current ->
                val was = _state.value.subscribed
                _state.update { it.copy(subscribed = current.subscribed) }
                when {
                    // A free user's device never asks for a board.
                    !current.subscribed -> {
                        loading?.cancel()
                        _state.update { it.copy(leaderboard = null, isLoading = false, failed = false) }
                    }
                    !was -> load()
                }
            }
        }
    }

    fun onBoard(board: Board) {
        if (board == _state.value.board && !_state.value.failed) return
        _state.update { it.copy(board = board) }
        load()
    }

    fun onRetry() = load()

    /**
     * The screen was come back to. This model outlives a tab switch, and what it holds may
     * be from before the user played today. The board on screen stays until the new one
     * arrives.
     */
    fun refresh() = load(keepShown = true)

    private fun load(keepShown: Boolean = false) {
        if (!_state.value.subscribed) return
        // The latest request wins: a board that answers late must not replace the one the
        // user has since switched to.
        loading?.cancel()
        val board = _state.value.board
        _state.update {
            val kept = it.leaderboard.takeIf { keepShown }
            it.copy(isLoading = kept == null, failed = false, leaderboard = kept)
        }
        loading = viewModelScope.launch {
            try {
                val loaded = when (board) {
                    Board.DAILY -> leaderboard.daily(today())
                    Board.STREAK -> leaderboard.streaks()
                }
                _state.update { it.copy(leaderboard = loaded, isLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }
}

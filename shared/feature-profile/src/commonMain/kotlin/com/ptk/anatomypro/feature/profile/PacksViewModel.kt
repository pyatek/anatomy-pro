package com.ptk.anatomypro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PacksUiState(
    val packs: List<PackState> = emptyList(),
    /** The pack whose deletion is waiting for a yes or a no. */
    val confirmingDelete: PackId? = null,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    val installedBytes: Long get() = packs.filter { it.status == PackStatus.Installed }.sumOf { it.byteSize }
}

/**
 * Prototype screen 19: the pack manager.
 *
 * Nothing here is ever locked: packs are free (all-screens spec §15.2). The repository owns
 * every download; this asks, and shows what the repository reports.
 */
class PacksViewModel(private val packs: PackRepository) : ViewModel() {

    private val _state = MutableStateFlow(PacksUiState())
    val state: StateFlow<PacksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                packs.packs.collect { all ->
                    _state.update { state ->
                        // A confirmation is for an installed pack. If the pack has gone, or is
                        // no longer installed, there is nothing left to say yes to.
                        val stillThere = all.any { it.id == state.confirmingDelete && it.status == PackStatus.Installed }
                        state.copy(
                            packs = all,
                            confirmingDelete = state.confirmingDelete.takeIf { stillThere },
                            isLoading = false,
                            failed = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    fun onDownload(id: PackId) = act { packs.download(id) }

    fun onCancel(id: PackId) = act { packs.cancel(id) }

    fun onDeleteRequested(id: PackId) {
        val installed = _state.value.packs.any { it.id == id && it.status == PackStatus.Installed }
        if (installed) _state.update { it.copy(confirmingDelete = id) }
    }

    fun onDeleteConfirmed() {
        val id = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null) }
        act { packs.delete(id) }
    }

    fun onDeleteDismissed() = _state.update { it.copy(confirmingDelete = null) }

    /**
     * Runs one request. A request that throws is dropped: what happened to the pack is the
     * repository's to report through its status, and an exception here is not that.
     */
    private fun act(request: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                request()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }
}

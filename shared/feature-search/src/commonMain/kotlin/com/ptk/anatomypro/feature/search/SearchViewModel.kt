package com.ptk.anatomypro.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.SearchHit
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Prototype screen 06.
 *
 * [recent] is kept in memory only. Persisting it is a preference store this project does
 * not have yet, and inventing one to hold five strings would be the wrong order of work.
 */
data class SearchUiState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val recent: List<String> = emptyList(),
    val isSearching: Boolean = false,
) {
    val hasQuery: Boolean get() = query.isNotBlank()
}

class SearchViewModel(
    private val repository: AtlasRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val debounced = queries.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()

    init {
        viewModelScope.launch {
            debounced.collect { query ->
                if (query.isBlank()) {
                    _state.value = _state.value.copy(hits = emptyList(), isSearching = false)
                    return@collect
                }
                _state.value = _state.value.copy(isSearching = true)
                val hits = runCatching { repository.search(query) }.getOrDefault(emptyList())
                _state.value = _state.value.copy(hits = hits, isSearching = false)
            }
        }
    }

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query)
        queries.value = query
    }

    /** The field's clear affordance. Named to avoid colliding with ViewModel.onCleared. */
    fun onQueryCleared() {
        onQueryChanged("")
    }

    /** Called when a result is opened, so the term is worth remembering. */
    fun onHitOpened(hit: SearchHit) {
        val term = _state.value.query.trim()
        if (term.isEmpty()) return
        _state.value = _state.value.copy(
            recent = (listOf(term) + _state.value.recent.filterNot { it.equals(term, ignoreCase = true) })
                .take(MAX_RECENT),
        )
    }

    fun onRecentSelected(term: String) = onQueryChanged(term)

    private companion object {
        /** Long enough that typing does not run a query per keystroke, short enough to feel live. */
        const val SEARCH_DEBOUNCE_MS = 200L
        const val MAX_RECENT = 5
    }
}

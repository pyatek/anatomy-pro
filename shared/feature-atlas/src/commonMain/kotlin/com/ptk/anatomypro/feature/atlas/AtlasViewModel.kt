package com.ptk.anatomypro.feature.atlas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the atlas screen's state.
 *
 * Children are fetched when a node is first expanded rather than up front: the whole
 * taxonomy is thousands of rows and almost none of it is ever looked at.
 *
 * Selection is deliberately one-directional in both senses — tapping a row and tapping the
 * model both end here, and the renderer is told what to highlight rather than asked what
 * is highlighted. That is §4's rule that Kotlin owns all state, applied one level up.
 */
class AtlasViewModel(
    private val repository: AtlasRepository,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AtlasUiState())
    val state: StateFlow<AtlasUiState> = _state.asStateFlow()

    private val childrenByParent = mutableMapOf<StructureId, List<StructureSummary>>()
    private val expanded = mutableSetOf<StructureId>()
    private var roots: List<StructureSummary> = emptyList()

    init {
        viewModelScope.launch {
            runCatching { repository.roots(locale) }
                .onSuccess {
                    roots = it
                    _state.value = AtlasUiState(rows = flatten(), isLoading = false)
                }
                .onFailure {
                    _state.value = AtlasUiState(isLoading = false, error = AtlasError.LoadFailed)
                }
        }
    }

    fun onRowToggled(id: StructureId) {
        viewModelScope.launch {
            if (id in expanded) {
                expanded -= id
            } else {
                if (id !in childrenByParent) {
                    childrenByParent[id] = repository.children(id, locale)
                }
                expanded += id
            }
            _state.value = _state.value.copy(rows = flatten())
        }
    }

    /** A row was tapped. Groups draw nothing, so selecting one highlights nothing. */
    fun onRowSelected(summary: StructureSummary) {
        _state.value = _state.value.copy(
            selected = summary.id.takeUnless { summary.isGroup },
            selectedName = summary.name,
        )
    }

    /** The model was tapped. Null is a miss, which clears the selection. */
    fun onPickedInModel(id: StructureId?) {
        viewModelScope.launch {
            val summary = id?.let { repository.summary(it, locale) }
            _state.value = _state.value.copy(
                selected = id,
                selectedName = summary?.name ?: id?.value,
            )
        }
    }

    private fun flatten(): List<AtlasRow> {
        val rows = mutableListOf<AtlasRow>()

        fun walk(nodes: List<StructureSummary>, depth: Int) {
            for (node in nodes) {
                val isExpanded = node.id in expanded
                rows += AtlasRow(node, depth, isExpanded)
                if (isExpanded) walk(childrenByParent[node.id].orEmpty(), depth + 1)
            }
        }

        walk(roots, 0)
        return rows
    }
}

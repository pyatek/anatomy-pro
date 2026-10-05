package com.ptk.anatomypro.feature.atlas.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.feature.atlas.AtlasError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen reader should say next. A kind and values; the screen picks the words. */
sealed interface TreeAnnouncement {
    data class Focused(val name: String, val position: Int, val total: Int) : TreeAnnouncement
    data class Level(val level: Int, val name: String?, val count: Int) : TreeAnnouncement
}

data class TreeUiState(
    /** From the top level down to the structure whose children are listed. Empty at the top. */
    val path: List<StructureSummary> = emptyList(),
    val items: List<StructureSummary> = emptyList(),
    val focusedIndex: Int? = null,
    val announcement: TreeAnnouncement? = null,
    val isLoading: Boolean = true,
    val error: AtlasError? = null,
) {
    /** 1 at the top, as the prototype counts it ("POZIOM 3"). */
    val level: Int get() = path.size + 1
    val focused: StructureSummary? get() = focusedIndex?.let(items::getOrNull)
}

/** Prototype screen 21: the hierarchy as a sequence of short lists (design spec §12). */
class StructureTreeViewModel(
    private val repository: AtlasRepository,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
        show(path = emptyList(), focusId = null)
    }

    fun onFocus(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        _state.value = current.copy(
            focusedIndex = index,
            announcement = TreeAnnouncement.Focused(item.name, position = index + 1, total = current.items.size),
        )
    }

    fun onEnter(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        if (!item.hasChildren) return
        show(path = current.path + item, focusId = null)
    }

    fun onUp() {
        val current = _state.value
        if (current.path.isEmpty()) return
        show(path = current.path.dropLast(1), focusId = current.path.last().id.value)
    }

    private fun show(path: List<StructureSummary>, focusId: String?) {
        viewModelScope.launch {
            runCatching {
                val parent = path.lastOrNull()
                if (parent == null) repository.roots(locale) else repository.children(parent.id, locale)
            }.onSuccess { items ->
                val focused = focusId?.let { id -> items.indexOfFirst { it.id.value == id } }?.takeIf { it >= 0 }
                _state.value = TreeUiState(
                    path = path,
                    items = items,
                    focusedIndex = focused,
                    announcement = TreeAnnouncement.Level(path.size + 1, path.lastOrNull()?.name, items.size),
                    isLoading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = AtlasError.LoadFailed)
            }
        }
    }
}

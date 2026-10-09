package com.ptk.anatomypro.feature.atlas.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.Laterality
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.AtlasError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen reader should say next. A kind and values; the screen picks the words. */
sealed interface TreeAnnouncement {
    data class Focused(val name: String, val laterality: Laterality, val position: Int, val total: Int) : TreeAnnouncement
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

/**
 * Prototype screen 21: the hierarchy as a sequence of short lists (design spec §12).
 *
 * One model per tree, whatever the interface language: [onLocale] asks for the same level
 * again in the new language. A model per language would each remember its own level, and
 * returning to a language would return to wherever that one was left.
 */
class StructureTreeViewModel(
    private val repository: AtlasRepository,
    locale: String,
    /** Ids from the top level down; lets a new model (a restored process) reopen at the same level. */
    initialPath: List<StructureId> = emptyList(),
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    private var locale: String = locale

    /**
     * Where the tree is, or is on its way to, as ids: names belong to one language, so this
     * is what a language change asks for again.
     */
    private var targetPath: List<StructureId> = initialPath
    private var targetFocus: StructureId? = null

    /** The load in flight. A newer navigation cancels it, so the last one wins. */
    private var loading: Job? = null

    init {
        reload()
    }

    /** The interface language changed: the same level and the same focused row, renamed. */
    fun onLocale(locale: String) {
        if (locale == this.locale) return
        this.locale = locale
        reload()
    }

    fun onFocus(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        targetFocus = item.id
        _state.value = current.copy(
            focusedIndex = index,
            announcement = TreeAnnouncement.Focused(
                item.name, item.laterality, position = index + 1, total = current.items.size,
            ),
        )
    }

    fun onEnter(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        if (!item.hasChildren) return
        show(path = current.path + item, focus = null)
    }

    fun onUp() {
        val current = _state.value
        if (current.path.isEmpty()) return
        show(path = current.path.dropLast(1), focus = current.path.last().id)
    }

    private fun show(path: List<StructureSummary>, focus: StructureId?) {
        targetPath = path.map { it.id }
        targetFocus = focus
        loading?.cancel()
        loading = viewModelScope.launch { load(path, focus) }
    }

    /** Loads the target again from its ids, in the current language. */
    private fun reload() {
        val ids = targetPath
        val focus = targetFocus
        loading?.cancel()
        loading = viewModelScope.launch {
            val path = try {
                ids.map { requireNotNull(repository.summary(it, locale)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A path that no longer resolves is not worth an error: start from the top.
                targetPath = emptyList()
                emptyList()
            }
            load(path, focus)
        }
    }

    private suspend fun load(path: List<StructureSummary>, focus: StructureId?) {
        val items = try {
            val parent = path.lastOrNull()
            if (parent == null) repository.roots(locale) else repository.children(parent.id, locale)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AtlasError.LoadFailed)
            return
        }
        val focused = focus?.let { id -> items.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        _state.value = TreeUiState(
            path = path,
            items = items,
            focusedIndex = focused,
            announcement = TreeAnnouncement.Level(path.size + 1, path.lastOrNull()?.name, items.size),
            isLoading = false,
        )
    }
}

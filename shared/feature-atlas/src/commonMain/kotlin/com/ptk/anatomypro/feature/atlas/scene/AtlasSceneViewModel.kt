package com.ptk.anatomypro.feature.atlas.scene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.IsolationPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEFAULT_GHOST_PERCENT = 30
const val MIN_GHOST_PERCENT = 10
const val MAX_GHOST_PERCENT = 60
const val FOCUS_DURATION_MS = 600

/**
 * The prototype lists systems from the body's surface inwards: skin, then muscle, then
 * bone. Ids the list does not know go last, alphabetically, so new content still shows.
 */
val SYSTEM_DISPLAY_ORDER: List<SystemId> = listOf(
    "regions-of-human-body", "muscular-system", "visceral-systems", "cardiovascular-system",
    "lymphoid-organs", "nervous-system-sense-organs", "joints", "skeletal-system",
).map(::SystemId)

data class LayerRow(val system: SystemId, val mode: LayerMode)

data class LayerPanelUiState(
    val rows: List<LayerRow> = emptyList(),
    val isolate: Boolean = false,
    val focus: StructureId? = null,
    val ghostPercent: Int = DEFAULT_GHOST_PERCENT,
    val isLoading: Boolean = true,
)

/**
 * What the renderer shows, for every screen that draws the model.
 *
 * App-scoped rather than per screen: a system hidden on screen 07 stays hidden on the
 * atlas, and a structure focused in tree mode (screen 21) is framed when the model is next
 * on screen. Screens only ever read [render] and [cameraFocus]; this is the one writer.
 */
class AtlasSceneViewModel(private val repository: AtlasRepository) : ViewModel() {

    private val _panel = MutableStateFlow(LayerPanelUiState())
    val panel: StateFlow<LayerPanelUiState> = _panel.asStateFlow()

    private val _render = MutableStateFlow(RenderState.None)
    val render: StateFlow<RenderState> = _render.asStateFlow()

    private val _cameraFocus = MutableStateFlow<FocusRequest?>(null)
    val cameraFocus: StateFlow<FocusRequest?> = _cameraFocus.asStateFlow()

    private var membership: Map<SystemId, Set<StructureId>> = emptyMap()
    private var everything: Set<StructureId> = emptySet()
    private var serial = 0
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            val systems = repository.systems()
            membership = systems.associateWith { repository.structuresIn(it) }
            everything = repository.allStructures()
            val ordered = systems.sortedWith(
                compareBy<SystemId>({ SYSTEM_DISPLAY_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.value }),
            )
            _panel.value = _panel.value.copy(
                rows = ordered.map { LayerRow(it, LayerMode.Visible) },
                isLoading = false,
            )
            resolve()
        }
    }

    fun setLayer(system: SystemId, mode: LayerMode) {
        _panel.value = _panel.value.copy(
            rows = _panel.value.rows.map { if (it.system == system) it.copy(mode = mode) else it },
        )
        launchResolve()
    }

    fun resetLayers() {
        _panel.value = _panel.value.copy(
            rows = _panel.value.rows.map { it.copy(mode = LayerMode.Visible) },
            isolate = false,
        )
        frameWholeModel()
        launchResolve()
    }

    fun setIsolation(enabled: Boolean) {
        val wasIsolating = _panel.value.isolate
        _panel.value = _panel.value.copy(isolate = enabled)
        val focus = _panel.value.focus
        if (enabled && focus != null) focusCamera(focus)
        // Isolation framed one structure; with it gone the camera has nothing left to be near.
        if (!enabled && wasIsolating) frameWholeModel()
        launchResolve()
    }

    fun setGhostPercent(percent: Int) {
        _panel.value = _panel.value.copy(ghostPercent = percent.coerceIn(MIN_GHOST_PERCENT, MAX_GHOST_PERCENT))
        launchResolve()
    }

    /**
     * A structure was chosen anywhere — model, tree, list. The camera stays where it is,
     * except when the selection is dropped while isolating: the isolated structure comes
     * back among everything else, and a camera left on it would show a close-up of nothing
     * in particular.
     */
    fun onStructureSelected(id: StructureId?) {
        _panel.value = _panel.value.copy(focus = id)
        if (id == null && _panel.value.isolate) frameWholeModel()
        launchResolve()
    }

    /** An explicit request to frame [id]: tree mode, or isolation turning on. */
    fun focusCamera(id: StructureId) {
        _cameraFocus.value = FocusRequest(id, FOCUS_DURATION_MS, ++serial)
    }

    /** Sends the camera back to the whole model, as it was on load. */
    private fun frameWholeModel() {
        _cameraFocus.value = FocusRequest(null, FOCUS_DURATION_MS, ++serial)
    }

    private fun launchResolve() {
        // The newest panel state always wins: a slower, older resolve must not land after it.
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        val panel = _panel.value
        val focus = panel.focus
        val isolation = if (panel.isolate && focus != null) {
            IsolationPolicy.resolve(focus, siblingsOf(focus), everything)
        } else {
            null
        }
        _render.value = SceneResolver.resolve(
            layers = panel.rows.associate { it.system to it.mode },
            membership = membership,
            isolation = isolation,
            ghostAlpha = panel.ghostPercent / 100f,
        )
    }

    /** The structures under the same parent — the neighbours isolation ghosts. */
    private suspend fun siblingsOf(id: StructureId): Set<StructureId> {
        val parent = repository.detail(id, LATIN)?.ancestors?.lastOrNull() ?: return emptySet()
        return repository.children(parent.id, LATIN).mapTo(mutableSetOf()) { it.id }
    }

    private companion object {
        const val LATIN = "la"
    }
}

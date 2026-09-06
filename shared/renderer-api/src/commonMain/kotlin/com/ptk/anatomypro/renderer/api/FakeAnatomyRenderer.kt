package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory [AnatomyRenderer] for tests and Compose previews.
 *
 * Deliberately in commonMain rather than commonTest: Kotlin Multiplatform has no working
 * equivalent of Java test fixtures, so a fake confined to a test source set could not be
 * used by other modules — which is the whole point of it (spec §15, §20.3).
 */
class FakeAnatomyRenderer(replay: Int = 64) : AnatomyRenderer {

    private val _events = MutableSharedFlow<RendererEvent>(replay = replay)
    override val events: Flow<RendererEvent> = _events.asSharedFlow()

    /** Every event emitted so far, newest last. */
    val emitted: List<RendererEvent> get() = _events.replayCache

    var loadedPacks: Set<PackId> = emptySet()
        private set
    var isolated: StructureId? = null
        private set
    var highlighted: Set<StructureId> = emptySet()
        private set
    var highlightStyle: HighlightStyle? = null
        private set
    var hiddenSystems: Set<SystemId> = emptySet()
        private set
    var cameraPose: CameraPose? = null
        private set
    var pickingEnabled: Boolean = true
        private set

    override suspend fun loadPack(pack: PackId, source: MeshSource) {
        if (loadedPacks.isEmpty()) _events.emit(RendererEvent.Ready)
        loadedPacks = loadedPacks + pack
        _events.emit(RendererEvent.PackLoaded(pack))
    }

    override suspend fun unloadPack(pack: PackId) {
        loadedPacks = loadedPacks - pack
    }

    override fun setSystemVisibility(system: SystemId, visible: Boolean) {
        hiddenSystems = if (visible) hiddenSystems - system else hiddenSystems + system
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) = Unit

    override fun isolate(structure: StructureId?, ghostNeighbours: Boolean) {
        isolated = structure
    }

    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        highlighted = structures
        highlightStyle = style
    }

    override fun focusCamera(structure: StructureId, durationMs: Int) = Unit

    override fun setCameraPose(pose: CameraPose) {
        cameraPose = pose
    }

    override fun setPickingEnabled(enabled: Boolean) {
        pickingEnabled = enabled
    }

    /** Test hook: simulate the user tapping [structure], or empty space when null. */
    suspend fun emitPick(structure: StructureId?) {
        _events.emit(RendererEvent.Picked(structure))
    }
}

package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
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
    /** What is highlighted, and how: exactly the last map [highlight] was given. */
    var highlights: Map<StructureId, HighlightStyle> = emptyMap()
        private set
    var cameraPose: CameraPose? = null
        private set

    /** The structure the camera was last asked to frame, or null once it frames the whole model. */
    var focused: StructureId? = null
        private set
    var pickingEnabled: Boolean = true
        private set
    var hidden: Set<StructureId> = emptySet()
        private set
    var ghosted: Set<StructureId> = emptySet()
        private set
    var ghostAlpha: Float = 1f
        private set

    override suspend fun loadPack(pack: PackId, source: MeshSource) {
        if (loadedPacks.isEmpty()) _events.emit(RendererEvent.Ready)
        loadedPacks = loadedPacks + pack
        _events.emit(RendererEvent.PackLoaded(pack))
    }

    override suspend fun unloadPack(pack: PackId) {
        if (pack !in loadedPacks) return
        loadedPacks = loadedPacks - pack
        _events.emit(RendererEvent.PackUnloaded(pack))
    }

    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        hidden = if (visible) hidden - structures else hidden + structures
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        ghosted = if (alpha >= 1f) ghosted - structures else ghosted + structures
        if (alpha < 1f) ghostAlpha = alpha
    }

    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlights = styles.toMap()
    }

    override fun focusCamera(structure: StructureId, durationMs: Int) {
        focused = structure
    }

    override fun frameAll(durationMs: Int) {
        focused = null
    }

    override fun setCameraPose(pose: CameraPose) {
        cameraPose = pose
    }

    override fun setPickingEnabled(enabled: Boolean) {
        pickingEnabled = enabled
    }

    /**
     * Test hook: simulate the user tapping [structure], or empty space when null.
     *
     * Honours [pickingEnabled] and [hidden] so the fake reports picks under exactly the
     * conditions a real renderer does — otherwise a screen could pass its tests against
     * behaviour the device would never produce.
     *
     * The two cases are not the same shape: picking disabled means the input never reached
     * the renderer, so nothing is emitted at all; a hidden structure means the renderer's own
     * picking pass ran and found nothing there, exactly as it would on a GPU where a hidden
     * renderable is excluded from that pass — so this still emits a miss.
     */
    suspend fun emitPick(structure: StructureId?) {
        if (!pickingEnabled) return
        val reported = if (structure in hidden) null else structure
        _events.emit(RendererEvent.Picked(reported))
    }
}

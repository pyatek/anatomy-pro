package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow

/**
 * Draws and picks geometry. Nothing else.
 *
 * Kotlin owns all state; the renderer is a pure projection of it and holds no truth the
 * app cannot reconstruct. That is what lets a lost render surface be recovered by replaying
 * app state, and what makes a three.js implementation a drop-in fallback (spec §4, §14).
 */
interface AnatomyRenderer {
    /**
     * Recent events, replayed to late subscribers.
     *
     * Replay is part of the contract rather than a convenience: a screen that subscribes
     * after a pack has begun loading must still learn that it loaded, and the same
     * property is what lets app state be rebuilt after a lost surface.
     */
    val events: Flow<RendererEvent>

    suspend fun loadPack(pack: PackId, source: MeshSource)
    suspend fun unloadPack(pack: PackId)

    fun setSystemVisibility(system: SystemId, visible: Boolean)
    fun setOpacity(structures: Set<StructureId>, alpha: Float)
    fun isolate(structure: StructureId?, ghostNeighbours: Boolean)
    fun highlight(structures: Set<StructureId>, style: HighlightStyle)

    fun focusCamera(structure: StructureId, durationMs: Int)
    fun setCameraPose(pose: CameraPose)

    fun setPickingEnabled(enabled: Boolean)
}

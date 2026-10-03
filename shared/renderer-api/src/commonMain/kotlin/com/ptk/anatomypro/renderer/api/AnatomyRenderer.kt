package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
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

    /**
     * Shows or hides structures outright.
     *
     * Replaces both `setSystemVisibility` and `isolate`, which named domain concepts the
     * renderer cannot see: a system's membership and §25.1's parent groups live in
     * `core-data`, so resolving either to a set of structures is the app's job and doing
     * it here would put policy in the one module §4 reserves for geometry.
     *
     * A hidden structure is not drawn and **is not picked** — a peeled-away layer must not
     * be able to answer a quiz question.
     */
    fun setVisibility(structures: Set<StructureId>, visible: Boolean)

    /**
     * Ghosts structures at [alpha], or returns them to fully opaque at 1.0.
     *
     * A ghost is a uniform pale shell rather than a faded copy of each structure's own
     * colour — see §26.3 — so every ghosted primitive shares one material instance and the
     * size of [structures] costs nothing on the CPU. The same sharing cuts the other way:
     * there is only one alpha, not one per structure, so the most recent sub-1.0 [alpha]
     * applies to the *entire* ghosted set, not just [structures]. `setOpacity({a}, 0.25f)`
     * followed by `setOpacity({b}, 0.5f)` leaves both `a` and `b` at 0.5.
     */
    fun setOpacity(structures: Set<StructureId>, alpha: Float)

    fun highlight(structures: Set<StructureId>, style: HighlightStyle)

    fun focusCamera(structure: StructureId, durationMs: Int)

    /**
     * Frames the whole loaded model, as on load, moving there over [durationMs].
     *
     * The way back from [focusCamera]: without it, a camera that framed one structure stays
     * there after the app has stopped being about that structure — a reset, isolation
     * turning off. Zero or less lands at once.
     */
    fun frameAll(durationMs: Int)
    fun setCameraPose(pose: CameraPose)

    fun setPickingEnabled(enabled: Boolean)
}

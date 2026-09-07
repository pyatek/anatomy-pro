package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId

sealed interface RendererEvent {
    data object Ready : RendererEvent
    data class Picked(val structure: StructureId?) : RendererEvent
    data class PackLoaded(val pack: PackId) : RendererEvent
    /**
     * Symmetric with [PackLoaded]. Without it, unloading has no observable effect on the
     * interface at all, and §15's contract test for unload has nothing to assert against.
     */
    data class PackUnloaded(val pack: PackId) : RendererEvent
    data class LoadProgress(val pack: PackId, val fraction: Float) : RendererEvent
    data class MemoryPressure(val residentBytes: Long) : RendererEvent
    data class Error(val code: String, val message: String) : RendererEvent
}

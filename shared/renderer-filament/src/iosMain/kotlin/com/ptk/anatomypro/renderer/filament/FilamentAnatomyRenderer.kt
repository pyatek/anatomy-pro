package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.StructureNode
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.CameraPose
import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_ERROR
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_LOAD_PROGRESS
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_MEMORY_PRESSURE
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_MODEL_LOADED
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_MODEL_UNLOADED
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_PICKED
import com.ptk.anatomypro.renderer.filament.cinterop.AR_EVENT_READY
import com.ptk.anatomypro.renderer.filament.cinterop.ar_attach_headless
import com.ptk.anatomypro.renderer.filament.cinterop.ar_attach_layer
import com.ptk.anatomypro.renderer.filament.cinterop.ar_clear_hidden
import com.ptk.anatomypro.renderer.filament.cinterop.ar_clear_highlight
import com.ptk.anatomypro.renderer.filament.cinterop.ar_clear_opacity
import com.ptk.anatomypro.renderer.filament.cinterop.ar_create
import com.ptk.anatomypro.renderer.filament.cinterop.ar_destroy
import com.ptk.anatomypro.renderer.filament.cinterop.ar_gpu_frame_nanos
import com.ptk.anatomypro.renderer.filament.cinterop.ar_event
import com.ptk.anatomypro.renderer.filament.cinterop.ar_load_model
import com.ptk.anatomypro.renderer.filament.cinterop.ar_node_count
import com.ptk.anatomypro.renderer.filament.cinterop.ar_node_name_at
import com.ptk.anatomypro.renderer.filament.cinterop.ar_pick_at
import com.ptk.anatomypro.renderer.filament.cinterop.ar_poll_event
import com.ptk.anatomypro.renderer.filament.cinterop.ar_render_frame
import com.ptk.anatomypro.renderer.filament.cinterop.ar_renderer_ref
import com.ptk.anatomypro.renderer.filament.cinterop.ar_set_highlight
import com.ptk.anatomypro.renderer.filament.cinterop.ar_set_hidden
import com.ptk.anatomypro.renderer.filament.cinterop.ar_set_opacity
import com.ptk.anatomypro.renderer.filament.cinterop.ar_set_picking_enabled
import com.ptk.anatomypro.renderer.filament.cinterop.ar_unload_model
import com.ptk.anatomypro.renderer.filament.cinterop.ar_wait_for_gpu
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.Flow
import platform.QuartzCore.CAMetalLayer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The iOS [AnatomyRenderer], drawn by Filament through the C seam in `ios-renderer`.
 *
 * Frames are pumped by the caller rather than by a coroutine this class owns. That keeps
 * the whole thing single-threaded from Kotlin's point of view: the app drives it from a
 * display link, tests drive it a frame at a time, and no Filament callback ever has to
 * reach into Kotlin from the backend thread — [renderFrame] drains the shim's own queue
 * instead.
 *
 * Phase 0 implements load, unload, highlight, and picking (spec §15). The rest of the
 * interface throws, naming the phase that fills it in.
 */
@OptIn(ExperimentalForeignApi::class)
class FilamentAnatomyRenderer : AnatomyRenderer {

    private val handle: ar_renderer_ref = requireNotNull(ar_create()) {
        "Filament host could not be created"
    }

    private val _events = MutableSharedFlow<RendererEvent>(replay = 64, extraBufferCapacity = 64)
    override val events: Flow<RendererEvent> = _events.asSharedFlow()

    private var loadedPack: PackId? = null
    private var nodesByStructure: Map<StructureId, List<String>> = emptyMap()
    private var nodeToStructure: Map<String, StructureId> = emptyMap()
    private var lastUnloaded: PackId? = null
    private var disposed = false

    /**
     * The hidden set is held here, not in the shim, because the interface is declarative
     * per call — `setVisibility(x, false)` then `setVisibility(y, false)` must hide both —
     * while the C seam takes a whole set. Kotlin owning it is also what §4 requires.
     */
    private val hidden = mutableSetOf<StructureId>()
    private val ghosted = mutableSetOf<StructureId>()

    /**
     * The alpha of the last partial ghost, mirroring `FakeAnatomyRenderer.ghostAlpha`.
     *
     * There is one shared blended material instance per §26.3, so the survivors of [ghosted]
     * must keep wearing whatever alpha put them there rather than whatever alpha the most
     * recent call happened to carry — otherwise un-ghosting one structure would re-send the
     * rest at `alpha = 1.0` and strand them fully opaque but still on the ghost material,
     * wearing its neutral tint instead of their own colour.
     */
    private var ghostAlpha = 1f

    /** Draws offscreen. Used by contract tests, which have no window. */
    fun attachHeadless(width: Int, height: Int) {
        ar_attach_headless(handle, width.toUInt(), height.toUInt())
        drain()
    }

    /** Draws into a `CAMetalLayer` owned by the host app. */
    fun attachLayer(layer: CAMetalLayer, width: Int, height: Int, refreshHz: Float) {
        ar_attach_layer(
            handle,
            interpretCPointer<CPointed>(layer.objcPtr()),
            width.toUInt(),
            height.toUInt(),
            refreshHz,
        )
        drain()
    }

    /**
     * Renders one frame and publishes whatever the shim queued while doing so.
     *
     * [frameTimeNanos] must be the display's vsync timestamp when drawing to a layer;
     * see the note on `ar_render_frame`. Zero is correct for offscreen rendering only.
     *
     * Returns false when Filament skipped the frame because too many were already in
     * flight, which a caller that needs frames to land can use to pace itself.
     */
    fun renderFrame(frameTimeNanos: Long = 0L): Boolean {
        val rendered = ar_render_frame(handle, frameTimeNanos.toULong())
        drain()
        return rendered
    }

    /**
     * The GPU's own time for the most recent frame, in milliseconds, or 0 when unknown.
     *
     * A frame rate on its own cannot tell being GPU-bound apart from being paced against
     * the wrong refresh rate; this can.
     */
    val gpuFrameMillis: Float get() = ar_gpu_frame_nanos(handle) / 1_000_000f

    /** How many distinct structures the loaded pack resolved to. */
    val loadedStructureCount: Int get() = nodesByStructure.size

    /** Test-only: blocks until the GPU has caught up, so picking results are deterministic. */
    fun waitForGpu() {
        ar_wait_for_gpu(handle)
        drain()
    }

    /** Reports a tap in view coordinates, origin top-left. Result arrives as [RendererEvent.Picked]. */
    fun pickAt(xPx: Float, yPx: Float) {
        ar_pick_at(handle, xPx, yPx)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        ar_destroy(handle)
    }

    override suspend fun loadPack(pack: PackId, source: MeshSource) {
        // ar_load_model calls releaseAsset on the C side first, which clears the shim's own
        // hidden/ghosted/highlight sets (see AnatomyRenderer.mm). Kotlin's copies have to be
        // cleared in step, or a set carried over from the previous pack would be stale: the
        // next unrelated setVisibility/setOpacity call would re-send it and hide or ghost
        // nodes nobody asked to touch in the newly loaded pack.
        forgetPerPackState()
        ar_load_model(handle, source.uri)
        loadedPack = pack
        indexNodes()
        drain()
    }

    override suspend fun unloadPack(pack: PackId) {
        if (loadedPack != pack) return
        lastUnloaded = pack
        forgetPerPackState()
        ar_unload_model(handle, null)
        loadedPack = null
        nodesByStructure = emptyMap()
        nodeToStructure = emptyMap()
        drain()
    }

    /** Clears the state that belongs to one loaded pack and cannot outlive it. */
    private fun forgetPerPackState() {
        hidden.clear()
        ghosted.clear()
        ghostAlpha = 1f
    }

    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        val nodes = structures.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) {
            ar_clear_highlight(handle)
            drain()
            return
        }
        passNodes(nodes) { names, count ->
            ar_set_highlight(handle, names, count, style.outlineArgb, style.fillLuminanceShift)
        }
        drain()
    }

    override fun setPickingEnabled(enabled: Boolean) {
        ar_set_picking_enabled(handle, enabled)
    }

    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        if (visible) hidden -= structures else hidden += structures
        val nodes = hidden.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) ar_clear_hidden(handle) else passNodes(nodes) { names, count ->
            ar_set_hidden(handle, names, count)
        }
        drain()
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        if (alpha >= 1f) ghosted -= structures else {
            ghosted += structures
            ghostAlpha = alpha
        }
        val nodes = ghosted.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) ar_clear_opacity(handle) else passNodes(nodes) { names, count ->
            ar_set_opacity(handle, names, count, ghostAlpha)
        }
        drain()
    }

    /** Marshals node names into a C array valid for the duration of [block]. */
    private inline fun passNodes(
        nodes: List<String>,
        block: (CArrayPointer<CPointerVar<ByteVar>>, ULong) -> Unit,
    ) = memScoped {
        val names = allocArray<CPointerVar<ByteVar>>(nodes.size)
        nodes.forEachIndexed { index, name -> names[index] = name.cstr.getPointer(this) }
        block(names, nodes.size.toULong())
    }

    override fun focusCamera(structure: StructureId, durationMs: Int): Unit =
        TODO("Phase 1: camera animation; Phase 0 frames the whole asset on load")

    override fun setCameraPose(pose: CameraPose): Unit =
        TODO("Phase 1: camera control; Phase 0 frames the whole asset on load")

    /**
     * Builds the structure index straight from the asset's node names.
     *
     * This is what lets picking resolve to a [StructureId] before `core-data` exists: the
     * sourcing convention carries enough to identify a structure on its own (spec §20.2).
     * A node whose name does not parse is indexed by name only and can never be picked as
     * a structure, so a naming mistake in the pipeline shows up as a miss, not as the
     * wrong answer.
     */
    private fun indexNodes() {
        val byStructure = mutableMapOf<StructureId, MutableList<String>>()
        val byNode = mutableMapOf<String, StructureId>()

        val count = ar_node_count(handle).toInt()
        for (index in 0 until count) {
            val name = ar_node_name_at(handle, index.toULong())?.toKString() ?: continue
            val node = StructureNode.parse(name) ?: continue
            byStructure.getOrPut(node.structure) { mutableListOf() }.add(name)
            byNode[name] = node.structure
        }

        nodesByStructure = byStructure
        nodeToStructure = byNode
    }

    /** Moves everything the shim has queued into [events]. */
    private fun drain() = memScoped {
        val event = alloc<ar_event>()
        while (ar_poll_event(handle, event.ptr)) {
            translate(event)?.let(_events::tryEmit)
        }
    }

    private fun translate(event: ar_event): RendererEvent? = when (event.type) {
        AR_EVENT_READY -> RendererEvent.Ready
        AR_EVENT_MODEL_LOADED -> loadedPack?.let(RendererEvent::PackLoaded)
        AR_EVENT_MODEL_UNLOADED -> lastUnloaded?.let(RendererEvent::PackUnloaded)
        AR_EVENT_LOAD_PROGRESS -> loadedPack?.let { RendererEvent.LoadProgress(it, event.fraction) }
        AR_EVENT_PICKED -> RendererEvent.Picked(
            event.node_name?.toKString()?.let { nodeToStructure[it] },
        )
        AR_EVENT_MEMORY_PRESSURE -> RendererEvent.MemoryPressure(event.resident_bytes)
        AR_EVENT_ERROR -> RendererEvent.Error(
            code = event.code?.toKString() ?: "unknown",
            message = event.message?.toKString() ?: "",
        )
        else -> null
    }

    init {
        drain()
    }
}

package com.ptk.anatomypro.renderer.filament

import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.StructureNode
import com.ptk.anatomypro.renderer.api.AnatomyRenderer
import com.ptk.anatomypro.renderer.api.CameraPose
import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.math.tan

/**
 * The Android [AnatomyRenderer], drawn by Filament through Google's Java bindings.
 *
 * Deliberately a mirror of the iOS implementation rather than a different design: the same
 * engine version, the same gltfio asset loading, the same `View.pick`, and the same
 * node-name index. §15's contract tests are meant to prove identical behaviour on both
 * platforms, and the cheapest way to make that true is to make the two implementations
 * differ only where the language forces them to.
 *
 * As on iOS, frames are pumped by the caller and events are published from that pump, so
 * the renderer owns no thread and stays the passive projection §4 requires.
 */
class FilamentAnatomyRenderer : AnatomyRenderer {

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val cameraEntity: Int
    private val sunEntity: Int
    private val materialProvider: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader

    private var swapChain: SwapChain? = null
    private var asset: FilamentAsset? = null
    private var width = 0
    private var height = 0

    private var loadedPack: PackId? = null
    private var nodesByStructure: Map<StructureId, List<String>> = emptyMap()
    private var entityToStructure: Map<Int, StructureId> = emptyMap()
    private var pickingEnabled = true
    private var disposed = false
    private var shot: CameraShot? = null
    private var flight: CameraFlight? = null

    /** Swapped-out material instances, so highlighting is exactly reversible. */
    private val swapped = mutableListOf<Triple<Int, Int, MaterialInstance>>()
    private val highlightInstances = mutableListOf<MaterialInstance>()

    private val hidden = mutableSetOf<StructureId>()
    private val ghosted = mutableSetOf<StructureId>()
    private val highlighted = mutableSetOf<StructureId>()
    private var ghostAlpha = 1f
    private var highlightStyle: HighlightStyle? = null
    private var entitiesByStructure: Map<StructureId, IntArray> = emptyMap()

    private val _events = MutableSharedFlow<RendererEvent>(replay = 64, extraBufferCapacity = 64)
    override val events: Flow<RendererEvent> = _events.asSharedFlow()

    private var ghostMaterial: MaterialInstance? = null

    /**
     * The one blended instance every ghosted primitive shares.
     *
     * Filament's `MaterialInstance` has setters and no getters, so a per-structure ghost
     * could not read the colour it was meant to fade. A uniform shell needs no such read,
     * costs one instance however large the ghosted set is, and reads better (§26.3).
     */
    private fun ghostMaterial(): MaterialInstance? {
        ghostMaterial?.let { return it }
        val key = MaterialProvider.MaterialKey().apply {
            alphaMode = 2 // BLEND
            doubleSided = false
            unlit = false
        }
        val uvmap = IntArray(8)
        key.constrainMaterial(uvmap)
        val created = materialProvider.createMaterialInstance(key, uvmap, "ghost", null) ?: return null
        // The ubershader defaults to fully metallic, which renders a translucent shell as
        // a dark smear. Both must be set explicitly.
        if (created.material.hasParameter("metallicFactor")) created.setParameter("metallicFactor", 0f)
        if (created.material.hasParameter("roughnessFactor")) created.setParameter("roughnessFactor", 0.8f)
        ghostMaterial = created
        return created
    }

    private companion object {
        /** Layer 0 is drawn and picked; layer 1 is neither. */
        const val LAYER_VISIBLE = 0x1
        const val LAYER_HIDDEN = 0x2
        const val LAYER_MASK = LAYER_VISIBLE or LAYER_HIDDEN

        val NO_ENTITIES = IntArray(0)

        /** A neutral bone-pale shell — deliberately not the structure's own colour (§26.3). */
        const val GHOST_RED = 0.82f
        const val GHOST_GREEN = 0.80f
        const val GHOST_BLUE = 0.78f
    }

    /** Picking results are delivered straight onto the caller's thread, as on iOS. */
    private val inlineExecutor = Executor { it.run() }

    init {
        Filament.init()
        Gltfio.init()

        engine = Engine.create()
        renderer = engine.createRenderer()
        // Filament does not clear the swap chain unless asked: with no skybox, a pixel the
        // model does not cover keeps whatever an earlier frame left there. Standing still
        // that is invisible; once the camera moves or structures are hidden, every earlier
        // frame shows through around the model — an isolated sternum sat on top of the
        // ribcage that had just been hidden.
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        }
        scene = engine.createScene()
        view = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)

        view.scene = scene
        view.camera = camera
        // Post-processing stays on: it carries tone mapping, and without it the linear
        // HDR values a physically-lit scene produces blow out to white. Picking uses its
        // own pass and is unaffected — the contract tests cover that.
        view.isPostProcessingEnabled = true
        view.setVisibleLayers(LAYER_MASK, LAYER_VISIBLE)
        // A ghost is context the learner can still tap, and Filament disables transparent
        // picking by default — without this, swapping a primitive to the blended material
        // silently removes it from `pick`, making ghosted and hidden indistinguishable to a
        // tap (§26.3). The cost is one extra depth pass.
        view.setTransparentPickingEnabled(true)

        sunEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100_000.0f)
            .direction(0.4f, -1.0f, -0.8f)
            .castShadows(false)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)

        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
    }

    /** Draws offscreen. Used by contract tests, which have no window. */
    fun attachHeadless(width: Int, height: Int) {
        // Offscreen rendering has no display to pace against; left at the default,
        // Filament drops most frames of a tight loop and a picking readback never lands.
        renderer.setDisplayInfo(Renderer.DisplayInfo().apply { refreshRate = 0.0f })
        releaseSwapChain()
        configureSurface(engine.createSwapChain(width, height, 0L), width, height)
    }

    /**
     * Draws into a `Surface` owned by the host — a SurfaceView or a TextureView.
     *
     * [refreshHz] must be the display's actual refresh rate. Filament paces against it, so
     * leaving it at the 60 Hz default on a 120 Hz panel silently caps the frame rate at a
     * fraction of what the hardware can do, and the result looks like a rendering cost.
     */
    fun attachSurface(surface: Any, width: Int, height: Int, refreshHz: Float) {
        renderer.setDisplayInfo(
            Renderer.DisplayInfo().apply { refreshRate = if (refreshHz > 0f) refreshHz else 60.0f }
        )
        releaseSwapChain()
        configureSurface(engine.createSwapChain(surface), width, height)
    }

    /**
     * Lets go of the current swap chain, and waits until the backend really has.
     *
     * A window takes one producer at a time, and Filament only queues the destroy. When the
     * host resizes its surface it hands over the same window again, so a new swap chain made
     * before the old one is gone fails (`eglCreateWindowSurface`, `EGL_BAD_ALLOC`) and the
     * model goes on being drawn at the old size, off-centre and cropped.
     */
    private fun releaseSwapChain() {
        val current = swapChain ?: return
        swapChain = null
        engine.destroySwapChain(current)
        engine.flushAndWait()
    }

    private fun configureSurface(next: SwapChain, width: Int, height: Int) {
        swapChain = next
        this.width = width
        this.height = height
        view.viewport = Viewport(0, 0, width, height)
        fitCameraToSurface()
        _events.tryEmit(RendererEvent.Ready)
    }

    /**
     * Renders one frame.
     *
     * [frameTimeNanos] must be the vsync timestamp from `Choreographer` when drawing to a
     * real surface. Filament paces against it, and an arbitrary clock reading makes it
     * refuse every frame after the first — the default is for headless rendering, where
     * pacing is switched off entirely.
     *
     * Returns false when Filament skipped the frame, which a caller that needs frames to
     * land — a test waiting on a picking readback — can use to pace itself.
     */
    fun renderFrame(frameTimeNanos: Long = System.nanoTime()): Boolean {
        val chain = swapChain ?: return false
        stepCamera(frameTimeNanos)
        if (!renderer.beginFrame(chain, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    private val frameInfoHistory = Array(8) { Renderer.FrameInfo() }

    /**
     * The GPU's own time for the most recent frame, in milliseconds, or 0 when unknown.
     *
     * A frame rate on its own cannot tell being GPU-bound apart from being paced against
     * the wrong refresh rate; this can.
     */
    val gpuFrameMillis: Float
        get() {
            val count = renderer.getFrameInfoHistory(frameInfoHistory)
            for (index in count - 1 downTo 0) {
                val nanos = frameInfoHistory[index].denoisedGpuFrameDuration
                if (nanos > 0) return nanos / 1_000_000f
            }
            return 0f
        }

    /** How many distinct structures the loaded pack resolved to. */
    val loadedStructureCount: Int get() = nodesByStructure.size

    /** Test-only: blocks until the GPU has caught up, so picking results are deterministic. */
    fun waitForGpu() = engine.flushAndWait()

    /** Reports a tap in view coordinates, origin top-left. */
    fun pickAt(xPx: Float, yPx: Float) {
        if (!pickingEnabled) return
        // Filament's picking viewport has its origin at the bottom left; callers work in
        // touch coordinates, whose origin is top left.
        val x = xPx.toInt().coerceAtLeast(0)
        val y = (height - yPx).toInt().coerceAtLeast(0)
        view.pick(x, y, inlineExecutor) { result ->
            _events.tryEmit(RendererEvent.Picked(entityToStructure[result.renderable]))
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        releaseAsset()
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroy()
        scene.removeEntity(sunEntity)
        engine.destroyEntity(sunEntity)
        EntityManager.get().destroy(sunEntity)
        swapChain?.let(engine::destroySwapChain)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    override suspend fun loadPack(pack: PackId, source: MeshSource) {
        // Unconditionally, as iOS does on every load: releaseAsset returns early when nothing
        // is loaded, so sets declared before the first pack would otherwise outlive it and be
        // applied to structures of a pack they were never meant for.
        hidden.clear()
        ghosted.clear()
        highlighted.clear()
        releaseAsset()

        val file = File(source.uri.removePrefix("file://"))
        if (!file.isFile) {
            _events.tryEmit(RendererEvent.Error("model-not-found", "No file at ${file.path}"))
            return
        }
        val bytes = file.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }

        val loaded = assetLoader.createAsset(buffer)
        if (loaded == null) {
            _events.tryEmit(RendererEvent.Error("model-invalid", "glTF could not be parsed"))
            return
        }
        resourceLoader.loadResources(loaded)
        loaded.releaseSourceData()

        asset = loaded
        loadedPack = pack
        indexNodes(loaded)
        scene.addEntities(loaded.entities)
        frameAsset()

        _events.tryEmit(RendererEvent.PackLoaded(pack))
    }

    override suspend fun unloadPack(pack: PackId) {
        if (loadedPack != pack) return
        releaseAsset()
        loadedPack = null
        _events.tryEmit(RendererEvent.PackUnloaded(pack))
    }

    override fun setPickingEnabled(enabled: Boolean) {
        pickingEnabled = enabled
    }

    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        if (visible) hidden -= structures else hidden += structures
        applyAppearance()
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        // Hold the alpha rather than taking the incoming one on every call. A caller that
        // un-ghosts one structure of several passes alpha = 1f, and the survivors must keep
        // the alpha they were ghosted at — on iOS, taking it unconditionally re-sent 1.0 for
        // structures the caller never named, leaving them opaque in the neutral ghost tint
        // with their own colour gone. Same hazard here; the fix is the same.
        if (alpha >= 1f) {
            ghosted -= structures
        } else {
            ghosted += structures
            ghostAlpha = alpha
        }
        applyAppearance()
    }

    // Interim: one style for the whole map. Replaced when applyAppearance takes a style
    // per structure.
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlighted.clear()
        highlighted += styles.keys
        highlightStyle = styles.values.firstOrNull()
        applyAppearance()
    }

    /**
     * Resolves the three declared sets to one state per primitive, and applies it.
     *
     * Clear-and-reapply rather than a diff: the ghosted set is bounded by design (§26.2),
     * so a diff would be machinery bought before anything needs it. Highlight beats ghost —
     * a structure the app is pointing at is not also faded out.
     *
     * Both loops read a primitive's original material before this pass installs anything.
     * That ordering is load-bearing: on iOS the same loops recorded an already-installed
     * override as the "original" when a set contained the same node twice, which left the
     * ghost permanently installed and, in the highlight path, a freed material instance on a
     * renderable. Kotlin's `Set<StructureId>` de-duplicates structures for free, so the
     * iOS de-duplication has no analogue here — but if this ever iterates node names or a
     * list instead, the hazard returns.
     */
    private fun applyAppearance() {
        val current = asset ?: return
        val renderables = engine.renderableManager

        for ((entity, primitive, original) in swapped) {
            val instance = renderables.getInstance(entity)
            if (instance != 0) renderables.setMaterialInstanceAt(instance, primitive, original)
        }
        swapped.clear()
        highlightInstances.forEach(engine::destroyMaterialInstance)
        highlightInstances.clear()

        for (entity in current.entities) {
            val instance = renderables.getInstance(entity)
            if (instance != 0) renderables.setLayerMask(instance, LAYER_MASK, LAYER_VISIBLE)
        }
        for (structure in hidden) {
            for (entity in (entitiesByStructure[structure] ?: NO_ENTITIES)) {
                val instance = renderables.getInstance(entity)
                if (instance != 0) renderables.setLayerMask(instance, LAYER_MASK, LAYER_HIDDEN)
            }
        }

        val ghost = if (ghosted.isEmpty()) null else ghostMaterial()
        if (ghost != null) {
            ghost.setParameter("baseColorFactor", GHOST_RED, GHOST_GREEN, GHOST_BLUE, ghostAlpha)
            for (structure in ghosted - highlighted) {
                for (entity in (entitiesByStructure[structure] ?: NO_ENTITIES)) {
                    val instance = renderables.getInstance(entity)
                    if (instance == 0) continue
                    for (primitive in 0 until renderables.getPrimitiveCount(instance)) {
                        val original = renderables.getMaterialInstanceAt(instance, primitive) ?: continue
                        swapped += Triple(entity, primitive, original)
                        renderables.setMaterialInstanceAt(instance, primitive, ghost)
                    }
                }
            }
        }

        val style = highlightStyle
        if (style != null && highlighted.isNotEmpty()) {
            val alpha = ((style.outlineArgb ushr 24) and 0xFF) / 255f
            val red = ((style.outlineArgb shr 16) and 0xFF) / 255f
            val green = ((style.outlineArgb shr 8) and 0xFF) / 255f
            val blue = (style.outlineArgb and 0xFF) / 255f
            val lift = style.fillLuminanceShift.coerceAtLeast(0f)
            for (structure in highlighted) {
                for (entity in (entitiesByStructure[structure] ?: NO_ENTITIES)) {
                    val instance = renderables.getInstance(entity)
                    if (instance == 0) continue
                    for (primitive in 0 until renderables.getPrimitiveCount(instance)) {
                        val original = renderables.getMaterialInstanceAt(instance, primitive) ?: continue
                        val replacement = MaterialInstance.duplicate(original, null)
                        val material = original.material
                        if (material.hasParameter("baseColorFactor")) {
                            replacement.setParameter("baseColorFactor", red, green, blue, alpha)
                        }
                        if (material.hasParameter("emissiveFactor")) {
                            replacement.setParameter("emissiveFactor", red * lift, green * lift, blue * lift)
                        }
                        swapped += Triple(entity, primitive, original)
                        highlightInstances += replacement
                        renderables.setMaterialInstanceAt(instance, primitive, replacement)
                    }
                }
            }
        }
    }

    override fun focusCamera(structure: StructureId, durationMs: Int) {
        val nodes = nodesByStructure[structure] ?: return // a group draws nothing to frame
        val box = nodeBounds(nodes) ?: return
        val to = CameraFraming.frame(box)
        val from = shot ?: assetBounds()?.let(CameraFraming::frame) ?: to
        flight = CameraFlight(from, to, durationMs * 1_000_000L)
        if (durationMs <= 0) stepCamera(0L)
    }

    override fun frameAll(durationMs: Int) {
        val to = assetBounds()?.let(CameraFraming::frame) ?: return // nothing loaded to frame
        flight = CameraFlight(shot ?: to, to, durationMs * 1_000_000L)
        if (durationMs <= 0) stepCamera(0L)
    }

    private fun stepCamera(frameTimeNanos: Long) {
        val current = flight ?: return
        place(current.at(frameTimeNanos))
        if (current.finished) flight = null
    }

    private fun place(next: CameraShot) {
        camera.lookAt(
            next.eye.x.toDouble(), next.eye.y.toDouble(), next.eye.z.toDouble(),
            next.target.x.toDouble(), next.target.y.toDouble(), next.target.z.toDouble(),
            0.0, 1.0, 0.0,
        )
        val aspect = if (height == 0) 1.0 else width.toDouble() / height.toDouble()
        camera.setProjection(CameraFraming.FOV_DEGREES, aspect, next.near, next.far, Camera.Fov.VERTICAL)
        shot = next
    }

    private fun assetBounds(): WorldBox? {
        val box = asset?.boundingBox ?: return null
        return WorldBox(
            Vec3(box.center[0], box.center[1], box.center[2]),
            Vec3(box.halfExtent[0], box.halfExtent[1], box.halfExtent[2]),
        )
    }

    /** World bounds of every renderable under [nodes], or null when none drew anything. */
    private fun nodeBounds(nodes: List<String>): WorldBox? {
        val current = asset ?: return null
        val renderables = engine.renderableManager
        val transforms = engine.transformManager
        val box = Box()
        val world = FloatArray(16)
        var total: WorldBox? = null
        for (node in nodes) {
            for (entity in current.getEntitiesByName(node)) {
                val renderable = renderables.getInstance(entity)
                val transform = transforms.getInstance(entity)
                if (renderable == 0 || transform == 0) continue
                renderables.getAxisAlignedBoundingBox(renderable, box)
                transforms.getWorldTransform(transform, world)
                val local = WorldBox(
                    Vec3(box.center[0], box.center[1], box.center[2]),
                    Vec3(box.halfExtent[0], box.halfExtent[1], box.halfExtent[2]),
                )
                val placed = WorldBox.transformed(local, world)
                total = total?.union(placed) ?: placed
            }
        }
        return total
    }

    override fun setCameraPose(pose: CameraPose): Unit =
        TODO("Phase 1: camera control; Phase 0 frames the whole asset on load")

    /**
     * Builds the structure index from the asset's own node names.
     *
     * A node whose name does not follow the sourcing convention is never picked as a
     * structure, so a naming mistake in the pipeline surfaces as a miss rather than as a
     * confidently wrong answer.
     */
    private fun indexNodes(loaded: FilamentAsset) {
        val byStructure = mutableMapOf<StructureId, MutableList<String>>()
        val byEntity = mutableMapOf<Int, StructureId>()
        val entitiesByStructureId = mutableMapOf<StructureId, MutableList<Int>>()
        for (entity in loaded.entities) {
            val name = loaded.getName(entity) ?: continue
            val node = StructureNode.parse(name) ?: continue
            byStructure.getOrPut(node.structure) { mutableListOf() }.add(name)
            byEntity[entity] = node.structure
            entitiesByStructureId.getOrPut(node.structure) { mutableListOf() }.add(entity)
        }
        nodesByStructure = byStructure
        entityToStructure = byEntity
        entitiesByStructure = entitiesByStructureId.mapValues { it.value.toIntArray() }
    }

    /**
     * Gives the camera the new surface's shape without moving it.
     *
     * The surface arrives and changes size on the host's schedule, which can be after the
     * app has sent the camera somewhere: a canvas is told what to frame as soon as its pack
     * has loaded, and its surface may not exist yet. Framing the whole asset here, as this
     * once did, threw that request away.
     */
    private fun fitCameraToSurface() {
        val current = shot
        when {
            current != null -> place(current)
            // A flight that has not drawn a frame yet places the camera on its first one.
            flight == null -> frameAsset()
        }
    }

    /** Frames the whole asset, so a caller that never sets a camera still sees the model. */
    private fun frameAsset() {
        val current = asset ?: return
        val box = current.boundingBox
        val centre = box.center
        val extent = box.halfExtent
        val radius = kotlin.math.sqrt(
            extent[0] * extent[0] + extent[1] * extent[1] + extent[2] * extent[2]
        )
        val distance = if (radius <= 0f) 1.0 else (radius / tan(Math.toRadians(22.5)) * 1.6)
        val aspect = if (height == 0) 1.0 else width.toDouble() / height.toDouble()

        camera.lookAt(
            centre[0].toDouble(), centre[1].toDouble(), centre[2] + distance,
            centre[0].toDouble(), centre[1].toDouble(), centre[2].toDouble(),
            0.0, 1.0, 0.0,
        )
        camera.setProjection(45.0, aspect, distance * 0.01, distance * 10.0, Camera.Fov.VERTICAL)
        shot = null
        flight = null
    }

    private fun releaseAsset() {
        val current = asset ?: return
        shot = null
        flight = null
        hidden.clear()
        ghosted.clear()
        highlighted.clear()
        applyAppearance()
        ghostMaterial?.let(engine::destroyMaterialInstance)
        ghostMaterial = null
        entitiesByStructure = emptyMap()
        scene.removeEntities(current.entities)
        assetLoader.destroyAsset(current)
        asset = null
        nodesByStructure = emptyMap()
        entityToStructure = emptyMap()
    }
}

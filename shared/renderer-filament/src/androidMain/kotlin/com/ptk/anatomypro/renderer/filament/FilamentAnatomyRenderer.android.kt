package com.ptk.anatomypro.renderer.filament

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
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.StructureNode
import com.ptk.anatomypro.core.model.SystemId
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

    /** Swapped-out material instances, so highlighting is exactly reversible. */
    private val swapped = mutableListOf<Triple<Int, Int, MaterialInstance>>()
    private val highlightInstances = mutableListOf<MaterialInstance>()

    private val _events = MutableSharedFlow<RendererEvent>(replay = 64, extraBufferCapacity = 64)
    override val events: Flow<RendererEvent> = _events.asSharedFlow()

    /** Picking results are delivered straight onto the caller's thread, as on iOS. */
    private val inlineExecutor = Executor { it.run() }

    init {
        Filament.init()
        Gltfio.init()

        engine = Engine.create()
        renderer = engine.createRenderer()
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
        configureSurface(engine.createSwapChain(width, height, 0L), width, height)
    }

    /** Draws into a `Surface` owned by the host — a SurfaceView or a TextureView. */
    fun attachSurface(surface: Any, width: Int, height: Int) {
        configureSurface(engine.createSwapChain(surface), width, height)
    }

    private fun configureSurface(next: SwapChain, width: Int, height: Int) {
        swapChain?.let(engine::destroySwapChain)
        swapChain = next
        this.width = width
        this.height = height
        view.viewport = Viewport(0, 0, width, height)
        frameAsset()
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
        if (!renderer.beginFrame(chain, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
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

    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        clearHighlightInternal()
        val current = asset ?: return
        val nodes = structures.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) return

        val alpha = ((style.outlineArgb ushr 24) and 0xFF) / 255f
        val red = ((style.outlineArgb shr 16) and 0xFF) / 255f
        val green = ((style.outlineArgb shr 8) and 0xFF) / 255f
        val blue = (style.outlineArgb and 0xFF) / 255f
        val lift = style.fillLuminanceShift.coerceAtLeast(0f)

        val renderables = engine.renderableManager
        for (node in nodes) {
            for (entity in current.getEntitiesByName(node)) {
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

    override fun setPickingEnabled(enabled: Boolean) {
        pickingEnabled = enabled
    }

    override fun setSystemVisibility(system: SystemId, visible: Boolean): Unit =
        TODO("Phase 1: needs the pack manifest's system index")

    override fun setOpacity(structures: Set<StructureId>, alpha: Float): Unit =
        TODO("Phase 1: needs transparent material variants")

    override fun isolate(structure: StructureId?, ghostNeighbours: Boolean): Unit =
        TODO("Phase 1: needs transparent material variants")

    override fun focusCamera(structure: StructureId, durationMs: Int): Unit =
        TODO("Phase 1: camera animation; Phase 0 frames the whole asset on load")

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
        for (entity in loaded.entities) {
            val name = loaded.getName(entity) ?: continue
            val node = StructureNode.parse(name) ?: continue
            byStructure.getOrPut(node.structure) { mutableListOf() }.add(name)
            byEntity[entity] = node.structure
        }
        nodesByStructure = byStructure
        entityToStructure = byEntity
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
    }

    private fun clearHighlightInternal() {
        val renderables = engine.renderableManager
        for ((entity, primitive, original) in swapped) {
            val instance = renderables.getInstance(entity)
            if (instance != 0) renderables.setMaterialInstanceAt(instance, primitive, original)
        }
        swapped.clear()
        highlightInstances.forEach(engine::destroyMaterialInstance)
        highlightInstances.clear()
    }

    private fun releaseAsset() {
        val current = asset ?: return
        clearHighlightInternal()
        scene.removeEntities(current.entities)
        assetLoader.destroyAsset(current)
        asset = null
        nodesByStructure = emptyMap()
        entityToStructure = emptyMap()
    }
}

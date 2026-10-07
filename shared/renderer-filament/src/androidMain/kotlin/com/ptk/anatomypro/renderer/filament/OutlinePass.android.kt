package com.ptk.anatomypro.renderer.filament

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws outlines: a mask per group of structures outlined alike, and an overlay that turns
 * each mask's edge into a line.
 *
 * Filament draws an entity once per view, with the material it holds, so an outline needs a
 * second look at the same geometry. Each group's entities are added to a small scene of
 * their own — an entity may be in several scenes — and a view renders that scene, with the
 * main camera, into a half-size target. Nothing else is in that scene, which is why the
 * outline shows through whatever stands in front. The overlay view then draws one
 * full-screen triangle per group with the outline material.
 *
 * It never touches a pack entity's material: the slot has one owner (design §26.4).
 */
internal class OutlinePass(
    private val engine: Engine,
    materialBytes: ByteArray,
    private val camera: Camera,
    private val layerSelect: Int,
    private val layerValues: Int,
) {

    /** One group to draw: its entities, its colour as unconverted fractions, its width in pixels. */
    class Spec(
        val entities: IntArray,
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
        val widthPx: Float,
    )

    private class Slot(val scene: Scene, val view: View, val quad: Int, val instance: MaterialInstance) {
        var entities: IntArray = NONE
        var colour: Texture? = null
        var target: RenderTarget? = null
        var widthPx = 0f
    }

    private val material: Material
    private val vertices: VertexBuffer
    private val indices: IndexBuffer
    private val overlayScene: Scene = engine.createScene()
    private val overlayView: View = engine.createView()

    /** Slots are kept and reused: moving a selection must not make and destroy a render target. */
    private val slots = mutableListOf<Slot>()
    private var active = 0
    private var width = 0
    private var height = 0

    init {
        val payload = ByteBuffer.allocateDirect(materialBytes.size).put(materialBytes).apply { flip() }
        material = Material.Builder().payload(payload, payload.remaining()).build(engine)

        // One triangle that covers the screen. The material's vertex domain is `device`, so
        // these are clip-space positions and no camera moves them.
        val positions = ByteBuffer.allocateDirect(3 * 3 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        positions.asFloatBuffer().put(floatArrayOf(-1f, -1f, 0f, 3f, -1f, 0f, -1f, 3f, 0f))
        vertices = VertexBuffer.Builder()
            .vertexCount(3)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 3 * Float.SIZE_BYTES)
            .build(engine)
        vertices.setBufferAt(engine, 0, positions)

        val order = ByteBuffer.allocateDirect(3 * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        order.asShortBuffer().put(shortArrayOf(0, 1, 2))
        indices = IndexBuffer.Builder().indexCount(3).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        indices.setBuffer(engine, order)

        overlayView.scene = overlayScene
        overlayView.camera = camera
        // No tone mapping: the outline colour is written as given, so a frame holds the
        // style's own bytes.
        overlayView.isPostProcessingEnabled = false
        overlayView.blendMode = View.BlendMode.TRANSLUCENT
        overlayView.setShadowingEnabled(false)
    }

    /** The surface's size in pixels. Masks are half of it, and are made again at the new size. */
    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        overlayView.viewport = Viewport(0, 0, width, height)
        for (index in 0 until active) allocate(slots[index])
    }

    /** Replaces what is outlined. A group with no entities draws nothing and holds nothing. */
    fun setGroups(specs: List<Spec>) {
        for (index in 0 until active) {
            val slot = slots[index]
            slot.scene.removeEntities(slot.entities)
            slot.entities = NONE
            overlayScene.removeEntity(slot.quad)
        }
        val drawn = specs.filter { it.entities.isNotEmpty() }
        while (slots.size < drawn.size) slots += newSlot()
        drawn.forEachIndexed { index, spec ->
            val slot = slots[index]
            slot.entities = spec.entities.copyOf()
            slot.scene.addEntities(slot.entities)
            slot.widthPx = spec.widthPx
            slot.instance.setParameter("color", spec.red, spec.green, spec.blue, spec.alpha)
            if (slot.target == null) allocate(slot) else setReach(slot)
            overlayScene.addEntity(slot.quad)
        }
        // Nothing highlighted, nothing held.
        for (index in drawn.size until slots.size) release(slots[index])
        active = drawn.size
    }

    /** Call inside a frame, before the main view. */
    fun renderMasks(renderer: Renderer) {
        for (index in 0 until active) {
            if (slots[index].target != null) renderer.render(slots[index].view)
        }
    }

    /** Call inside a frame, after the main view. */
    fun renderOverlay(renderer: Renderer) {
        if (active > 0 && width > 0 && height > 0) renderer.render(overlayView)
    }

    fun destroy() {
        setGroups(emptyList())
        for (slot in slots) {
            engine.destroyEntity(slot.quad)
            EntityManager.get().destroy(slot.quad)
            engine.destroyMaterialInstance(slot.instance)
            engine.destroyView(slot.view)
            engine.destroyScene(slot.scene)
        }
        slots.clear()
        engine.destroyView(overlayView)
        engine.destroyScene(overlayScene)
        engine.destroyVertexBuffer(vertices)
        engine.destroyIndexBuffer(indices)
        engine.destroyMaterial(material)
    }

    private fun newSlot(): Slot {
        val scene = engine.createScene()
        val view = engine.createView()
        view.scene = scene
        view.camera = camera
        view.isPostProcessingEnabled = false
        // Translucent, so the target keeps the alpha that was drawn into it. An opaque view
        // may hand back alpha 1 everywhere: it did for the packs' materials (specular and
        // ior extensions), though not for a bare metallic-roughness one, and a mask that is
        // opaque everywhere has no edge to draw.
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.setShadowingEnabled(false)
        // The main view's layers, so a hidden structure leaves nothing in its mask.
        view.setVisibleLayers(layerSelect, layerValues)

        val instance = material.createInstance()
        val quad = EntityManager.get().create()
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, indices, 0, 3)
            .material(0, instance)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, quad)
        return Slot(scene, view, quad, instance)
    }

    private fun allocate(slot: Slot) {
        release(slot)
        if (width <= 0 || height <= 0) return
        val maskWidth = maxOf(1, width / 2)
        val maskHeight = maxOf(1, height / 2)
        val colour = Texture.Builder()
            .width(maskWidth)
            .height(maskHeight)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.RGBA8)
            .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE)
            .build(engine)
        val target = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, colour)
            .build(engine)
        slot.colour = colour
        slot.target = target
        slot.view.renderTarget = target
        slot.view.viewport = Viewport(0, 0, maskWidth, maskHeight)
        slot.instance.setParameter(
            "mask",
            colour,
            TextureSampler(
                TextureSampler.MinFilter.LINEAR,
                TextureSampler.MagFilter.LINEAR,
                TextureSampler.WrapMode.CLAMP_TO_EDGE,
            ),
        )
        setReach(slot)
    }

    private fun setReach(slot: Slot) {
        if (width <= 0 || height <= 0) return
        slot.instance.setParameter("reach", slot.widthPx / width, slot.widthPx / height)
    }

    private fun release(slot: Slot) {
        slot.view.renderTarget = null
        slot.target?.let(engine::destroyRenderTarget)
        slot.colour?.let(engine::destroyTexture)
        slot.target = null
        slot.colour = null
    }

    private companion object {
        val NONE = IntArray(0)
    }
}

/*
 * Draws outlines: a mask per group of nodes outlined alike, and an overlay that turns each
 * mask's edge into a line. The counterpart of OutlinePass.android.kt; read that file's
 * header comment for why it is built this way.
 *
 * Private to the shim. Nothing here crosses the C seam.
 */
#ifndef ANATOMY_OUTLINE_PASS_H
#define ANATOMY_OUTLINE_PASS_H

#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/Renderer.h>

#include <math/vec4.h>
#include <utils/Entity.h>

#include <cstddef>
#include <cstdint>
#include <vector>

namespace anatomy {

/** One group to draw: its entities, its colour as unconverted fractions, its width in pixels. */
struct OutlineSpec {
    std::vector<utils::Entity> entities;
    filament::math::float4 color;
    float widthPx;
};

class OutlinePass {
public:
    /** Null when the material does not load. */
    static OutlinePass* create(filament::Engine* engine, const void* material, size_t size,
                               filament::Camera* camera, uint8_t layerSelect, uint8_t layerValues);
    ~OutlinePass();

    OutlinePass(const OutlinePass&) = delete;
    OutlinePass& operator=(const OutlinePass&) = delete;

    /** The surface's size in pixels. Masks are half of it, and are made again at the new size. */
    void resize(uint32_t width, uint32_t height);

    /** Replaces what is outlined. A group with no entities draws nothing and holds nothing. */
    void setGroups(const std::vector<OutlineSpec>& specs);

    /** Call inside a frame, before the main view. */
    void renderMasks(filament::Renderer* renderer);

    /** Call inside a frame, after the main view. */
    void renderOverlay(filament::Renderer* renderer);

private:
    struct Slot;
    OutlinePass() = default;
    Slot* newSlot();
    void allocate(Slot* slot);
    void setReach(Slot* slot);
    void release(Slot* slot);

    filament::Engine* mEngine = nullptr;
    filament::Camera* mCamera = nullptr;
    filament::Material* mMaterial = nullptr;
    filament::VertexBuffer* mVertices = nullptr;
    filament::IndexBuffer* mIndices = nullptr;
    filament::Scene* mOverlayScene = nullptr;
    filament::View* mOverlayView = nullptr;
    // Slots are kept and reused: moving a selection must not make and destroy a render target.
    std::vector<Slot*> mSlots;
    size_t mActive = 0;
    uint32_t mWidth = 0;
    uint32_t mHeight = 0;
    uint8_t mLayerSelect = 0;
    uint8_t mLayerValues = 0;
};

} // namespace anatomy

#endif

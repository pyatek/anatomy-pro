#include "OutlinePass.h"

#include <filament/IndexBuffer.h>
#include <filament/Material.h>
#include <filament/MaterialInstance.h>
#include <filament/RenderTarget.h>
#include <filament/RenderableManager.h>
#include <filament/Scene.h>
#include <filament/Texture.h>
#include <filament/TextureSampler.h>
#include <filament/VertexBuffer.h>
#include <filament/View.h>
#include <filament/Viewport.h>

#include <math/vec2.h>
#include <utils/EntityManager.h>

#include <algorithm>

using namespace filament;
using utils::Entity;

namespace anatomy {

namespace {

// One triangle that covers the screen. The material's vertex domain is `device`, so these
// are clip-space positions. Static, because Filament reads them after this call returns.
const float kPositions[] = {-1.0f, -1.0f, 0.0f, 3.0f, -1.0f, 0.0f, -1.0f, 3.0f, 0.0f};
const uint16_t kOrder[] = {0, 1, 2};

} // namespace

struct OutlinePass::Slot {
    Scene* scene = nullptr;
    View* view = nullptr;
    Entity quad;
    MaterialInstance* instance = nullptr;
    std::vector<Entity> entities;
    Texture* colour = nullptr;
    RenderTarget* target = nullptr;
    float widthPx = 0.0f;
};

OutlinePass* OutlinePass::create(Engine* engine, const void* material, size_t size, Camera* camera,
                                 uint8_t layerSelect, uint8_t layerValues) {
    if (!engine || !material || size == 0 || !camera) return nullptr;
    Material* built = Material::Builder().package(material, size).build(*engine);
    if (!built) return nullptr;

    auto* pass = new OutlinePass();
    pass->mEngine = engine;
    pass->mCamera = camera;
    pass->mMaterial = built;
    pass->mLayerSelect = layerSelect;
    pass->mLayerValues = layerValues;

    pass->mVertices = VertexBuffer::Builder()
        .vertexCount(3)
        .bufferCount(1)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, 3 * sizeof(float))
        .build(*engine);
    pass->mVertices->setBufferAt(*engine, 0, VertexBuffer::BufferDescriptor(kPositions, sizeof(kPositions)));
    pass->mIndices = IndexBuffer::Builder()
        .indexCount(3)
        .bufferType(IndexBuffer::IndexType::USHORT)
        .build(*engine);
    pass->mIndices->setBuffer(*engine, IndexBuffer::BufferDescriptor(kOrder, sizeof(kOrder)));

    pass->mOverlayScene = engine->createScene();
    pass->mOverlayView = engine->createView();
    pass->mOverlayView->setScene(pass->mOverlayScene);
    pass->mOverlayView->setCamera(camera);
    // No tone mapping: the outline colour is written as given, so a frame holds the style's
    // own bytes.
    pass->mOverlayView->setPostProcessingEnabled(false);
    pass->mOverlayView->setBlendMode(View::BlendMode::TRANSLUCENT);
    pass->mOverlayView->setShadowingEnabled(false);
    return pass;
}

OutlinePass::~OutlinePass() {
    setGroups({});
    for (Slot* slot : mSlots) {
        mEngine->destroy(slot->quad);
        utils::EntityManager::get().destroy(slot->quad);
        mEngine->destroy(slot->instance);
        mEngine->destroy(slot->view);
        mEngine->destroy(slot->scene);
        delete slot;
    }
    mSlots.clear();
    mEngine->destroy(mOverlayView);
    mEngine->destroy(mOverlayScene);
    mEngine->destroy(mVertices);
    mEngine->destroy(mIndices);
    mEngine->destroy(mMaterial);
}

void OutlinePass::resize(uint32_t width, uint32_t height) {
    mWidth = width;
    mHeight = height;
    mOverlayView->setViewport({0, 0, width, height});
    for (size_t i = 0; i < mActive; ++i) allocate(mSlots[i]);
}

void OutlinePass::setGroups(const std::vector<OutlineSpec>& specs) {
    for (size_t i = 0; i < mActive; ++i) {
        Slot* slot = mSlots[i];
        slot->scene->removeEntities(slot->entities.data(), slot->entities.size());
        slot->entities.clear();
        mOverlayScene->remove(slot->quad);
    }
    size_t drawn = 0;
    for (const auto& spec : specs) {
        if (spec.entities.empty()) continue;
        if (mSlots.size() <= drawn) mSlots.push_back(newSlot());
        Slot* slot = mSlots[drawn++];
        slot->entities = spec.entities;
        slot->scene->addEntities(slot->entities.data(), slot->entities.size());
        slot->widthPx = spec.widthPx;
        // The plain setter: unconverted, unlike the tint's RgbaType::sRGB.
        slot->instance->setParameter("color", spec.color);
        if (!slot->target) allocate(slot); else setReach(slot);
        mOverlayScene->addEntity(slot->quad);
    }
    // Nothing highlighted, nothing held.
    for (size_t i = drawn; i < mSlots.size(); ++i) release(mSlots[i]);
    mActive = drawn;
}

void OutlinePass::renderMasks(Renderer* renderer) {
    for (size_t i = 0; i < mActive; ++i) {
        if (mSlots[i]->target) renderer->render(mSlots[i]->view);
    }
}

void OutlinePass::renderOverlay(Renderer* renderer) {
    if (mActive > 0 && mWidth > 0 && mHeight > 0) renderer->render(mOverlayView);
}

OutlinePass::Slot* OutlinePass::newSlot() {
    auto* slot = new Slot();
    slot->scene = mEngine->createScene();
    slot->view = mEngine->createView();
    slot->view->setScene(slot->scene);
    slot->view->setCamera(mCamera);
    slot->view->setPostProcessingEnabled(false);
    // Translucent, so the target keeps the alpha that was drawn into it. An opaque view
    // may hand back alpha 1 everywhere: it did on Android for the packs' materials
    // (specular and ior extensions), and a mask that is opaque everywhere has no edge.
    slot->view->setBlendMode(View::BlendMode::TRANSLUCENT);
    slot->view->setShadowingEnabled(false);
    // The main view's layers, so a hidden node leaves nothing in its mask.
    slot->view->setVisibleLayers(mLayerSelect, mLayerValues);

    slot->instance = mMaterial->createInstance();
    slot->quad = utils::EntityManager::get().create();
    RenderableManager::Builder(1)
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, mVertices, mIndices, 0, 3)
        .material(0, slot->instance)
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)
        .build(*mEngine, slot->quad);
    return slot;
}

void OutlinePass::allocate(Slot* slot) {
    release(slot);
    if (mWidth == 0 || mHeight == 0) return;
    const uint32_t maskWidth = std::max<uint32_t>(1, mWidth / 2);
    const uint32_t maskHeight = std::max<uint32_t>(1, mHeight / 2);
    slot->colour = Texture::Builder()
        .width(maskWidth)
        .height(maskHeight)
        .levels(1)
        .sampler(Texture::Sampler::SAMPLER_2D)
        .format(Texture::InternalFormat::RGBA8)
        .usage(Texture::Usage::COLOR_ATTACHMENT | Texture::Usage::SAMPLEABLE)
        .build(*mEngine);
    slot->target = RenderTarget::Builder()
        .texture(RenderTarget::AttachmentPoint::COLOR, slot->colour)
        .build(*mEngine);
    slot->view->setRenderTarget(slot->target);
    slot->view->setViewport({0, 0, maskWidth, maskHeight});
    TextureSampler sampler(TextureSampler::MinFilter::LINEAR, TextureSampler::MagFilter::LINEAR,
                           TextureSampler::WrapMode::CLAMP_TO_EDGE);
    slot->instance->setParameter("mask", slot->colour, sampler);
    setReach(slot);
}

void OutlinePass::setReach(Slot* slot) {
    if (mWidth == 0 || mHeight == 0) return;
    slot->instance->setParameter(
        "reach", math::float2{slot->widthPx / float(mWidth), slot->widthPx / float(mHeight)});
}

void OutlinePass::release(Slot* slot) {
    slot->view->setRenderTarget(nullptr);
    if (slot->target) mEngine->destroy(slot->target);
    if (slot->colour) mEngine->destroy(slot->colour);
    slot->target = nullptr;
    slot->colour = nullptr;
}

} // namespace anatomy

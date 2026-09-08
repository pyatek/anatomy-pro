#include "anatomy_renderer.h"

#include <backend/CallbackHandler.h>
#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/IndirectLight.h>
#include <filament/LightManager.h>
#include <filament/Material.h>
#include <filament/MaterialInstance.h>
#include <filament/RenderableManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/SwapChain.h>
#include <filament/TransformManager.h>
#include <filament/View.h>
#include <filament/Viewport.h>

#include <gltfio/AssetLoader.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/FilamentInstance.h>
#include <gltfio/MaterialProvider.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/materials/uberarchive.h>

#include <utils/EntityManager.h>
#include <utils/NameComponentManager.h>

#include <cmath>
#include <cstdio>
#include <deque>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

using namespace filament;
using namespace filament::gltfio;
using namespace filament::math;
using utils::Entity;

namespace {

/**
 * An event with its strings owned, so the queue outlives whatever produced it.
 *
 * ar_event hands out borrowed pointers; holding the storage here and keeping the most
 * recently drained event alive is what makes that safe without asking the caller to free.
 */
struct QueuedEvent {
    ar_event_type type = AR_EVENT_READY;
    std::string uri;
    std::string nodeName;
    std::string code;
    std::string message;
    float fraction = 0.0f;
    int64_t residentBytes = 0;
};

/**
 * Runs Filament's callbacks the moment the backend produces them.
 *
 * The default handler dispatches "opportunistically" on Filament's own main thread, which
 * in practice means a picking result can sit undelivered for as long as the engine lives.
 * Running it inline is safe here precisely because the event queue is mutex-guarded and
 * nothing crosses into Kotlin from the callback — the caller drains on its own thread.
 */
class ImmediateCallbackHandler : public backend::CallbackHandler {
public:
    void post(void* user, Callback callback) override { callback(user); }
};

ImmediateCallbackHandler& immediateHandler() {
    static ImmediateCallbackHandler handler;
    return handler;
}

/** A material instance we swapped out, so highlighting is exactly reversible. */
struct SwappedMaterial {
    Entity entity;
    size_t primitiveIndex;
    MaterialInstance* original;
};

std::string pathFromUri(const char* uri) {
    if (!uri) return {};
    std::string value(uri);
    const std::string scheme = "file://";
    if (value.rfind(scheme, 0) == 0) value.erase(0, scheme.size());
    return value;
}

} // namespace

struct ar_renderer {
    Engine* engine = nullptr;
    SwapChain* swapChain = nullptr;
    filament::Renderer* renderer = nullptr;
    Scene* scene = nullptr;
    View* view = nullptr;
    Camera* camera = nullptr;
    Entity cameraEntity;
    Entity sunEntity;

    MaterialProvider* materials = nullptr;
    utils::NameComponentManager* names = nullptr;
    AssetLoader* assetLoader = nullptr;
    ResourceLoader* resourceLoader = nullptr;

    FilamentAsset* asset = nullptr;
    std::string assetUri;
    std::vector<std::string> nodeNames;
    std::unordered_map<uint32_t, std::string> entityToNode;

    std::vector<SwappedMaterial> swapped;
    std::vector<MaterialInstance*> highlightInstances;

    bool pickingEnabled = true;
    uint32_t width = 0;
    uint32_t height = 0;

    std::mutex mutex;
    std::deque<QueuedEvent> queue;
    // Kept alive so borrowed pointers in the last ar_event handed out stay valid.
    QueuedEvent drained;

    void push(QueuedEvent&& event) {
        std::lock_guard<std::mutex> lock(mutex);
        queue.push_back(std::move(event));
    }

    void fail(std::string code, std::string message) {
        QueuedEvent event;
        event.type = AR_EVENT_ERROR;
        event.code = std::move(code);
        event.message = std::move(message);
        push(std::move(event));
    }
};

namespace {

/** Frames the whole asset, so a caller that never sets a camera still sees the model. */
void frameAsset(ar_renderer* r) {
    if (!r->asset) return;

    const Aabb bounds = r->asset->getBoundingBox();
    const float3 center = (bounds.min + bounds.max) * 0.5f;
    const float radius = length(bounds.max - bounds.min) * 0.5f;
    const float distance = radius <= 0.0f ? 1.0f : radius / std::tan(45.0f * 0.5f * float(M_PI) / 180.0f) * 1.6f;

    r->camera->lookAt(center + float3{0.0f, 0.0f, distance}, center, float3{0.0f, 1.0f, 0.0f});
    const double aspect = r->height == 0 ? 1.0 : double(r->width) / double(r->height);
    r->camera->setProjection(45.0, aspect, distance * 0.01, distance * 10.0, Camera::Fov::VERTICAL);
}

void configureSurface(ar_renderer* r, SwapChain* swapChain, uint32_t width, uint32_t height) {
    if (r->swapChain) r->engine->destroy(r->swapChain);
    r->swapChain = swapChain;
    r->width = width;
    r->height = height;
    r->view->setViewport({0, 0, width, height});
    frameAsset(r);

    QueuedEvent ready;
    ready.type = AR_EVENT_READY;
    r->push(std::move(ready));
}

void clearHighlightInternal(ar_renderer* r) {
    auto& rm = r->engine->getRenderableManager();
    for (const auto& entry : r->swapped) {
        auto instance = rm.getInstance(entry.entity);
        if (instance) rm.setMaterialInstanceAt(instance, entry.primitiveIndex, entry.original);
    }
    r->swapped.clear();

    for (auto* material : r->highlightInstances) r->engine->destroy(material);
    r->highlightInstances.clear();
}

void releaseAsset(ar_renderer* r) {
    if (!r->asset) return;
    clearHighlightInternal(r);
    r->scene->removeEntities(r->asset->getEntities(), r->asset->getEntityCount());
    r->assetLoader->destroyAsset(r->asset);
    r->asset = nullptr;
    r->assetUri.clear();
    r->nodeNames.clear();
    r->entityToNode.clear();
}

} // namespace

ar_renderer_ref ar_create(void) {
    auto* r = new ar_renderer();

    r->engine = Engine::create(Engine::Backend::METAL);
    if (!r->engine) {
        r->fail("engine-create-failed", "Filament could not initialise the Metal backend");
        return r;
    }

    r->renderer = r->engine->createRenderer();
    r->scene = r->engine->createScene();
    r->view = r->engine->createView();
    r->cameraEntity = utils::EntityManager::get().create();
    r->camera = r->engine->createCamera(r->cameraEntity);

    r->view->setScene(r->scene);
    r->view->setCamera(r->camera);
    // Picking reads the renderable id buffer, which the post-processing pass would consume.
    r->view->setPostProcessingEnabled(false);

    // A single directional light. Phase 0 needs the model lit well enough to see and to
    // pick; image-based lighting belongs with the material work in Phase 1.
    r->sunEntity = utils::EntityManager::get().create();
    LightManager::Builder(LightManager::Type::DIRECTIONAL)
        .color({1.0f, 1.0f, 1.0f})
        .intensity(100000.0f)
        .direction({0.4f, -1.0f, -0.8f})
        .castShadows(false)
        .build(*r->engine, r->sunEntity);
    r->scene->addEntity(r->sunEntity);

    r->materials = createUbershaderProvider(r->engine, UBERARCHIVE_DEFAULT_DATA, UBERARCHIVE_DEFAULT_SIZE);
    r->names = new utils::NameComponentManager(utils::EntityManager::get());
    r->assetLoader = AssetLoader::create({r->engine, r->materials, r->names});

    ResourceConfiguration resourceConfig{};
    resourceConfig.engine = r->engine;
    resourceConfig.normalizeSkinningWeights = true;
    r->resourceLoader = new ResourceLoader(resourceConfig);

    return r;
}

void ar_destroy(ar_renderer_ref r) {
    if (!r) return;
    if (r->engine) {
        releaseAsset(r);
        delete r->resourceLoader;
        if (r->assetLoader) AssetLoader::destroy(&r->assetLoader);
        delete r->names;
        if (r->materials) delete r->materials;

        if (!r->sunEntity.isNull()) {
            r->scene->remove(r->sunEntity);
            r->engine->destroy(r->sunEntity);
            utils::EntityManager::get().destroy(r->sunEntity);
        }
        if (r->swapChain) r->engine->destroy(r->swapChain);
        if (r->view) r->engine->destroy(r->view);
        if (r->scene) r->engine->destroy(r->scene);
        if (r->renderer) r->engine->destroy(r->renderer);
        if (r->camera) r->engine->destroyCameraComponent(r->cameraEntity);
        if (!r->cameraEntity.isNull()) utils::EntityManager::get().destroy(r->cameraEntity);
        Engine::destroy(&r->engine);
    }
    delete r;
}

void ar_attach_headless(ar_renderer_ref r, uint32_t width, uint32_t height) {
    if (!r || !r->engine) return;
    // Offscreen rendering has no display to pace against. Left at the default, Filament
    // drops most frames of a tight render loop, and a picking readback never completes.
    r->renderer->setDisplayInfo({.refreshRate = 0.0f});
    configureSurface(r, r->engine->createSwapChain(width, height), width, height);
}

void ar_attach_layer(ar_renderer_ref r, void* layer, uint32_t width, uint32_t height) {
    if (!r || !r->engine) return;
    r->renderer->setDisplayInfo({.refreshRate = 60.0f});
    configureSurface(r, r->engine->createSwapChain(layer), width, height);
}

void ar_load_model(ar_renderer_ref r, const char* uri) {
    if (!r || !r->engine) return;
    releaseAsset(r);

    const std::string path = pathFromUri(uri);
    FILE* file = std::fopen(path.c_str(), "rb");
    if (!file) {
        r->fail("model-not-found", "No file at " + path);
        return;
    }
    std::fseek(file, 0, SEEK_END);
    const long size = std::ftell(file);
    std::fseek(file, 0, SEEK_SET);
    std::vector<uint8_t> bytes(size > 0 ? size_t(size) : 0);
    const size_t read = bytes.empty() ? 0 : std::fread(bytes.data(), 1, bytes.size(), file);
    std::fclose(file);
    if (read != bytes.size() || bytes.empty()) {
        r->fail("model-unreadable", "Could not read " + path);
        return;
    }

    r->asset = r->assetLoader->createAsset(bytes.data(), uint32_t(bytes.size()));
    if (!r->asset) {
        r->fail("model-invalid", "glTF could not be parsed: " + path);
        return;
    }
    if (!r->resourceLoader->loadResources(r->asset)) {
        r->fail("model-resources-failed", "glTF resources could not be uploaded: " + path);
        releaseAsset(r);
        return;
    }
    r->asset->releaseSourceData();

    // The name index is built here, from the asset itself, so the caller can map node
    // names to StructureIds without any content database (spec §20.2).
    const Entity* entities = r->asset->getEntities();
    for (size_t i = 0, n = r->asset->getEntityCount(); i < n; ++i) {
        const char* name = r->asset->getName(entities[i]);
        if (!name || !*name) continue;
        r->nodeNames.emplace_back(name);
        r->entityToNode[entities[i].getId()] = name;
    }

    r->scene->addEntities(entities, r->asset->getEntityCount());
    r->assetUri = uri ? uri : "";
    frameAsset(r);

    QueuedEvent loaded;
    loaded.type = AR_EVENT_MODEL_LOADED;
    loaded.uri = r->assetUri;
    r->push(std::move(loaded));
}

void ar_unload_model(ar_renderer_ref r, const char* uri) {
    if (!r || !r->engine || !r->asset) return;
    const std::string requested = uri ? uri : "";
    if (!requested.empty() && requested != r->assetUri) return;

    QueuedEvent unloaded;
    unloaded.type = AR_EVENT_MODEL_UNLOADED;
    unloaded.uri = r->assetUri;
    releaseAsset(r);
    r->push(std::move(unloaded));
}

size_t ar_node_count(ar_renderer_ref r) {
    return r ? r->nodeNames.size() : 0;
}

const char* ar_node_name_at(ar_renderer_ref r, size_t index) {
    if (!r || index >= r->nodeNames.size()) return nullptr;
    return r->nodeNames[index].c_str();
}

void ar_set_highlight(ar_renderer_ref r, const char* const* nodeNames, size_t count,
                      int32_t outlineArgb, float luminanceShift) {
    if (!r || !r->engine || !r->asset) return;
    clearHighlightInternal(r);
    if (!nodeNames || count == 0) return;

    const float4 tint{
        float((outlineArgb >> 16) & 0xFF) / 255.0f,
        float((outlineArgb >> 8) & 0xFF) / 255.0f,
        float(outlineArgb & 0xFF) / 255.0f,
        float((uint32_t(outlineArgb) >> 24) & 0xFF) / 255.0f,
    };
    const float lift = luminanceShift < 0.0f ? 0.0f : luminanceShift;

    auto& rm = r->engine->getRenderableManager();
    for (size_t i = 0; i < count; ++i) {
        if (!nodeNames[i]) continue;
        Entity found[8];
        const size_t matches = r->asset->getEntitiesByName(nodeNames[i], found, 8);
        for (size_t m = 0; m < matches; ++m) {
            auto instance = rm.getInstance(found[m]);
            if (!instance) continue;
            for (size_t p = 0, primitives = rm.getPrimitiveCount(instance); p < primitives; ++p) {
                MaterialInstance* original = rm.getMaterialInstanceAt(instance, p);
                if (!original) continue;
                const Material* material = original->getMaterial();
                MaterialInstance* replacement = MaterialInstance::duplicate(original);
                if (material->hasParameter("baseColorFactor")) {
                    replacement->setParameter("baseColorFactor", RgbaType::sRGB, tint);
                }
                if (material->hasParameter("emissiveFactor")) {
                    replacement->setParameter("emissiveFactor",
                                              float3{tint.r * lift, tint.g * lift, tint.b * lift});
                }
                r->swapped.push_back({found[m], p, original});
                r->highlightInstances.push_back(replacement);
                rm.setMaterialInstanceAt(instance, p, replacement);
            }
        }
    }
}

void ar_clear_highlight(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    clearHighlightInternal(r);
}

void ar_set_picking_enabled(ar_renderer_ref r, bool enabled) {
    if (r) r->pickingEnabled = enabled;
}

void ar_pick_at(ar_renderer_ref r, float x, float y) {
    if (!r || !r->engine || !r->view || !r->pickingEnabled) return;

    // Callers work in touch coordinates, whose origin is top-left; Filament's picking
    // viewport has its origin at the bottom-left.
    const uint32_t px = uint32_t(x < 0.0f ? 0.0f : x);
    const float flipped = float(r->height) - y;
    const uint32_t py = uint32_t(flipped < 0.0f ? 0.0f : flipped);

    r->view->pick(px, py, [r](View::PickingQueryResult const& result) {
        QueuedEvent picked;
        picked.type = AR_EVENT_PICKED;
        if (!result.renderable.isNull()) {
            const auto entry = r->entityToNode.find(result.renderable.getId());
            if (entry != r->entityToNode.end()) picked.nodeName = entry->second;
        }
        r->push(std::move(picked));
    }, &immediateHandler());
}

bool ar_render_frame(ar_renderer_ref r, uint64_t vsync_nanos) {
    if (!r || !r->engine || !r->swapChain) return false;
    // Filament refuses a frame while too many are already in flight. Reporting that back
    // lets a caller that needs frames to actually land — a contract test waiting on a
    // picking readback — know the difference between a drawn frame and a skipped one.
    if (!r->renderer->beginFrame(r->swapChain, vsync_nanos)) return false;
    r->renderer->render(r->view);
    r->renderer->endFrame();
    return true;
}

void ar_wait_for_gpu(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->engine->flushAndWait();
}

bool ar_poll_event(ar_renderer_ref r, ar_event* out) {
    if (!r || !out) return false;

    std::lock_guard<std::mutex> lock(r->mutex);
    if (r->queue.empty()) return false;

    r->drained = std::move(r->queue.front());
    r->queue.pop_front();

    out->type = r->drained.type;
    out->uri = r->drained.uri.empty() ? nullptr : r->drained.uri.c_str();
    out->node_name = r->drained.nodeName.empty() ? nullptr : r->drained.nodeName.c_str();
    out->code = r->drained.code.empty() ? nullptr : r->drained.code.c_str();
    out->message = r->drained.message.empty() ? nullptr : r->drained.message.c_str();
    out->fraction = r->drained.fraction;
    out->resident_bytes = r->drained.residentBytes;
    return true;
}

#include "anatomy_renderer.h"

#include "OutlinePass.h"

#include <backend/CallbackHandler.h>
#include <backend/PixelBufferDescriptor.h>
#include <filament/Box.h>
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

#include <mach/mach.h>

#include <algorithm>
#include <atomic>
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

/** Nodes highlighted alike. The tint arrives ready-made; see ar_add_highlight. */
struct HighlightGroup {
    std::vector<std::string> nodes;
    float4 tint;
    float3 emissive;
    float4 outline;
    float outlineWidthPx;
};

std::string pathFromUri(const char* uri) {
    if (!uri) return {};
    std::string value(uri);
    const std::string scheme = "file://";
    if (value.rfind(scheme, 0) == 0) value.erase(0, scheme.size());
    return value;
}

bool contains(const std::vector<std::string>& names, const std::string& name) {
    for (const auto& candidate : names) {
        if (candidate == name) return true;
    }
    return false;
}

/*
 * De-duplicates on the way in. A repeated name would otherwise make entitiesFor visit the
 * same entity twice in one apply pass; the second visit would read back the material the
 * first visit just installed and record it as "original", corrupting the unwind stack for
 * every feature sharing it.
 */
std::vector<std::string> collect(const char* const* names, size_t count) {
    std::vector<std::string> result;
    if (!names) return result;
    for (size_t i = 0; i < count; ++i) {
        if (names[i] && !contains(result, names[i])) result.emplace_back(names[i]);
    }
    return result;
}

/* Layer 0 is drawn and picked; layer 1 is neither. */
constexpr uint8_t kLayerVisible = 0x1;
constexpr uint8_t kLayerHidden = 0x2;
constexpr uint8_t kLayerMask = kLayerVisible | kLayerHidden;

/* A neutral bone-pale shell. Deliberately not the structure's own colour — see §26.3. */
constexpr float kGhostRed = 0.82f;
constexpr float kGhostGreen = 0.80f;
constexpr float kGhostBlue = 0.78f;

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
    std::vector<std::string> hiddenNodes;
    std::vector<std::string> ghostedNodes;
    std::vector<HighlightGroup> highlights;
    float ghostAlpha = 1.0f;
    MaterialInstance* ghostMaterial = nullptr;
    std::vector<uint8_t> outlineMaterial;
    anatomy::OutlinePass* outline = nullptr;
    bool outlineFailed = false;

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
    if (r->outline) r->outline->resize(width, height);
    // Layer 1 is neither drawn nor picked; ar_set_hidden moves renderables onto it.
    r->view->setVisibleLayers(kLayerMask, kLayerVisible);
    // A ghost is context the user can still tap (§26.2: the ghosted set is the isolated
    // structure's neighbours, which is exactly what navigation taps on next) — Filament
    // disables picking transparent renderables by default, which would otherwise make a
    // ghosted structure indistinguishable from a hidden one to ar_pick_at. The cost is one
    // extra depth pass.
    r->view->setTransparentPickingEnabled(true);
    frameAsset(r);

    QueuedEvent ready;
    ready.type = AR_EVENT_READY;
    r->push(std::move(ready));
}

MaterialInstance* ghostMaterial(ar_renderer* r) {
    if (r->ghostMaterial) return r->ghostMaterial;

    // The ubershader archive already carries a BLEND variant; asking the provider for one
    // is the whole of "transparent material variants" (§26.1). MaterialKey is a hashed POD
    // of bitfields, so it must be zero-initialised or the padding poisons the lookup.
    filament::gltfio::MaterialKey key = {};
    key.alphaMode = filament::gltfio::AlphaMode::BLEND;
    key.doubleSided = false;
    key.unlit = false;

    filament::gltfio::UvMap uvmap = {};
    filament::gltfio::constrainMaterial(&key, &uvmap);

    MaterialInstance* instance = r->materials->createMaterialInstance(&key, &uvmap, "ghost", nullptr);
    if (!instance) return nullptr;

    // The ubershader defaults to a fully metallic surface, which renders a translucent
    // shell as a dark smear. Both must be set explicitly.
    const Material* material = instance->getMaterial();
    if (material->hasParameter("metallicFactor")) instance->setParameter("metallicFactor", 0.0f);
    if (material->hasParameter("roughnessFactor")) instance->setParameter("roughnessFactor", 0.8f);

    r->ghostMaterial = instance;
    return instance;
}

/* Returns every entity whose node name is in `names`. */
std::vector<Entity> entitiesFor(ar_renderer* r, const std::vector<std::string>& names) {
    std::vector<Entity> result;
    for (const auto& name : names) {
        // A fixed-size buffer would silently drop matches beyond its capacity — for
        // ar_set_hidden that means a leftover fragment of a structure that was supposed
        // to disappear entirely. Passing a null buffer returns the exact match count
        // without writing, so ask for that first and then fetch all of them.
        const size_t matchCount = r->asset->getEntitiesByName(name.c_str(), nullptr, 0);
        if (matchCount == 0) continue;
        std::vector<Entity> found(matchCount);
        const size_t written = r->asset->getEntitiesByName(name.c_str(), found.data(), matchCount);
        result.insert(result.end(), found.begin(), found.begin() + written);
    }
    return result;
}

bool isHighlighted(const ar_renderer* r, const std::string& name) {
    for (const auto& group : r->highlights) {
        if (contains(group.nodes, name)) return true;
    }
    return false;
}

/*
 * The outline pass, made the first time something is highlighted. A material that is
 * missing or will not load is reported once; highlights go on being tinted without it.
 */
anatomy::OutlinePass* outlinePass(ar_renderer* r) {
    if (r->outline) return r->outline;
    if (r->outlineFailed) return nullptr;
    r->outline = anatomy::OutlinePass::create(r->engine, r->outlineMaterial.data(),
                                              r->outlineMaterial.size(), r->camera,
                                              kLayerMask, kLayerVisible);
    if (!r->outline) {
        r->outlineFailed = true;
        r->fail("outline-unavailable", "the outline material did not load");
        return nullptr;
    }
    r->outline->resize(r->width, r->height);
    return r->outline;
}

/* Tells the outline pass what the highlight groups mean in entities. Same outline, same mask. */
void applyOutline(ar_renderer* r) {
    std::vector<anatomy::OutlineSpec> specs;
    for (const auto& group : r->highlights) {
        auto entities = entitiesFor(r, group.nodes);
        if (entities.empty()) continue;
        auto same = std::find_if(specs.begin(), specs.end(), [&](const anatomy::OutlineSpec& spec) {
            return spec.color == group.outline && spec.widthPx == group.outlineWidthPx;
        });
        if (same == specs.end()) {
            specs.push_back({std::move(entities), group.outline, group.outlineWidthPx});
        } else {
            same->entities.insert(same->entities.end(), entities.begin(), entities.end());
        }
    }
    if (specs.empty()) {
        if (r->outline) r->outline->setGroups({});
        return;
    }
    if (auto* pass = outlinePass(r)) pass->setGroups(specs);
}

/*
 * Resolves the three declared sets to one state per primitive, and applies it (§26.4).
 *
 * Called after every change, so no two features race over setMaterialInstanceAt. Highlight
 * beats ghost: a structure the app is pointing at is not also faded out.
 *
 * Clear-and-reapply rather than the per-primitive diff §26.4 describes: the ghosted set is
 * bounded by design (§26.2), so a diff would be machinery bought before anything needs it.
 */
void applyAppearance(ar_renderer* r) {
    if (!r->engine || !r->asset) return;
    auto& rm = r->engine->getRenderableManager();

    // Unwind everything first, so the result depends on the sets and not on call order.
    for (const auto& entry : r->swapped) {
        auto instance = rm.getInstance(entry.entity);
        if (instance) rm.setMaterialInstanceAt(instance, entry.primitiveIndex, entry.original);
    }
    r->swapped.clear();
    for (auto* material : r->highlightInstances) r->engine->destroy(material);
    r->highlightInstances.clear();

    for (size_t i = 0, count = r->asset->getEntityCount(); i < count; ++i) {
        auto instance = rm.getInstance(r->asset->getEntities()[i]);
        if (instance) rm.setLayerMask(instance, kLayerMask, kLayerVisible);
    }

    for (const auto& entity : entitiesFor(r, r->hiddenNodes)) {
        auto instance = rm.getInstance(entity);
        if (instance) rm.setLayerMask(instance, kLayerMask, kLayerHidden);
    }

    MaterialInstance* ghost = nullptr;
    if (!r->ghostedNodes.empty()) {
        ghost = ghostMaterial(r);
        // Guarded the same way the highlight path guards its baseColorFactor set below,
        // even though this ubershader variant is always known to expose it — one standard
        // of trust for a parameter set from this function, not two.
        if (ghost && ghost->getMaterial()->hasParameter("baseColorFactor")) {
            ghost->setParameter("baseColorFactor", RgbaType::sRGB,
                                float4{kGhostRed, kGhostGreen, kGhostBlue, r->ghostAlpha});
        }
    }
    if (ghost) {
        for (const auto& entity : entitiesFor(r, r->ghostedNodes)) {
            const auto nodeEntry = r->entityToNode.find(entity.getId());
            // Highlight beats ghost, whichever group the node is highlighted in.
            if (nodeEntry != r->entityToNode.end() && isHighlighted(r, nodeEntry->second)) continue;
            auto instance = rm.getInstance(entity);
            if (!instance) continue;
            for (size_t p = 0, primitives = rm.getPrimitiveCount(instance); p < primitives; ++p) {
                MaterialInstance* original = rm.getMaterialInstanceAt(instance, p);
                if (!original) continue;
                // Belt-and-braces: collect() de-duplicates names, but if a primitive were
                // ever visited twice regardless, re-recording an already-installed ghost as
                // "original" would make it permanent. Skip rather than corrupt the unwind.
                if (original == ghost) continue;
                r->swapped.push_back({entity, p, original});
                rm.setMaterialInstanceAt(instance, p, ghost);
            }
        }
    }

    for (const auto& group : r->highlights) {
        for (const auto& entity : entitiesFor(r, group.nodes)) {
            auto instance = rm.getInstance(entity);
            if (!instance) continue;
            for (size_t p = 0, primitives = rm.getPrimitiveCount(instance); p < primitives; ++p) {
                MaterialInstance* original = rm.getMaterialInstanceAt(instance, p);
                if (!original) continue;
                const Material* material = original->getMaterial();
                MaterialInstance* replacement = MaterialInstance::duplicate(original);
                if (material->hasParameter("baseColorFactor")) {
                    replacement->setParameter("baseColorFactor", RgbaType::sRGB, group.tint);
                }
                if (material->hasParameter("emissiveFactor")) {
                    replacement->setParameter("emissiveFactor", group.emissive);
                }
                r->swapped.push_back({entity, p, original});
                r->highlightInstances.push_back(replacement);
                rm.setMaterialInstanceAt(instance, p, replacement);
            }
        }
    }
    applyOutline(r);
}

void releaseAsset(ar_renderer* r) {
    if (!r->asset) return;
    r->hiddenNodes.clear();
    r->ghostedNodes.clear();
    r->highlights.clear();
    applyAppearance(r);
    if (r->ghostMaterial) {
        r->engine->destroy(r->ghostMaterial);
        r->ghostMaterial = nullptr;
    }
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
    // Filament does not clear the swap chain unless asked: with no skybox, a pixel the model
    // does not cover keeps whatever an earlier frame left there. Seen on Android as earlier
    // frames showing through around an isolated structure once the camera moved; the default
    // is the engine's, so it is the same here.
    // Alpha 0: an outline mask is cleared with this colour too, and must start transparent.
    // The layer is opaque, so the screen is black either way.
    r->renderer->setClearOptions({.clearColor = {0.0f, 0.0f, 0.0f, 0.0f}, .clear = true});
    r->scene = r->engine->createScene();
    r->view = r->engine->createView();
    r->cameraEntity = utils::EntityManager::get().create();
    r->camera = r->engine->createCamera(r->cameraEntity);

    r->view->setScene(r->scene);
    r->view->setCamera(r->camera);
    // Post-processing stays on: it carries tone mapping, and without it the linear HDR
    // values a physically-lit scene produces blow out to white. Picking uses its own pass
    // and is unaffected — the contract tests cover that.
    r->view->setPostProcessingEnabled(true);

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
        delete r->outline;
        r->outline = nullptr;
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

void ar_set_outline_material(ar_renderer_ref r, const uint8_t* bytes, size_t size) {
    if (!r || !bytes || size == 0) return;
    r->outlineMaterial.assign(bytes, bytes + size);
}

void ar_attach_headless(ar_renderer_ref r, uint32_t width, uint32_t height) {
    if (!r || !r->engine) return;
    // Offscreen rendering has no display to pace against. Left at the default, Filament
    // drops most frames of a tight render loop, and a picking readback never completes.
    r->renderer->setDisplayInfo({.refreshRate = 0.0f});
    configureSurface(r, r->engine->createSwapChain(width, height), width, height);
}

void ar_attach_layer(ar_renderer_ref r, void* layer, uint32_t width, uint32_t height,
                     float refresh_hz) {
    if (!r || !r->engine) return;
    r->renderer->setDisplayInfo({.refreshRate = refresh_hz > 0.0f ? refresh_hz : 60.0f});
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

void ar_add_highlight(ar_renderer_ref r, const char* const* nodeNames, size_t count,
                      const float* tintRgba, const float* emissiveRgb,
                      const float* outlineRgba, float outlineWidthPx) {
    if (!r || !r->engine || !r->asset || !tintRgba || !emissiveRgb || !outlineRgba) return;

    HighlightGroup group;
    group.nodes = collect(nodeNames, count);
    group.tint = float4{tintRgba[0], tintRgba[1], tintRgba[2], tintRgba[3]};
    group.emissive = float3{emissiveRgb[0], emissiveRgb[1], emissiveRgb[2]};
    group.outline = float4{outlineRgba[0], outlineRgba[1], outlineRgba[2], outlineRgba[3]};
    group.outlineWidthPx = outlineWidthPx;

    // A node in two groups would be visited twice in one apply pass, and the second visit
    // would record the first's replacement as the "original" — the same corruption collect()
    // guards against within a group. Adding a node therefore moves it.
    for (auto& existing : r->highlights) {
        existing.nodes.erase(
            std::remove_if(existing.nodes.begin(), existing.nodes.end(),
                           [&](const std::string& name) { return contains(group.nodes, name); }),
            existing.nodes.end());
    }
    if (!group.nodes.empty()) r->highlights.push_back(std::move(group));
    applyAppearance(r);
}

void ar_clear_highlight(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->highlights.clear();
    applyAppearance(r);
}

void ar_set_hidden(ar_renderer_ref r, const char* const* nodeNames, size_t count) {
    if (!r || !r->engine || !r->asset) return;
    r->hiddenNodes = collect(nodeNames, count);
    applyAppearance(r);
}

void ar_clear_hidden(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->hiddenNodes.clear();
    applyAppearance(r);
}

void ar_set_opacity(ar_renderer_ref r, const char* const* nodeNames, size_t count, float alpha) {
    if (!r || !r->engine || !r->asset) return;
    r->ghostedNodes = collect(nodeNames, count);
    // An alpha of 0 would be a structure that is invisible yet still drawn and still costs
    // a blended draw — a worse way to get nothing on screen than ar_set_hidden. Clamped to
    // a valid range.
    r->ghostAlpha = alpha < 0.0f ? 0.0f : (alpha > 1.0f ? 1.0f : alpha);
    applyAppearance(r);
}

void ar_clear_opacity(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->ghostedNodes.clear();
    applyAppearance(r);
}

bool ar_nodes_bounds(ar_renderer_ref r, const char* const* node_names, size_t count,
                     float out_center[3], float out_half_extent[3]) {
    if (!r || !r->asset) return false;

    Aabb total;
    bool any = false;
    auto add = [&](float3 lower, float3 upper) {
        if (!any) { total.min = lower; total.max = upper; any = true; return; }
        total.min = min(total.min, lower);
        total.max = max(total.max, upper);
    };

    if (count == 0) {
        const Aabb whole = r->asset->getBoundingBox();
        add(whole.min, whole.max);
    } else {
        auto& renderables = r->engine->getRenderableManager();
        auto& transforms = r->engine->getTransformManager();
        for (size_t i = 0; i < count; ++i) {
            const size_t matches = r->asset->getEntitiesByName(node_names[i], nullptr, 0);
            std::vector<Entity> found(matches);
            r->asset->getEntitiesByName(node_names[i], found.data(), matches);
            for (Entity entity : found) {
                const auto ri = renderables.getInstance(entity);
                const auto ti = transforms.getInstance(entity);
                if (!ri || !ti) continue;
                const Box world = rigidTransform(renderables.getAxisAlignedBoundingBox(ri),
                                                 transforms.getWorldTransform(ti));
                add(world.center - world.halfExtent, world.center + world.halfExtent);
            }
        }
    }
    if (!any) return false;

    const float3 center = (total.min + total.max) * 0.5f;
    const float3 half = (total.max - total.min) * 0.5f;
    out_center[0] = center.x; out_center[1] = center.y; out_center[2] = center.z;
    out_half_extent[0] = half.x; out_half_extent[1] = half.y; out_half_extent[2] = half.z;
    return true;
}

void ar_set_camera(ar_renderer_ref r, const float eye[3], const float target[3],
                   double near_plane, double far_plane) {
    if (!r || !r->camera) return;
    r->camera->lookAt({eye[0], eye[1], eye[2]}, {target[0], target[1], target[2]}, {0.0f, 1.0f, 0.0f});
    const double aspect = r->height == 0 ? 1.0 : double(r->width) / double(r->height);
    r->camera->setProjection(45.0, aspect, near_plane, far_plane, Camera::Fov::VERTICAL);
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

namespace {

/* Masks first, so the overlay reads this frame's; the overlay last, over the model. */
void drawViews(ar_renderer* r) {
    if (r->outline) r->outline->renderMasks(r->renderer);
    r->renderer->render(r->view);
    if (r->outline) r->outline->renderOverlay(r->renderer);
}

} // namespace

bool ar_render_frame(ar_renderer_ref r, uint64_t vsync_nanos) {
    if (!r || !r->engine || !r->swapChain) return false;
    // Filament refuses a frame while too many are already in flight. Reporting that back
    // lets a caller that needs frames to actually land — a contract test waiting on a
    // picking readback — know the difference between a drawn frame and a skipped one.
    if (!r->renderer->beginFrame(r->swapChain, vsync_nanos)) return false;
    drawViews(r);
    r->renderer->endFrame();
    return true;
}

int64_t ar_gpu_frame_nanos(ar_renderer_ref r) {
    if (!r || !r->renderer) return 0;
    auto history = r->renderer->getFrameInfoHistory(8);
    for (auto it = history.rbegin(); it != history.rend(); ++it) {
        if (it->denoisedGpuFrameDuration > 0) return it->denoisedGpuFrameDuration;
    }
    return 0;
}

namespace {

bool readTaskVmInfo(task_vm_info_data_t* info) {
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    return task_info(mach_task_self(), TASK_VM_INFO, reinterpret_cast<task_info_t>(info), &count)
        == KERN_SUCCESS;
}

} // namespace

int64_t ar_resident_bytes(void) {
    task_vm_info_data_t info;
    return readTaskVmInfo(&info) ? int64_t(info.resident_size) : 0;
}

int64_t ar_footprint_bytes(void) {
    task_vm_info_data_t info;
    return readTaskVmInfo(&info) ? int64_t(info.phys_footprint) : 0;
}

void ar_wait_for_gpu(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->engine->flushAndWait();
}

namespace {

/*
 * One frame being read back. On the heap and owned by whoever finishes last: Filament holds
 * a pointer to `pixels` until the GPU answers, which can be after ar_capture_frame has given
 * up, so neither the caller's buffer nor anything on its stack may be what Filament writes to.
 */
struct Capture {
    enum State : int { kPending = 0, kDone = 1, kAbandoned = 2 };
    std::vector<uint8_t> pixels;
    std::atomic<int> state{kPending};
};

} // namespace

bool ar_capture_frame(ar_renderer_ref r, uint8_t* out, size_t capacity) {
    if (!r || !r->engine || !r->swapChain || !out) return false;
    const size_t size = size_t(r->width) * size_t(r->height) * 4;
    if (size == 0 || capacity < size) return false;

    auto* capture = new Capture();
    capture->pixels.resize(size);
    bool asked = false;
    for (int attempt = 0; attempt < 16 && capture->state.load() == Capture::kPending; ++attempt) {
        if (r->renderer->beginFrame(r->swapChain, 0)) {
            drawViews(r);
            if (!asked) {
                backend::PixelBufferDescriptor descriptor(
                    capture->pixels.data(), size,
                    backend::PixelDataFormat::RGBA, backend::PixelDataType::UBYTE,
                    &immediateHandler(),
                    [](void*, size_t, void* user) {
                        auto* finished = static_cast<Capture*>(user);
                        int expected = Capture::kPending;
                        // Abandoned already: nobody is waiting, so this is the last owner.
                        if (!finished->state.compare_exchange_strong(expected, Capture::kDone)) {
                            delete finished;
                        }
                    },
                    capture);
                r->renderer->readPixels(0, 0, r->width, r->height, std::move(descriptor));
                asked = true;
            }
            r->renderer->endFrame();
        }
        r->engine->flushAndWait();
    }

    int expected = Capture::kPending;
    if (capture->state.compare_exchange_strong(expected, Capture::kAbandoned)) {
        // Never asked means Filament holds nothing and will never call back.
        if (!asked) delete capture;
        return false;
    }
    std::copy(capture->pixels.begin(), capture->pixels.end(), out);
    delete capture;
    return true;
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

# Transparent Material Variants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the renderer two working verbs — `setVisibility` and `setOpacity` — so the layer/system panel (prototype screen 07) can hide systems and ghost a selected structure's context, and delete the two verbs that were policy in disguise.

**Architecture:** Hiding is a Filament layer mask, not a material. Ghosting is a *single* `BLEND`-keyed `MaterialInstance` obtained from the `UbershaderProvider` already in use and shared by every ghosted primitive. Both platforms keep three declared sets — hidden, ghosted, highlighted — and resolve them to one desired state per primitive on every change, so no two features fight over `setMaterialInstanceAt`. Isolation itself is composed in `feature-atlas`, where §25.1's taxonomy is reachable.

**Tech Stack:** Kotlin Multiplatform, Filament 1.75.1 (Java bindings on Android, C++ through an Objective-C++ shim on iOS), gltfio `UbershaderProvider`, Room, kotlinx-coroutines.

**Spec:** `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` — §26 is this work; §4, §15, §21.4, §25.1 and §26 are the sections it argues from.

## Global Constraints

- Filament is pinned to **1.75.1** on both platforms. Do not change the version.
- **No new build artifacts.** No `.mat` files, no `matc`, no new build stage. That is §12, which §26 explicitly splits out of this work.
- §4: Kotlin owns all state. The renderer may hold only what the app can replay.
- Node names cross the C boundary, never `StructureId`s (`ios-renderer/include/anatomy_renderer.h`).
- `FakeAnatomyRenderer` and `AnatomyRendererContract` live in **`commonMain`**, not `commonTest`, and must not gain a `kotlin.test` dependency (§20.3).
- `HighlightStyle` is untouched. `outlineStyle` keeps carrying `DASHED` unhonoured.
- Frame budget is **16.7 ms** (§6.1). Measure by interleaving variants inside one session and repeating the pair — never by comparing runs taken at different times (§25.4).
- Run `./gradlew --stop` between long sessions; daemons exhaust this machine's memory.

## File Structure

| File | Responsibility |
|---|---|
| `shared/renderer-api/src/commonMain/.../AnatomyRenderer.kt` | Interface: `isolate` and `setSystemVisibility` removed, `setVisibility` added |
| `shared/renderer-api/src/commonMain/.../FakeAnatomyRenderer.kt` | Records the three declared sets for screen tests |
| `shared/renderer-api/src/commonMain/.../AnatomyRendererContract.kt` | Four new shared verifications, including "a hidden structure is not pickable" |
| `ios-renderer/include/anatomy_renderer.h` | Four new C entry points |
| `ios-renderer/src/AnatomyRenderer.mm` | Appearance resolver, layer masks, shared ghost instance |
| `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt` | Marshals the sets across cinterop |
| `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt` | The same resolver in Kotlin |
| `shared/feature-atlas/src/commonMain/.../IsolationPolicy.kt` | **New.** Pure: taxonomy + selection → the three sets |

---

### Task 1: The interface loses two verbs and gains one

**Files:**
- Modify: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/AnatomyRenderer.kt`
- Modify: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRenderer.kt`
- Modify: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/AnatomyRendererContract.kt`
- Modify: `shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.android.kt`
- Modify: `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.kt`
- Test: `shared/renderer-api/src/commonTest/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRendererTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `AnatomyRenderer.setVisibility(structures: Set<StructureId>, visible: Boolean)`; `FakeAnatomyRenderer.hidden: Set<StructureId>`, `.ghosted: Set<StructureId>`, `.ghostAlpha: Float`; contract methods `verifyHidingAStructureRemovesItFromPicking()`, `verifyShowingAHiddenStructureRestoresPicking()`, `verifyGhostingIsReversible()`, `verifyHighlightAndGhostResolveInEitherOrder()`.

- [ ] **Step 1: Write the failing test**

Replace the body of `holds_isolation_and_highlight_state_so_a_lost_surface_can_be_replayed` in `FakeAnatomyRendererTest.kt` and add a second test:

```kotlin
@Test
fun holds_the_three_declared_sets_so_a_lost_surface_can_be_replayed() = runTest {
    val renderer = FakeAnatomyRenderer()
    val femur = StructureId("a02-5-04-001-femur-left")
    val tibia = StructureId("a02-5-06-001-tibia-left")

    renderer.setVisibility(setOf(tibia), visible = false)
    renderer.setOpacity(setOf(femur), alpha = 0.25f)

    assertEquals(setOf(tibia), renderer.hidden)
    assertEquals(setOf(femur), renderer.ghosted)
    assertEquals(0.25f, renderer.ghostAlpha)
}

@Test
fun showing_a_structure_removes_it_from_the_hidden_set() = runTest {
    val renderer = FakeAnatomyRenderer()
    val tibia = StructureId("a02-5-06-001-tibia-left")

    renderer.setVisibility(setOf(tibia), visible = false)
    renderer.setVisibility(setOf(tibia), visible = true)

    assertEquals(emptySet(), renderer.hidden)
}

@Test
fun opacity_of_one_clears_the_ghost_set() = runTest {
    val renderer = FakeAnatomyRenderer()
    val femur = StructureId("a02-5-04-001-femur-left")

    renderer.setOpacity(setOf(femur), alpha = 0.25f)
    renderer.setOpacity(setOf(femur), alpha = 1.0f)

    assertEquals(emptySet(), renderer.ghosted)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:renderer-api:testAndroidHostTest`
Expected: FAIL — `unresolved reference: setVisibility`, `unresolved reference: hidden`.

- [ ] **Step 3: Change the interface**

In `AnatomyRenderer.kt`, delete the `setSystemVisibility` and `isolate` declarations, delete the now-unused `SystemId` import, and put this in their place:

```kotlin
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
     * size of [structures] costs nothing on the CPU.
     */
    fun setOpacity(structures: Set<StructureId>, alpha: Float)
```

- [ ] **Step 4: Update the fake**

In `FakeAnatomyRenderer.kt`, delete the `isolated` and `hiddenSystems` properties, delete the `setSystemVisibility` and `isolate` overrides, delete the `SystemId` import, and add:

```kotlin
    var hidden: Set<StructureId> = emptySet()
        private set
    var ghosted: Set<StructureId> = emptySet()
        private set
    var ghostAlpha: Float = 1f
        private set
```

```kotlin
    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        hidden = if (visible) hidden - structures else hidden + structures
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        ghosted = if (alpha >= 1f) ghosted - structures else ghosted + structures
        if (alpha < 1f) ghostAlpha = alpha
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :shared:renderer-api:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 6: Keep both platform implementations compiling**

In **both** `FilamentAnatomyRenderer.android.kt` and `FilamentAnatomyRenderer.kt` (iosMain), delete the `setSystemVisibility` and `isolate` overrides and the `SystemId` import, and add:

```kotlin
    override fun setVisibility(structures: Set<StructureId>, visible: Boolean): Unit =
        TODO("Task 2-4: appearance resolver")
```

Leave the existing `setOpacity` `TODO()` in place; the platform tasks replace both together.

- [ ] **Step 7: Add the contract verifications**

In `AnatomyRendererContract.kt`, add these four methods after `verifyHighlightingALoadedStructureIsAccepted`. No platform wires them up yet — the later tasks do, one platform at a time, so every commit stays green.

```kotlin
    suspend fun verifyHidingAStructureRemovesItFromPicking() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setVisibility(setOf(hitStructure), visible = false)
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            null,
            (picked as RendererEvent.Picked).structure,
            "a hidden structure was picked",
        )
    }

    suspend fun verifyShowingAHiddenStructureRestoresPicking() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setVisibility(setOf(hitStructure), visible = false)
        settle(renderer)
        renderer.setVisibility(setOf(hitStructure), visible = true)
        pickHit(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            hitStructure,
            (picked as RendererEvent.Picked).structure,
            "picked structure after being shown again",
        )
    }

    suspend fun verifyGhostingIsReversible() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // Like highlight, ghosting is asserted through survival and through picking, which
        // the contract can see; colour, which it cannot see, is left to the eye.
        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        settle(renderer)
        renderer.setOpacity(setOf(hitStructure), alpha = 1.0f)
        pickHit(renderer)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported while ghosting")

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(
            hitStructure,
            (picked as RendererEvent.Picked).structure,
            "a ghosted structure must stay pickable",
        )
    }

    suspend fun verifyHighlightAndGhostResolveInEitherOrder() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        renderer.highlight(setOf(hitStructure), HIGHLIGHT)
        settle(renderer)

        renderer.highlight(emptySet(), HIGHLIGHT)
        renderer.setOpacity(setOf(hitStructure), alpha = 1.0f)
        renderer.highlight(setOf(hitStructure), HIGHLIGHT)
        renderer.setOpacity(setOf(hitStructure), alpha = 0.25f)
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported resolving highlight over ghost")
    }
```

- [ ] **Step 8: Run the whole module and both platform compilations**

Run: `./gradlew :shared:renderer-api:allTests :shared:renderer-filament:compileTestKotlinIosSimulatorArm64 :shared:renderer-filament:compileAndroidHostTestSources`
Expected: PASS. Any call site in `feature-atlas`, `androidApp` or `shared` that used `isolate`/`setSystemVisibility` must be fixed here; there should be none, because §21.5 left both throwing.

- [ ] **Step 9: Commit**

```bash
git add shared/renderer-api shared/renderer-filament
git commit -m "feat(renderer-api): replace isolate and setSystemVisibility with setVisibility

Both named domain concepts the renderer cannot see. Resolving a system or
a parent group to a set of structures needs core-data, so per §4 the app
does it and the renderer is told only the result.

The contract gains the four cases in §26.6; no platform wires them up yet."
```

---

### Task 2: The C seam

**Files:**
- Modify: `ios-renderer/include/anatomy_renderer.h`
- Modify: `ios-renderer/src/AnatomyRenderer.mm`

**Interfaces:**
- Consumes: Task 1's decision that node names carry hidden/ghosted sets.
- Produces: `ar_set_hidden`, `ar_clear_hidden`, `ar_set_opacity`, `ar_clear_opacity`.

This task has no automated test of its own — the shim is only reachable from Kotlin, and Task 3 is where it gets exercised. It ends at "compiles and links".

- [ ] **Step 1: Declare the entry points**

In `anatomy_renderer.h`, immediately after `ar_clear_highlight`:

```c
/*
 * Hides nodes outright: not drawn, and not picked.
 *
 * Hiding is a layer mask rather than a material, so it costs nothing per node and removes
 * draws rather than adding them. Each call replaces the previous hidden set.
 */
void ar_set_hidden(ar_renderer_ref renderer, const char* const* node_names, size_t count);
void ar_clear_hidden(ar_renderer_ref renderer);

/*
 * Ghosts nodes at `alpha`.
 *
 * Every ghosted node shares one blended material instance, so a ghost is a uniform pale
 * shell rather than a faded copy of each node's own colour — Filament exposes no way to
 * read a material instance's parameters back, and the uniform shell is the better look
 * anyway. Each call replaces the previous ghosted set.
 */
void ar_set_opacity(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                    float alpha);
void ar_clear_opacity(ar_renderer_ref renderer);
```

- [ ] **Step 2: Add the state**

In `AnatomyRenderer.mm`, extend the anonymous namespace above `struct ar_renderer`:

```cpp
/* Layer 0 is drawn and picked; layer 1 is neither. */
constexpr uint8_t kLayerVisible = 0x1;
constexpr uint8_t kLayerHidden = 0x2;
constexpr uint8_t kLayerMask = kLayerVisible | kLayerHidden;

/* A neutral bone-pale shell. Deliberately not the structure's own colour — see §26.3. */
constexpr float kGhostRed = 0.82f;
constexpr float kGhostGreen = 0.80f;
constexpr float kGhostBlue = 0.78f;
```

Add to `struct ar_renderer`, next to `swapped` and `highlightInstances`:

```cpp
    std::vector<std::string> hiddenNodes;
    std::vector<std::string> ghostedNodes;
    std::vector<std::string> highlightedNodes;
    float ghostAlpha = 1.0f;
    int32_t highlightArgb = 0;
    float highlightLift = 0.0f;
    MaterialInstance* ghostMaterial = nullptr;
```

- [ ] **Step 3: Build the ghost material**

Add above `clearHighlightInternal`:

```cpp
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
```

- [ ] **Step 4: Replace `clearHighlightInternal` with a resolver**

Delete `clearHighlightInternal` and put this in its place. It is deliberately clear-and-reapply rather than a diff: the ghosted set is bounded by design (§26.2), and a diff would be machinery bought before anything needs it.

```cpp
bool contains(const std::vector<std::string>& names, const std::string& name) {
    for (const auto& candidate : names) {
        if (candidate == name) return true;
    }
    return false;
}

/* Returns every entity whose node name is in `names`. */
std::vector<Entity> entitiesFor(ar_renderer* r, const std::vector<std::string>& names) {
    std::vector<Entity> result;
    for (const auto& name : names) {
        Entity found[8];
        const size_t matches = r->asset->getEntitiesByName(name.c_str(), found, 8);
        for (size_t m = 0; m < matches; ++m) result.push_back(found[m]);
    }
    return result;
}

/*
 * Resolves the three declared sets to one state per primitive, and applies it.
 *
 * Called after every change, so no two features race over setMaterialInstanceAt. Highlight
 * beats ghost: a structure the app is pointing at is not also faded out.
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
        if (ghost) {
            ghost->setParameter("baseColorFactor", RgbaType::sRGB,
                                float4{kGhostRed, kGhostGreen, kGhostBlue, r->ghostAlpha});
        }
    }
    if (ghost) {
        for (const auto& entity : entitiesFor(r, r->ghostedNodes)) {
            if (contains(r->highlightedNodes, r->entityToNode[entity.getId()])) continue;
            auto instance = rm.getInstance(entity);
            if (!instance) continue;
            for (size_t p = 0, primitives = rm.getPrimitiveCount(instance); p < primitives; ++p) {
                MaterialInstance* original = rm.getMaterialInstanceAt(instance, p);
                if (!original) continue;
                r->swapped.push_back({entity, p, original});
                rm.setMaterialInstanceAt(instance, p, ghost);
            }
        }
    }

    if (!r->highlightedNodes.empty()) {
        const float4 tint{
            float((r->highlightArgb >> 16) & 0xFF) / 255.0f,
            float((r->highlightArgb >> 8) & 0xFF) / 255.0f,
            float(r->highlightArgb & 0xFF) / 255.0f,
            float((uint32_t(r->highlightArgb) >> 24) & 0xFF) / 255.0f,
        };
        const float lift = r->highlightLift < 0.0f ? 0.0f : r->highlightLift;
        for (const auto& entity : entitiesFor(r, r->highlightedNodes)) {
            auto instance = rm.getInstance(entity);
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
                r->swapped.push_back({entity, p, original});
                r->highlightInstances.push_back(replacement);
                rm.setMaterialInstanceAt(instance, p, replacement);
            }
        }
    }
}
```

- [ ] **Step 5: Rewrite the entry points over the resolver**

Replace the bodies of `ar_set_highlight` and `ar_clear_highlight`, and add the four new functions beside them:

First add this helper to the **existing anonymous namespace** near the top of the file,
beside `pathFromUri` — the entry points below sit inside `extern "C"`, which is no place
for a new namespace:

```cpp
std::vector<std::string> collect(const char* const* names, size_t count) {
    std::vector<std::string> result;
    if (!names) return result;
    for (size_t i = 0; i < count; ++i) {
        if (names[i]) result.emplace_back(names[i]);
    }
    return result;
}
```

Then rewrite the entry points:

```cpp
void ar_set_highlight(ar_renderer_ref r, const char* const* nodeNames, size_t count,
                      int32_t outlineArgb, float luminanceShift) {
    if (!r || !r->engine || !r->asset) return;
    r->highlightedNodes = collect(nodeNames, count);
    r->highlightArgb = outlineArgb;
    r->highlightLift = luminanceShift;
    applyAppearance(r);
}

void ar_clear_highlight(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->highlightedNodes.clear();
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
    r->ghostAlpha = alpha;
    applyAppearance(r);
}

void ar_clear_opacity(ar_renderer_ref r) {
    if (!r || !r->engine) return;
    r->ghostedNodes.clear();
    applyAppearance(r);
}
```

- [ ] **Step 6: Restrict the view to the visible layer, and clean up**

In the function that creates the `View` (search for `r->view = r->engine->createView()`), add immediately after the viewport is set:

```cpp
    // Layer 1 is neither drawn nor picked; ar_set_hidden moves renderables onto it.
    r->view->setVisibleLayers(kLayerMask, kLayerVisible);
```

In `releaseAsset`, replace the `clearHighlightInternal(r);` call with:

```cpp
    r->hiddenNodes.clear();
    r->ghostedNodes.clear();
    r->highlightedNodes.clear();
    applyAppearance(r);
    if (r->ghostMaterial) {
        r->engine->destroy(r->ghostMaterial);
        r->ghostMaterial = nullptr;
    }
```

- [ ] **Step 7: Verify it compiles and links**

Run: `./gradlew :ios-renderer:assemble`
Expected: BUILD SUCCESSFUL. If `MaterialKey`, `UvMap` or `constrainMaterial` do not resolve, confirm `#include <gltfio/MaterialProvider.h>` is present — it already is at line 21.

- [ ] **Step 8: Commit**

```bash
git add ios-renderer
git commit -m "feat(ios-renderer): hide by layer mask, ghost by one shared blended instance

The three declared sets now resolve to one state per primitive on every
change, so hiding, ghosting and highlighting cannot race over the material
slot. Clear-and-reapply rather than a diff: the ghosted set is bounded by
design and a diff would be machinery bought early."
```

---

### Task 3: The iOS binding, and the first green proof

**Files:**
- Modify: `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.kt`
- Test: `shared/renderer-filament/src/iosTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: Task 1's `setVisibility`; Task 2's `ar_set_hidden`, `ar_clear_hidden`, `ar_set_opacity`, `ar_clear_opacity`.
- Produces: a working `AnatomyRenderer` on iOS. Nothing later depends on its internals.

This is the fast loop — the simulator, no device. It is where the design is first actually proved.

- [ ] **Step 1: Wire the four contract cases as failing tests**

At the bottom of `FilamentAnatomyRendererContractTest.kt`, beside the existing `@Test` lines:

```kotlin
    @Test fun hides_a_structure_from_picking() = runBlocking { verifyHidingAStructureRemovesItFromPicking() }
    @Test fun shows_a_hidden_structure_again() = runBlocking { verifyShowingAHiddenStructureRestoresPicking() }
    @Test fun reverses_a_ghost() = runBlocking { verifyGhostingIsReversible() }
    @Test fun resolves_highlight_over_ghost() = runBlocking { verifyHighlightAndGhostResolveInEitherOrder() }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:renderer-filament:iosSimulatorArm64Test`
Expected: the four new tests FAIL with `kotlin.NotImplementedError` from the `TODO()` Task 1 left in `setVisibility`. The six existing tests still pass.

- [ ] **Step 3: Implement the two verbs**

Add the imports `ar_clear_hidden`, `ar_clear_opacity`, `ar_set_hidden`, `ar_set_opacity` from `com.ptk.anatomypro.renderer.filament.cinterop`, then replace the `setVisibility` and `setOpacity` overrides:

```kotlin
    /**
     * The hidden set is held here, not in the shim, because the interface is declarative
     * per call — `setVisibility(x, false)` then `setVisibility(y, false)` must hide both —
     * while the C seam takes a whole set. Kotlin owning it is also what §4 requires.
     */
    private val hidden = mutableSetOf<StructureId>()
    private val ghosted = mutableSetOf<StructureId>()

    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        if (visible) hidden -= structures else hidden += structures
        val nodes = hidden.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) ar_clear_hidden(handle) else passNodes(nodes) { names, count ->
            ar_set_hidden(handle, names, count)
        }
        drain()
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        if (alpha >= 1f) ghosted -= structures else ghosted += structures
        val nodes = ghosted.flatMap { nodesByStructure[it].orEmpty() }
        if (nodes.isEmpty()) ar_clear_opacity(handle) else passNodes(nodes) { names, count ->
            ar_set_opacity(handle, names, count, alpha)
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
```

Add `import kotlinx.cinterop.CArrayPointer`. Then rewrite `highlight` to use `passNodes` too, so the marshalling exists once:

```kotlin
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
```

Finally, clear both sets wherever the asset is released — find `unloadPack` and add `hidden.clear()` and `ghosted.clear()` before it calls into the shim.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:iosSimulatorArm64Test`
Expected: all ten PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament
git commit -m "feat(renderer-filament): implement setVisibility and setOpacity on iOS

Kotlin holds the hidden and ghosted sets because the interface is
declarative per call while the C seam takes a whole set — and because §4
puts that state on the Kotlin side regardless.

All four of §26.6's cases pass against real Filament on the simulator,
including the one that matters: a hidden structure is not picked."
```

---

### Task 4: The same on Android

**Files:**
- Modify: `shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.android.kt`
- Test: `shared/renderer-filament/src/androidDeviceTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: Task 1's `setVisibility`.
- Produces: a working `AnatomyRenderer` on Android.

**This task needs a connected device.** The Android renderer contract is instrumented.

- [ ] **Step 1: Wire the four contract cases as failing tests**

Add the same four `@Test` lines as Task 3 Step 1 to the `androidDeviceTest` subclass, matching the coroutine builder already used in that file.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: the four new tests FAIL with `kotlin.NotImplementedError`.

- [ ] **Step 3: Index entities by structure**

In `indexNodes`, build a third map so appearance changes do not re-resolve names on every call:

```kotlin
    private var entitiesByStructure: Map<StructureId, IntArray> = emptyMap()
```

```kotlin
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
```

- [ ] **Step 4: Add the layer mask and the ghost material**

Add these constants to the existing `private companion object`, creating one if the class has none:

```kotlin
    private companion object {
        /** Layer 0 is drawn and picked; layer 1 is neither. */
        const val LAYER_VISIBLE = 0x1
        const val LAYER_HIDDEN = 0x2
        const val LAYER_MASK = LAYER_VISIBLE or LAYER_HIDDEN

        /** A neutral bone-pale shell — deliberately not the structure's own colour (§26.3). */
        const val GHOST_RED = 0.82f
        const val GHOST_GREEN = 0.80f
        const val GHOST_BLUE = 0.78f
    }
```

In `init`, after `view.isPostProcessingEnabled = true`:

```kotlin
        view.setVisibleLayers(LAYER_MASK, LAYER_VISIBLE)
        // A ghost is context the learner can still tap, and Filament disables transparent
        // picking by default — without this, swapping a primitive to the blended material
        // silently removes it from `pick`, making ghosted and hidden indistinguishable to a
        // tap (§26.3). The cost is one extra depth pass.
        view.setTransparentPickingEnabled(true)
```

**Both of these are corrections carried over from the iOS review, which caught them there
first — do not reship them here.** `setTransparentPickingEnabled` exists in the Android Java
binding at `View.java:867`; it is verified present, not a guess.

Add the ghost material, mirroring the shim:

```kotlin
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
```

Add `import com.google.android.filament.gltfio.MaterialProvider`.

- [ ] **Step 5: Replace the three mutators with a resolver**

Add the declared sets beside the existing `swapped`/`highlightInstances` fields:

```kotlin
    private val hidden = mutableSetOf<StructureId>()
    private val ghosted = mutableSetOf<StructureId>()
    private val highlighted = mutableSetOf<StructureId>()
    private var ghostAlpha = 1f
    private var highlightStyle: HighlightStyle? = null
```

Replace the `highlight` override and the two `TODO()`s with:

```kotlin
    override fun setVisibility(structures: Set<StructureId>, visible: Boolean) {
        if (visible) hidden -= structures else hidden += structures
        applyAppearance()
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) {
        if (alpha >= 1f) ghosted -= structures else ghosted += structures
        ghostAlpha = alpha
        applyAppearance()
    }

    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        highlighted.clear()
        highlighted += structures
        highlightStyle = style
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
            for (entity in entitiesByStructure[structure].orEmpty()) {
                val instance = renderables.getInstance(entity)
                if (instance != 0) renderables.setLayerMask(instance, LAYER_MASK, LAYER_HIDDEN)
            }
        }

        val ghost = if (ghosted.isEmpty()) null else ghostMaterial()
        if (ghost != null) {
            ghost.setParameter("baseColorFactor", GHOST_RED, GHOST_GREEN, GHOST_BLUE, ghostAlpha)
            for (structure in ghosted - highlighted) {
                for (entity in entitiesByStructure[structure].orEmpty()) {
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
                for (entity in entitiesByStructure[structure].orEmpty()) {
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
```

Delete the now-unused `clearHighlightInternal`, and in `releaseAsset` replace the call to it with:

```kotlin
        hidden.clear()
        ghosted.clear()
        highlighted.clear()
        applyAppearance()
        ghostMaterial?.let(engine::destroyMaterialInstance)
        ghostMaterial = null
        entitiesByStructure = emptyMap()
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: all ten PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/renderer-filament
git commit -m "feat(renderer-filament): implement setVisibility and setOpacity on Android

Same resolver as the shim, and the same shared blended instance from the
ubershader provider. clearHighlightInternal is gone: unwinding is now part
of resolving, so the three features cannot leave each other's swaps behind."
```

---

### Task 5: Isolation, composed where the taxonomy is

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/IsolationPolicy.kt`
- Test: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/IsolationPolicyTest.kt`

**Interfaces:**
- Consumes: `AtlasRepository.children`, `AtlasRepository.detail` (both exist); `StructureSummary.id`, `.isGroup`; `AnatomyRenderer.setVisibility`, `.setOpacity`.
- Produces: `IsolationPolicy.resolve(focus, siblings, everything): Isolation` and `data class Isolation(val focus: StructureId?, val ghosted: Set<StructureId>, val hidden: Set<StructureId>)`.

The renderer verbs are done; this is the policy §26.2 moved out of them. Pure and synchronous, so it needs neither a database nor a GPU.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.model.StructureId
import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationPolicyTest {

    private val femur = StructureId("a02-5-04-001-femur-left")
    private val tibia = StructureId("a02-5-06-001-tibia-left")
    private val fibula = StructureId("a02-5-07-001-fibula-left")
    private val scapula = StructureId("a02-4-01-001-scapula-left")
    private val everything = setOf(femur, tibia, fibula, scapula)

    @Test
    fun ghosts_the_siblings_and_hides_everything_else() {
        val isolation = IsolationPolicy.resolve(
            focus = tibia,
            siblings = setOf(femur, fibula),
            everything = everything,
        )

        assertEquals(tibia, isolation.focus)
        assertEquals(setOf(femur, fibula), isolation.ghosted)
        assertEquals(setOf(scapula), isolation.hidden)
    }

    @Test
    fun never_ghosts_or_hides_the_focus_itself() {
        val isolation = IsolationPolicy.resolve(
            focus = tibia,
            siblings = setOf(tibia, femur),
            everything = everything,
        )

        assertEquals(setOf(femur), isolation.ghosted)
        assertEquals(setOf(fibula, scapula), isolation.hidden)
    }

    @Test
    fun clearing_the_focus_shows_everything() {
        val isolation = IsolationPolicy.resolve(
            focus = null,
            siblings = emptySet(),
            everything = everything,
        )

        assertEquals(null, isolation.focus)
        assertEquals(emptySet(), isolation.ghosted)
        assertEquals(emptySet(), isolation.hidden)
    }

    @Test
    fun a_focus_with_no_siblings_hides_the_rest_and_ghosts_nothing() {
        val isolation = IsolationPolicy.resolve(
            focus = scapula,
            siblings = emptySet(),
            everything = everything,
        )

        assertEquals(emptySet(), isolation.ghosted)
        assertEquals(setOf(femur, tibia, fibula), isolation.hidden)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :shared:feature-atlas:testAndroidHostTest`
Expected: FAIL — `unresolved reference: IsolationPolicy`.

- [ ] **Step 3: Write the policy**

```kotlin
package com.ptk.anatomypro.feature.atlas

import com.ptk.anatomypro.core.model.StructureId

/**
 * What the renderer should show while one structure is being studied.
 *
 * [focus] stays fully opaque, [ghosted] becomes a pale shell, [hidden] is not drawn and
 * not picked. Every structure in the pack falls into exactly one of the three.
 */
data class Isolation(
    val focus: StructureId?,
    val ghosted: Set<StructureId>,
    val hidden: Set<StructureId>,
)

/**
 * Turns a selection into the three sets the renderer understands.
 *
 * This lives here rather than behind an `isolate` verb on `AnatomyRenderer` because it
 * needs §25.1's taxonomy, which lives in `core-data` — and §4 gives the renderer geometry
 * and picking and nothing else. See §26.2.
 *
 * Pure and synchronous: the caller does the querying, so this is testable without a
 * database and without a GPU.
 */
object IsolationPolicy {

    fun resolve(
        focus: StructureId?,
        siblings: Set<StructureId>,
        everything: Set<StructureId>,
    ): Isolation {
        if (focus == null) return Isolation(focus = null, ghosted = emptySet(), hidden = emptySet())

        val ghosted = siblings - focus
        return Isolation(
            focus = focus,
            ghosted = ghosted,
            hidden = everything - ghosted - focus,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :shared:feature-atlas:testAndroidHostTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-atlas
git commit -m "feat(feature-atlas): compose isolation from the taxonomy

The policy §26.2 took off the renderer. Pure and synchronous — the caller
supplies the sibling set, so this needs neither a database nor a GPU."
```

---

### Task 6: Measure it, and record what it cost

**Files:**
- Modify: `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` (§26.8)
- Modify: `docs/state-of-play.md`

**Interfaces:**
- Consumes: everything above.
- Produces: numbers, and a spec that stops saying "nothing is implemented".

§26.3 predicts ghosting is close to free on the CPU and that hiding *removes* draws. §23.9 measured `skeletal-body` at 16.8 ms against a 16.7 ms frame, so that prediction is the whole reason this design was chosen. It has to be checked.

- [ ] **Step 1: Measure, interleaved**

On the Pixel 10, in the Android harness, with `skeletal-body` loaded. Take `gpuFrameMillis` medians for three states, **interleaved inside one session and the sequence repeated at least three times** — never three separate runs, which §25.4 showed is how two earlier conclusions came out backwards:

1. everything visible, nothing ghosted (the §23.9 baseline);
2. one structure focused, siblings ghosted, the rest hidden;
3. everything visible, half the pack ghosted (the pathological case the design avoids, measured so the cost of getting it wrong is on record).

- [ ] **Step 2: Rewrite §26.8**

Replace the `### 26.8 Status` section with what is now true: the four contract cases pass on both platforms, the three measurements, and whether the uniform ghost reads acceptably against real anatomy or whether §26.3's fallback — carrying each structure's base colour in Kotlin from load time — is needed after all.

- [ ] **Step 3: Update `docs/state-of-play.md`**

Under "What to do next", transparency moves from designed to built. §12 becomes the next item. Update the test count in "What exists".

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: record what transparency cost, and what it did to the budget"
```

---

## Notes for the executor

- **Task 4 needs a connected device.** Task 3 does not — it is the simulator, and it is where the design is proved first. If no device is available, stop after Task 5 and leave Task 4's tests wired but failing rather than deleting them.
- **If the ubershader provider returns null for the BLEND key**, do not reach for `matc` — that is §12's problem and this plan explicitly excludes it. Report it instead: it would mean §26.1's central finding is wrong and the design needs revisiting.
- **Do not add outline rendering, dashed outlines, or a stencil pass.** That is §12, split out deliberately.

# Several Highlights at Once Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the renderer highlight several structures at the same time, each in its own style, so screen 12 can show the expected answer and the chosen one together.

**Architecture:** `AnatomyRenderer.highlight` changes from "this set, in this style" to "this map of structure to style", which replaces the whole highlight state as before. How a style becomes material parameters moves into shared Kotlin (`HighlightPaint`), so iOS and Android cannot drift and a negative luminance shift finally darkens instead of being clamped away. The iOS C seam swaps `ar_set_highlight` for `ar_add_highlight`, which appends one group of nodes with a ready-made tint; Kotlin clears and then adds a group per style. `AnatomyCanvas` takes the map.

**Tech Stack:** Kotlin Multiplatform, Filament 1.75.1 (Kotlin/Java on Android, Objective-C++ behind a C seam on iOS), kotlinx.cinterop, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md` §2 (screen 12 is built on colour and shape, no outlines), §9 (screen 12: expected and chosen distinguished by hue and shape), §10 test 1. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §4 (the renderer boundary), §12 (never by hue alone), §26.4 (one owner for the material slot). Decided 2026-10-06: the renderer is extended before the quiz plan is written.

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **The Android renderer contract is instrumented.** Run it on the emulator: `emulator -avd Medium_Phone_API_36.1`, then `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`. The emulator counts as a connected device; Filament renders on it.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **The renderer is told, never asked** (design spec §4). Kotlin owns all state; a renderer change goes through `AnatomyRendererContract` and holds on the fake, iOS and Android.
- **One owner for the material slot** (design spec §26.4). Hidden, ghosted and highlighted are resolved together in `applyAppearance`; nothing else calls `setMaterialInstanceAt`.
- **A primitive is visited once per apply pass.** Reading back a material this pass already installed and recording it as "original" makes the override permanent (the comment above `collect` in `AnatomyRenderer.mm`). A node therefore belongs to at most one highlight group.
- **The contract cannot see colour.** It proves a renderer accepts calls without faulting; what the colours are is proved by `HighlightPaintTest`, and what they look like is not proved here at all (see Task 5).
- **Out of scope:** outlines, solid or dashed (§12's shaders need `matc`, which is not installed); using `fillArgb` (today the tint is taken from `outlineArgb` on both platforms, and that is left as it is); any screen that shows two styles — screen 12 is the quiz plan's.

## Review Focus

1. **The same structure is highlighted twice in a row with different styles**: the second style is what shows, and the first leaves nothing behind. — Task 2 (fake), Task 3 and 4 (survival on each renderer).
2. **A highlight names a structure the pack does not contain** (a group, or an id from another pack): it is ignored, the others still highlight, nothing faults. — Task 2 contract.
3. **An empty map**: clears every highlight. — Task 2.
4. **A structure is ghosted and highlighted at once, in a map of several styles**: highlight still beats ghost for every highlighted structure, not only the first group's. — Task 3 and 4 by code; the existing interleave contract keeps passing.
5. **A fully negative shift (−1)**: the tint is black, not a negative colour. — Task 1.

## File Structure

| File | Responsibility |
|---|---|
| `shared/renderer-filament/src/commonMain/.../HighlightPaint.kt` | Create. A `HighlightStyle` as the two material parameters both platforms set. |
| `shared/renderer-filament/src/commonTest/.../HighlightPaintTest.kt` | Create. |
| `shared/renderer-api/.../AnatomyRenderer.kt` | Modify. `highlight(styles: Map<StructureId, HighlightStyle>)`, and the one-style form as an extension. |
| `shared/renderer-api/.../FakeAnatomyRenderer.kt` | Modify. Records `highlights`. |
| `shared/renderer-api/.../AnatomyRendererContract.kt` | Modify. `verifySeveralHighlightsAtOnceAreAccepted`. |
| `shared/renderer-api/src/commonTest/.../FakeAnatomyRendererTest.kt` | Modify. |
| `ios-renderer/include/anatomy_renderer.h`, `ios-renderer/src/AnatomyRenderer.mm` | Modify. `ar_add_highlight` replaces `ar_set_highlight`; highlight groups. |
| `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt` | Modify. |
| `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt` | Modify. |
| The two `FilamentAnatomyRendererContractTest.kt` files | Modify. One `@Test` each. |
| `shared/src/commonMain/.../AnatomyCanvas.kt` and both actuals, `AtlasTab.kt` | Modify. The canvas takes the map. (The actuals gain one import in Task 2.) |
| `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` | Modify. An addendum. |

Kotlin paths abbreviate `kotlin/com/ptk/anatomypro/renderer/filament/` and `kotlin/com/ptk/anatomypro/renderer/api/` as `.../`.

---

### Task 1: What a style paints

Both platforms turn a `HighlightStyle` into a base colour and an emissive colour, in two languages, and both clamp a negative `fillLuminanceShift` to zero. `HighlightTokens.Incorrect` carries −0.20, so "the wrong answer is darker" has never been drawn. One function, tested, fixes both.

**Files:**
- Create: `shared/renderer-filament/src/commonMain/.../HighlightPaint.kt`
- Test: `shared/renderer-filament/src/commonTest/.../HighlightPaintTest.kt`

**Interfaces:**
- Consumes: `HighlightStyle(outlineArgb, outlineWidthDp, outlineStyle, fillArgb, fillLuminanceShift)` from `renderer-api`.
- Produces:
  ```kotlin
  data class HighlightPaint(
      val red: Float, val green: Float, val blue: Float, val alpha: Float,
      val emissiveRed: Float, val emissiveGreen: Float, val emissiveBlue: Float,
  ) { companion object { fun of(style: HighlightStyle): HighlightPaint } }
  ```

- [ ] **Step 1: Write the failing tests**

`HighlightPaintTest.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.OutlineStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HighlightPaintTest {

    private fun style(argb: Long, shift: Float) = HighlightStyle(
        outlineArgb = argb.toInt(),
        outlineWidthDp = 2f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = argb.toInt(),
        fillLuminanceShift = shift,
    )

    private fun assertNear(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 0.001f, "expected $expected, was $actual")

    @Test
    fun with_no_shift_the_tint_is_the_colour_and_nothing_glows() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = 0f))

        assertNear(0x80 / 255f, paint.red)
        assertNear(0x40 / 255f, paint.green)
        assertNear(0x20 / 255f, paint.blue)
        assertEquals(0f, paint.emissiveRed)
        assertEquals(0f, paint.emissiveGreen)
        assertEquals(0f, paint.emissiveBlue)
    }

    @Test
    fun a_positive_shift_makes_the_structure_glow_in_its_own_colour() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = 0.5f))

        assertNear(0x80 / 255f, paint.red)
        assertNear(0x80 / 255f * 0.5f, paint.emissiveRed)
        assertNear(0x40 / 255f * 0.5f, paint.emissiveGreen)
        assertNear(0x20 / 255f * 0.5f, paint.emissiveBlue)
    }

    @Test
    fun a_negative_shift_darkens_the_tint_instead_of_being_thrown_away() {
        // §12: never by hue alone. The wrong answer's token asks to be darker, and until
        // now both renderers clamped that request to nothing.
        val paint = HighlightPaint.of(style(0xFF804020, shift = -0.25f))

        assertNear(0x80 / 255f * 0.75f, paint.red)
        assertNear(0x40 / 255f * 0.75f, paint.green)
        assertNear(0x20 / 255f * 0.75f, paint.blue)
        assertEquals(0f, paint.emissiveRed)
    }

    @Test
    fun a_shift_of_minus_one_is_black_not_a_negative_colour() {
        val paint = HighlightPaint.of(style(0xFF804020, shift = -1f))

        assertEquals(0f, paint.red)
        assertEquals(0f, paint.green)
        assertEquals(0f, paint.blue)
    }

    @Test
    fun alpha_is_carried_and_never_shifted() {
        assertNear(0x80 / 255f, HighlightPaint.of(style(0x80804020, shift = -0.5f)).alpha)
        assertNear(1f, HighlightPaint.of(style(0xFF804020, shift = 0.5f)).alpha)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: compilation FAILS with `Unresolved reference 'HighlightPaint'`.

- [ ] **Step 3: Implement**

`HighlightPaint.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.HighlightStyle

/**
 * A highlight as the two material parameters the renderers set: `baseColorFactor` and
 * `emissiveFactor`.
 *
 * In shared code so iOS and Android cannot disagree about what a style looks like. The
 * colour is the style's outline colour, as it has been on both platforms; outlines
 * themselves are §12's shader work and are not drawn yet.
 *
 * A positive luminance shift is light the structure gives off, in its own colour. A
 * negative one darkens the tint. Spec §12 forbids telling two states apart by hue alone,
 * so the shift has to survive in both directions.
 */
data class HighlightPaint(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val emissiveRed: Float,
    val emissiveGreen: Float,
    val emissiveBlue: Float,
) {
    companion object {
        fun of(style: HighlightStyle): HighlightPaint {
            val argb = style.outlineArgb
            val alpha = ((argb ushr 24) and 0xFF) / 255f
            val red = ((argb shr 16) and 0xFF) / 255f
            val green = ((argb shr 8) and 0xFF) / 255f
            val blue = (argb and 0xFF) / 255f

            val shift = style.fillLuminanceShift
            val lift = shift.coerceAtLeast(0f)
            val keep = (1f + shift.coerceAtMost(0f)).coerceAtLeast(0f)
            return HighlightPaint(
                red = red * keep,
                green = green * keep,
                blue = blue * keep,
                alpha = alpha,
                emissiveRed = red * lift,
                emissiveGreen = green * lift,
                emissiveBlue = blue * lift,
            )
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: BUILD SUCCESSFUL; `HighlightPaintTest` five passing on the JVM host and on the iOS simulator.

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament/src/commonMain shared/renderer-filament/src/commonTest
git commit -m "feat(renderer): what a highlight style paints is worked out once, in shared code"
```

---

### Task 2: The interface, the fake and the contract

Changing an interface method breaks every implementation at once. So that this task ends green, both real renderers get a three-line adapter here that draws every structure in the map's first style. That is today's behaviour for the only caller there is; Tasks 3 and 4 replace it.

**Files:**
- Modify: `shared/renderer-api/src/commonMain/.../AnatomyRenderer.kt`
- Modify: `shared/renderer-api/src/commonMain/.../FakeAnatomyRenderer.kt`
- Modify: `shared/renderer-api/src/commonMain/.../AnatomyRendererContract.kt`
- Modify: `shared/renderer-api/src/commonTest/.../FakeAnatomyRendererTest.kt`
- Modify: `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt`
- Modify: `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  ```kotlin
  interface AnatomyRenderer { fun highlight(styles: Map<StructureId, HighlightStyle>) }
  fun AnatomyRenderer.highlight(structures: Set<StructureId>, style: HighlightStyle)   // extension
  class FakeAnatomyRenderer { val highlights: Map<StructureId, HighlightStyle> }
  abstract class AnatomyRendererContract { suspend fun verifySeveralHighlightsAtOnceAreAccepted() }
  ```
  `FakeAnatomyRenderer.highlighted` and `.highlightStyle` are removed; nothing reads them.

- [ ] **Step 1: Write the failing tests**

In `FakeAnatomyRendererTest.kt`, add to the contract subclass's list of `@Test` functions (beside `accepts_a_highlight`):

```kotlin
    @Test fun accepts_several_highlights_at_once() = runTest { verifySeveralHighlightsAtOnceAreAccepted() }
```

and add inside `class FakeAnatomyRendererTest`:

```kotlin
    private val rib = StructureId("costa-vii")
    private val vertebra = StructureId("vertebra-c7")

    private fun style(argb: Long) = HighlightStyle(
        outlineArgb = argb.toInt(),
        outlineWidthDp = 3f,
        outlineStyle = OutlineStyle.SOLID,
        fillArgb = argb.toInt(),
        fillLuminanceShift = 0f,
    )

    @Test
    fun each_structure_keeps_its_own_style() {
        // Screen 12: the expected answer and the chosen one, on the model together.
        val renderer = FakeAnatomyRenderer()
        val expected = style(0xFF57B37C)
        val chosen = style(0xFFD89B3C)

        renderer.highlight(mapOf(rib to expected, vertebra to chosen))

        assertEquals(expected, renderer.highlights[rib])
        assertEquals(chosen, renderer.highlights[vertebra])
    }

    @Test
    fun a_new_highlight_replaces_the_last_one_rather_than_adding_to_it() {
        // Review Focus 1. The renderer is a projection of what it was last told (§4).
        val renderer = FakeAnatomyRenderer()
        renderer.highlight(mapOf(rib to style(0xFF57B37C), vertebra to style(0xFFD89B3C)))

        renderer.highlight(mapOf(rib to style(0xFFD89B3C)))

        assertEquals(mapOf(rib to style(0xFFD89B3C)), renderer.highlights)
    }

    @Test
    fun an_empty_map_clears_every_highlight() {
        // Review Focus 3.
        val renderer = FakeAnatomyRenderer()
        renderer.highlight(mapOf(rib to style(0xFF57B37C)))

        renderer.highlight(emptyMap())

        assertTrue(renderer.highlights.isEmpty())
    }

    @Test
    fun the_one_style_form_gives_every_structure_that_style() {
        val renderer = FakeAnatomyRenderer()
        val selected = style(0xFFFFD3CB)

        renderer.highlight(setOf(rib, vertebra), selected)

        assertEquals(mapOf(rib to selected, vertebra to selected), renderer.highlights)
    }
```

Add any of these imports the file lacks: `com.ptk.anatomypro.core.model.StructureId`, `com.ptk.anatomypro.renderer.api.HighlightStyle`, `com.ptk.anatomypro.renderer.api.OutlineStyle`, `kotlin.test.assertEquals`, `kotlin.test.assertTrue`. (The file is in the `renderer.api` package, so the first two api imports are unnecessary there; keep only what the compiler asks for.)

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:renderer-api:allTests`
Expected: compilation FAILS with `Unresolved reference 'verifySeveralHighlightsAtOnceAreAccepted'` and `Unresolved reference 'highlights'`.

- [ ] **Step 3: Change the interface**

In `AnatomyRenderer.kt`, replace the line `    fun highlight(structures: Set<StructureId>, style: HighlightStyle)` with:

```kotlin
    /**
     * Highlights each structure in its own style, and nothing else.
     *
     * The map is the whole of what is highlighted: a structure left out of it stops being
     * highlighted, and an empty map clears every highlight. A structure the loaded packs do
     * not draw — a group, or an id from a pack that is not loaded — is ignored.
     *
     * Several styles at once is what lets a wrong answer be shown beside the right one.
     * Highlight beats ghost: a structure the app is pointing at is not also faded out.
     */
    fun highlight(styles: Map<StructureId, HighlightStyle>)
```

and add at the end of the file, outside the interface:

```kotlin
/** Every structure in [structures] in the one [style]: the common case, a selection. */
fun AnatomyRenderer.highlight(structures: Set<StructureId>, style: HighlightStyle) =
    highlight(structures.associateWith { style })
```

- [ ] **Step 4: Change the fake**

In `FakeAnatomyRenderer.kt`, replace

```kotlin
    var highlighted: Set<StructureId> = emptySet()
        private set
    var highlightStyle: HighlightStyle? = null
        private set
```

with

```kotlin
    /** What is highlighted, and how: exactly the last map [highlight] was given. */
    var highlights: Map<StructureId, HighlightStyle> = emptyMap()
        private set
```

and replace the `highlight` override with

```kotlin
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlights = styles.toMap()
    }
```

- [ ] **Step 5: Add the contract case**

In `AnatomyRendererContract.kt`, after `verifyHighlightingALoadedStructureIsAccepted`, add:

```kotlin
    suspend fun verifySeveralHighlightsAtOnceAreAccepted() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // Survival again, not pixels. Two structures in two styles, with a third the pack
        // does not contain; then one of them restyled; then nothing.
        val absent = StructureId("no-such-structure")
        renderer.highlight(
            mapOf(hitStructure to HIGHLIGHT, offCentreStructure to SECOND_HIGHLIGHT, absent to HIGHLIGHT)
        )
        settle(renderer)
        renderer.highlight(mapOf(hitStructure to SECOND_HIGHLIGHT))
        settle(renderer)
        renderer.highlight(emptyMap())
        settle(renderer)

        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported highlighting several structures")
    }
```

and in the companion object, after `HIGHLIGHT`:

```kotlin
        /** Darker and a different hue: the wrong answer beside the right one. */
        val SECOND_HIGHLIGHT = HighlightStyle(
            outlineArgb = 0xFFD89B3C.toInt(),
            outlineWidthDp = 3f,
            outlineStyle = OutlineStyle.DASHED,
            fillArgb = 0xFFD89B3C.toInt(),
            fillLuminanceShift = -0.20f,
        )
```

The existing calls `renderer.highlight(setOf(hitStructure), HIGHLIGHT)` and `renderer.highlight(emptySet(), HIGHLIGHT)` in this file now resolve to the extension and need no change.

- [ ] **Step 6: Keep the real renderers compiling**

In `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt`, change the signature line

```kotlin
    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
```

to

```kotlin
    // Interim: one style for the whole map. Replaced when the shim takes groups.
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        val structures = styles.keys
        val style = styles.values.firstOrNull() ?: HighlightStyle(0, 1f, OutlineStyle.SOLID, 0, 0f)
```

and add `import com.ptk.anatomypro.renderer.api.OutlineStyle` if absent. The rest of the function body is unchanged: with an empty map `structures` is empty, `nodes` is empty, and the existing branch clears.

In `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt`, replace

```kotlin
    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        highlighted.clear()
        highlighted += structures
        highlightStyle = style
        applyAppearance()
    }
```

with

```kotlin
    // Interim: one style for the whole map. Replaced when applyAppearance takes a style
    // per structure.
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlighted.clear()
        highlighted += styles.keys
        highlightStyle = styles.values.firstOrNull()
        applyAppearance()
    }
```

The two canvases call the one-style form from another package, where an extension has to be imported. In `shared/src/iosMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.ios.kt` and `shared/src/androidMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.android.kt`, add:

```kotlin
import com.ptk.anatomypro.renderer.api.highlight
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-api:allTests :shared:renderer-filament:allTests :shared:feature-atlas:allTests`
Expected: BUILD SUCCESSFUL. `FakeAnatomyRendererTest` gains four tests and the fake's contract subclass one, on each target; the iOS renderer contract still passes unchanged.

Run: `./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (proves the Android renderer and both canvases compile).

- [ ] **Step 8: Commit**

```bash
git add shared/renderer-api shared/renderer-filament shared/src
git commit -m "feat(renderer-api): a highlight is a map of structure to style"
```

---

### Task 3: iOS draws each group in its own style

**Files:**
- Modify: `ios-renderer/include/anatomy_renderer.h`
- Modify: `ios-renderer/src/AnatomyRenderer.mm`
- Modify: `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt`
- Test: `shared/renderer-filament/src/iosTest/.../FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: `HighlightPaint.of(style)` (Task 1); `verifySeveralHighlightsAtOnceAreAccepted` (Task 2).
- Produces, in the C seam:
  ```c
  void ar_add_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                        const float* tint_rgba, const float* emissive_rgb);
  void ar_clear_highlight(ar_renderer_ref renderer);   /* unchanged */
  ```
  `ar_set_highlight` is removed.

- [ ] **Step 1: Write the failing test**

In the iOS `FilamentAnatomyRendererContractTest.kt`, beside `accepts_a_highlight`, add:

```kotlin
    @Test fun accepts_several_highlights_at_once() = runBlocking { verifySeveralHighlightsAtOnceAreAccepted() }
```

- [ ] **Step 2: Run it**

Run: `./gradlew :shared:renderer-filament:iosSimulatorArm64Test`
Expected: PASS. The interim adapter draws one style and survives, and the contract cannot see colour, so this test cannot fail first. It is here to guard the next steps, which rewrite the code it exercises; say so in the commit rather than pretending it went red.

- [ ] **Step 3: Change the seam's header**

In `anatomy_renderer.h`, replace

```c
void ar_set_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                      int32_t outline_argb, float luminance_shift);
void ar_clear_highlight(ar_renderer_ref renderer);
```

with

```c
/*
 * Adds a group of nodes to what is highlighted, all painted alike.
 *
 * `tint_rgba` is four floats and `emissive_rgb` three, both already worked out by the
 * caller: how a highlight style becomes a colour is decided once, in shared Kotlin, so
 * that this shim and the Android renderer cannot disagree.
 *
 * Groups accumulate until ar_clear_highlight. A node belongs to one group at a time: adding
 * it again moves it to the new group.
 */
void ar_add_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                      const float* tint_rgba, const float* emissive_rgb);
void ar_clear_highlight(ar_renderer_ref renderer);
```

- [ ] **Step 4: Change the shim**

In `AnatomyRenderer.mm`:

Add `#include <algorithm>` beside `#include <cmath>`.

After the `SwappedMaterial` struct, add:

```cpp
/** Nodes highlighted alike. The tint arrives ready-made; see ar_add_highlight. */
struct HighlightGroup {
    std::vector<std::string> nodes;
    float4 tint;
    float3 emissive;
};
```

In `struct ar_renderer`, replace

```cpp
    std::vector<std::string> highlightedNodes;
```

with

```cpp
    std::vector<HighlightGroup> highlights;
```

and delete the two lines

```cpp
    int32_t highlightArgb = 0;
    float highlightLift = 0.0f;
```

Immediately above `applyAppearance`'s comment block, add:

```cpp
bool isHighlighted(const ar_renderer* r, const std::string& name) {
    for (const auto& group : r->highlights) {
        if (contains(group.nodes, name)) return true;
    }
    return false;
}
```

In `applyAppearance`, in the ghost loop, replace

```cpp
            const bool isHighlighted = nodeEntry != r->entityToNode.end() &&
                                        contains(r->highlightedNodes, nodeEntry->second);
            if (isHighlighted) continue;
```

with

```cpp
            // Highlight beats ghost, whichever group the node is highlighted in.
            if (nodeEntry != r->entityToNode.end() && isHighlighted(r, nodeEntry->second)) continue;
```

Replace the whole final block of `applyAppearance`, from `if (!r->highlightedNodes.empty()) {` to its closing brace, with:

```cpp
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
```

In `releaseAsset`, replace `r->highlightedNodes.clear();` with `r->highlights.clear();`.

Replace `ar_set_highlight` and `ar_clear_highlight` with:

```cpp
void ar_add_highlight(ar_renderer_ref r, const char* const* nodeNames, size_t count,
                      const float* tintRgba, const float* emissiveRgb) {
    if (!r || !r->engine || !r->asset || !tintRgba || !emissiveRgb) return;

    HighlightGroup group;
    group.nodes = collect(nodeNames, count);
    group.tint = float4{tintRgba[0], tintRgba[1], tintRgba[2], tintRgba[3]};
    group.emissive = float3{emissiveRgb[0], emissiveRgb[1], emissiveRgb[2]};

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
```

- [ ] **Step 5: Call it from Kotlin**

In the iOS `FilamentAnatomyRenderer.kt`, replace the import `…cinterop.ar_set_highlight` with `…cinterop.ar_add_highlight`, add `import kotlinx.cinterop.allocArrayOf` if absent, and replace the whole interim `highlight` function from Task 2 with:

```kotlin
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        // Clear, then one group per style. No frame is drawn in between: this runs to
        // completion on the thread that renders.
        ar_clear_highlight(handle)
        for ((style, structures) in styles.entries.groupBy({ it.value }, { it.key })) {
            val nodes = structures.flatMap { nodesByStructure[it].orEmpty() }
            if (nodes.isEmpty()) continue // groups, and structures this pack does not draw
            val paint = HighlightPaint.of(style)
            passNodes(nodes) { names, count ->
                memScoped {
                    val tint = allocArrayOf(paint.red, paint.green, paint.blue, paint.alpha)
                    val emissive = allocArrayOf(paint.emissiveRed, paint.emissiveGreen, paint.emissiveBlue)
                    ar_add_highlight(handle, names, count, tint, emissive)
                }
            }
        }
        drain()
    }
```

Remove the `OutlineStyle` import if Task 2 added it and nothing else uses it.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: BUILD SUCCESSFUL. The Gradle build restages the native library; `accepts_several_highlights_at_once`, `accepts_a_highlight` and `does_not_fault_when_highlight_and_ghost_interleave` all pass on the simulator.

If the link fails with `Undefined symbols: _ar_set_highlight`, something still imports the old function: `grep -rn ar_set_highlight shared ios-renderer --include='*.kt' --include='*.mm' --include='*.h' | grep -v /build/` must print nothing.

- [ ] **Step 7: Commit**

```bash
git add ios-renderer shared/renderer-filament
git commit -m "feat(ios): the shim highlights several groups, each with its own tint

The contract case was green before this change too: it proves survival, and
the interim one-style adapter survived. It guards the rewrite, not the colour."
```

---

### Task 4: Android draws each structure in its own style

**Files:**
- Modify: `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt`
- Test: `shared/renderer-filament/src/androidDeviceTest/.../FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: `HighlightPaint.of(style)` (Task 1); `verifySeveralHighlightsAtOnceAreAccepted` (Task 2).
- Produces: nothing other tasks rely on.

- [ ] **Step 1: Write the test**

In the Android `FilamentAnatomyRendererContractTest.kt`, beside `accepts_a_highlight`, add:

```kotlin
    @Test fun accepts_several_highlights_at_once() = runBlocking { verifySeveralHighlightsAtOnceAreAccepted() }
```

- [ ] **Step 2: Run it**

Start the emulator (`emulator -avd Medium_Phone_API_36.1`), then run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: PASS, for the reason given in Task 3 Step 2. Note the total: it was 24 before this plan and is 25 now.

- [ ] **Step 3: Implement**

In `FilamentAnatomyRenderer.android.kt`:

Replace the two fields

```kotlin
    private val highlighted = mutableSetOf<StructureId>()
```

and

```kotlin
    private var highlightStyle: HighlightStyle? = null
```

(each with its own doc comment, if it has one) with the single field

```kotlin
    /** What is highlighted and how. One style per structure, so no primitive is visited twice. */
    private val highlights = mutableMapOf<StructureId, HighlightStyle>()
```

Replace every remaining `highlighted.clear()` (there are two: in the pack-unload path and in `dispose`) with `highlights.clear()`.

Replace the interim `highlight` function from Task 2 with:

```kotlin
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlights.clear()
        highlights += styles
        applyAppearance()
    }
```

In `applyAppearance`, replace `for (structure in ghosted - highlighted) {` with:

```kotlin
            // Highlight beats ghost, whatever style the structure is highlighted in.
            for (structure in ghosted - highlights.keys) {
```

and replace the whole final block, from `val style = highlightStyle` to the closing brace of its `if`, with:

```kotlin
        for ((structure, style) in highlights) {
            val paint = HighlightPaint.of(style)
            for (entity in (entitiesByStructure[structure] ?: NO_ENTITIES)) {
                val instance = renderables.getInstance(entity)
                if (instance == 0) continue
                for (primitive in 0 until renderables.getPrimitiveCount(instance)) {
                    val original = renderables.getMaterialInstanceAt(instance, primitive) ?: continue
                    val replacement = MaterialInstance.duplicate(original, null)
                    val material = original.material
                    if (material.hasParameter("baseColorFactor")) {
                        replacement.setParameter("baseColorFactor", paint.red, paint.green, paint.blue, paint.alpha)
                    }
                    if (material.hasParameter("emissiveFactor")) {
                        replacement.setParameter(
                            "emissiveFactor",
                            paint.emissiveRed,
                            paint.emissiveGreen,
                            paint.emissiveBlue,
                        )
                    }
                    swapped += Triple(entity, primitive, original)
                    highlightInstances += replacement
                    renderables.setMaterialInstanceAt(instance, primitive, replacement)
                }
            }
        }
```

In the doc comment above `applyAppearance`, the sentence "Kotlin's `Set<StructureId>` de-duplicates structures for free" is now about a map's keys; change `Set<StructureId>` to `Map<StructureId, HighlightStyle>` there.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: BUILD SUCCESSFUL, 25 tests passing on the emulator.

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament
git commit -m "feat(android): a style per highlighted structure, painted by the shared rule"
```

---

### Task 5: The canvas takes the map, and the change is recorded

`AnatomyCanvas` takes one structure and hard-codes `HighlightTokens.Selected`. Screen 12 needs to pass two structures with two tokens.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.kt`
- Modify: `shared/src/iosMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.ios.kt`
- Modify: `shared/src/androidMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.android.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AtlasTab.kt`
- Modify: `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`
- Modify: `docs/state-of-play.md`

**Interfaces:**
- Consumes: `AnatomyRenderer.highlight(styles)` (Task 2).
- Produces:
  ```kotlin
  @Composable expect fun AnatomyCanvas(
      modifier: Modifier,
      highlights: Map<StructureId, HighlightStyle>,
      render: RenderState,
      focus: FocusRequest?,
      onPicked: (StructureId?) -> Unit,
      onStats: (CanvasStats) -> Unit,
  )
  fun selectionHighlight(structure: StructureId?): Map<StructureId, HighlightStyle>
  ```
  The quiz plan calls `AnatomyCanvas` with a map built from `HighlightTokens.Correct` and `HighlightTokens.Incorrect`.

No test: `:shared` has no test source set that has ever run, and this is a parameter passed through. The compilers and Step 5 are the check.

- [ ] **Step 1: Change the expect declaration**

In `AnatomyCanvas.kt`, replace `    highlighted: StructureId?,` with `    highlights: Map<StructureId, HighlightStyle>,`, add the imports

```kotlin
import com.ptk.anatomypro.core.designsystem.HighlightTokens
import com.ptk.anatomypro.renderer.api.HighlightStyle
```

and add at the end of the file:

```kotlin
/** The atlas's one highlight: the selected structure, or nothing. */
fun selectionHighlight(structure: StructureId?): Map<StructureId, HighlightStyle> =
    structure?.let { mapOf(it to HighlightTokens.Selected) } ?: emptyMap()
```

- [ ] **Step 2: Change both actuals**

In `AnatomyCanvas.ios.kt` and in `AnatomyCanvas.android.kt`, replace the parameter `    highlighted: StructureId?,` with `    highlights: Map<StructureId, HighlightStyle>,`, add `import com.ptk.anatomypro.renderer.api.HighlightStyle`, and replace the final effect

```kotlin
    LaunchedEffect(renderer, highlighted, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        renderer.highlight(setOfNotNull(highlighted), HighlightTokens.Selected)
    }
```

with

```kotlin
    // Keyed on the map's contents: an equal map built afresh on recomposition is not a change.
    LaunchedEffect(renderer, highlights, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        renderer.highlight(highlights)
    }
```

Remove from each actual the `import com.ptk.anatomypro.renderer.api.highlight` that Task 2 added, and the `HighlightTokens` import if nothing else in the file uses it.

- [ ] **Step 3: Change the callers**

In `AtlasTab.kt` there are two `AnatomyCanvas(` calls, one in `LayersRoute` and one in `BrowseRoute`, each passing `highlighted = panel.focus,`. In those two calls only, replace that argument with the line below. `BrowseRoute` also passes `highlighted = panel.focus` to `AtlasScreen`, which marks the selected row in the list; that one is a different parameter and stays as it is.

```kotlin
                highlights = selectionHighlight(panel.focus),
```

keeping each line's own indentation.

- [ ] **Step 4: Build both apps**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

Run:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: See that selection still highlights**

On the Android emulator (`./gradlew :androidApp:installDebug`) and on the iOS simulator, tap a bone on the model. It is named under the canvas and highlighted as before; tapping empty space clears it; tapping another moves the highlight and leaves nothing behind on the first.

On the iOS simulator, taps go through `mcp__mobile__input`, in the coordinate space of the last `mcp__mobile__screen` capture; wait for the pack to load before tapping the model.

Write down what was seen. **Two styles at once are not seen in this plan**: nothing in the app asks for them until screen 12.

- [ ] **Step 6: Record it**

Append to `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`, numbering the addendum one above the last in the file:

```markdown
## NN. Addendum — 2026-10-06: several highlights at once

Screen 12 shows the expected answer beside the chosen one. The renderer could hold one
highlight style at a time: a second `highlight` call replaced the first on both platforms.

`AnatomyRenderer.highlight` now takes a map of structure to style, which is the whole of
what is highlighted. The one-style form survives as an extension. On iOS the C seam's
`ar_set_highlight` became `ar_add_highlight`, which appends a group of nodes with a
ready-made tint; a node belongs to one group, so no primitive is visited twice in an apply
pass (§26.4).

How a style becomes material parameters is `HighlightPaint`, in shared Kotlin. Both
renderers had clamped a negative luminance shift to zero, so `HighlightTokens.Incorrect`'s
"darker" was never drawn. It is now: a negative shift scales the tint down.

Verified: the paint rule by unit tests on both targets; that each renderer accepts several
styles, a restyle and a clear without faulting, by the contract on the fake, the iOS
simulator and the Android emulator; that selecting a structure still highlights it, by hand
on both.

Not verified: what two styles look like side by side. The contract cannot see colour, and
no screen asks for two styles until screen 12 exists. Outlines are still not drawn; the tint
still comes from the style's outline colour, and `fillArgb` is still unused.
```

Replace `NN` with the real number.

In `docs/state-of-play.md`, update the instrumented test count from 24 to 25, and add under "What is verified, and what is not" one line saying two highlight styles at once are accepted by both renderers and have not been seen.

- [ ] **Step 7: Run everything once more**

Run: `./gradlew allTests`
Expected: BUILD SUCCESSFUL on both targets. Stop any app running on a simulator first.

- [ ] **Step 8: Commit**

```bash
git add shared/src docs
git commit -m "feat: the canvas takes a map of highlights; the atlas passes its one selection"
```

---

## Spec coverage

| Requirement | Where |
|---|---|
| All-screens §9, screen 12: expected and chosen on the model together | Tasks 2–5 make it possible; the quiz plan builds the screen |
| All-screens §2, §10: distinguished by more than hue | Task 1 — a negative shift now darkens. The "and shape" half is glyphs and labels on the screen, in the quiz plan |
| Design §4: one contract, held on the fake, iOS and Android | Task 2 contract case, wired in Tasks 2, 3, 4 |
| Design §26.4: one owner of the material slot; no primitive visited twice | Task 3 `ar_add_highlight` moves a node between groups; Task 4 uses a map |
| Design §12: outlines | Not here. Out of scope, and said so in the addendum |

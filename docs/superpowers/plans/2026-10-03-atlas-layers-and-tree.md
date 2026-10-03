# Atlas Layers (Screen 07) and Structure Tree Mode (Screen 21) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build prototype screen 07 (show, ghost or hide each body system, and isolate the selected structure with its neighbours ghosted) and screen 21 (the screen-reader equivalent of the 3D canvas: the hierarchy walked level by level, with camera focus and spoken position), including the renderer work both depend on.

**Architecture:** One app-scoped `AtlasSceneViewModel` owns what the renderer shows — per-system layer modes, isolation, ghost opacity, and camera-focus requests — and resolves them to a `RenderState` (hidden set, ghosted set, alpha) through a pure `SceneResolver` that reuses `IsolationPolicy`. `AnatomyCanvas` takes that `RenderState` and a `FocusRequest` and applies them to the renderer by diffing. Camera focus is new renderer behaviour on both platforms, with the framing maths in shared Kotlin and only bounds and camera placement per platform. Screen 21 is a list screen with its own `StructureTreeViewModel`; it replaces the canvas when the existing `structureTreeMode` setting is on.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.11.1, navigation-compose 2.9.2, Room 2.8.4, Filament 1.75.1 (Kotlin/Java on Android, Objective-C++ C seam on iOS), kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-24-all-screens-mocked-design.md` §9 (screens 07 and 21: states that must exist), §10, §11 step 6. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §4 (renderer boundary), §12 (accessibility), §26 (transparency, `IsolationPolicy`). Prototype: screens 07 and 21 in the artifact linked from design spec §19.

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`.** Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **The Android renderer contract is instrumented.** Run it on the emulator: `emulator -avd Medium_Phone_API_36.1`, then `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`. The emulator counts as a connected device; Filament renders on it.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **UI strings are always resources** (all-screens spec §8). English in `values/`, Polish in `values-pl/`. Key names are `<screen>_<purpose>` in lower snake case. Glyphs, locale codes and each language's own name are the only literals.
- **ViewModels never produce user-visible text.** They expose kinds and values; screens pick the resource.
- **State that must survive an interface-language change lives above `ProvideAppLocale`** — it rebuilds everything beneath it.
- **The renderer is told, never asked** (design spec §4). Kotlin owns all state; a renderer change goes through `AnatomyRendererContract` and holds on the fake, iOS and Android.
- **§12:** touch targets at least 44 dp; never distinguish state by colour alone — every layer mode and the focused row carry a text label.
- Ids are lowercase kebab-case slugs. System ids in real data look like `skeletal-system`, `muscular-system`.
- **Out of scope:** "Relacje przestrzenne" (spatial relations) from the screen-21 prototype — no data exists for it; the per-row descriptor ("os longum, par"); orbit/pan camera controls and `setCameraPose`; bundling the real pack on iOS (iOS still draws the three-cube toy pack); Compose UI tests.

## File Structure

| File | Responsibility |
|---|---|
| `shared/renderer-filament/src/commonMain/.../CameraFraming.kt` | Create. `Vec3`, `WorldBox`, `CameraShot`, `CameraFraming`, `CameraFlight` — the camera maths both platforms share. |
| `shared/renderer-filament/src/commonTest/.../CameraFramingTest.kt` | Create. Host + simulator tests for the maths. |
| `shared/renderer-api/.../AnatomyRendererContract.kt` | Modify. `offCentreStructure`, `pickCentre`, `verifyFocusingTheCameraCentresAStructure`. |
| `shared/renderer-api/.../FakeAnatomyRenderer.kt` | Modify. Records `focused`. |
| `ios-renderer/include/anatomy_renderer.h`, `ios-renderer/src/AnatomyRenderer.mm` | Modify. `ar_nodes_bounds`, `ar_set_camera`. |
| `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt` | Modify. `focusCamera`, camera stepping in `renderFrame`. |
| `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt` | Modify. Hide/ghost (Task 1), `focusCamera` (Task 4). |
| `shared/core-data/.../dao/StructureDao.kt`, `repository/AtlasRepository.kt`, `repository/RoomAtlasRepository.kt` | Modify. `systems()`, `structuresIn()`, `allStructures()`. |
| `shared/core-data-fake/.../Fixture.kt`, `FakeAtlasRepository.kt`, `FakeQuizRepository.kt` | Modify. Fixture gains systems and a muscular branch. |
| `shared/feature-atlas/.../scene/Scene.kt` | Create. `LayerMode`, `RenderState`, `FocusRequest`, `SceneResolver`, `applyRenderState`. |
| `shared/feature-atlas/.../scene/AtlasSceneViewModel.kt` | Create. The app-scoped owner of what the renderer shows. |
| `shared/feature-atlas/.../layers/LayersScreen.kt`, `SystemNames.kt` | Create. Screen 07. |
| `shared/feature-atlas/.../tree/StructureTreeViewModel.kt`, `TreeScreen.kt` | Create. Screen 21. |
| `shared/src/commonMain/.../AnatomyCanvas.kt` + both actuals | Modify. Take `RenderState` and `FocusRequest`. |
| `shared/src/commonMain/.../App.kt`, `AtlasTab.kt` | Modify. Scene ViewModel scope, Layers route, tree-mode branch. |

---

### Task 1: Android hides and ghosts (transparency plan Task 4, on the emulator)

`setVisibility` and `setOpacity` still throw `TODO()` on Android. Screen 07 calls both, so Android would crash on it. The work is already fully specified — including the `ghostAlpha` fix the iOS review demanded — in `docs/superpowers/plans/2026-09-10-transparent-material-variants.md`, Task 4 (lines 689–950). It was deferred only because "it needs a device"; the emulator is one.

**Files:**
- Modify: `shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.android.kt`
- Modify: `shared/renderer-filament/src/androidDeviceTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: the existing `AnatomyRenderer.setVisibility` / `setOpacity` contract.
- Produces: a working Android `setVisibility` and `setOpacity`, used by Task 8's canvas.

- [ ] **Step 1: Boot the emulator**

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.1 -no-snapshot-save -no-audio -no-boot-anim &
~/Library/Android/sdk/platform-tools/adb wait-for-device
```

- [ ] **Step 2: Execute the transparency plan's Task 4, Steps 1–7, exactly as written**

The four contract lines to add in its Step 1 are precisely the four the Android subclass lacks compared with iOS:

```kotlin
    @Test fun hides_a_structure_from_picking() = runBlocking { verifyHidingAStructureRemovesItFromPicking() }
    @Test fun shows_a_hidden_structure_again() = runBlocking { verifyShowingAHiddenStructureRestoresPicking() }
    @Test fun keeps_a_ghosted_structure_pickable() = runBlocking { verifyAGhostedStructureStaysPickable() }
    @Test fun does_not_fault_when_highlight_and_ghost_interleave() = runBlocking { verifyDoesNotFaultWhenHighlightAndGhostInterleave() }
```

Its Step 2 must show these four failing with `kotlin.NotImplementedError`; its Step 6 must show all ten passing:

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected after Step 6: 10 tests, 0 failures.

- [ ] **Step 3: Commit**, with the transparency plan's Task 4 commit message.

---

### Task 2: The camera maths, shared by both platforms

Both platforms already frame the whole model with the same formula (`frameAsset` in `AnatomyRenderer.mm` and in the Android renderer). Focusing on one structure is that formula over a smaller box, plus an animation. Keeping it in common Kotlin means it is written and tested once.

**Files:**
- Create: `shared/renderer-filament/src/commonMain/kotlin/com/ptk/anatomypro/renderer/filament/CameraFraming.kt`
- Create: `shared/renderer-filament/src/commonTest/kotlin/com/ptk/anatomypro/renderer/filament/CameraFramingTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (all `internal`, used by Tasks 3 and 4): `Vec3(x, y, z)`; `WorldBox(center: Vec3, halfExtent: Vec3)` with `lower`, `upper`, `union(other)`, `WorldBox.transformed(local, m: FloatArray)`; `CameraShot(eye: Vec3, target: Vec3, near: Double, far: Double)`; `CameraFraming.FOV_DEGREES`, `CameraFraming.frame(box): CameraShot`; `CameraFlight(from, to, durationNanos)` with `at(nowNanos): CameraShot` and `finished: Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraFramingTest {

    private fun near(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < 1e-4f, "expected $expected, was $actual")

    private val unitCube = WorldBox(Vec3(0f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))

    @Test
    fun framing_matches_the_formula_both_platforms_already_use_for_the_whole_model() {
        val shot = CameraFraming.frame(unitCube)
        val radius = sqrt(0.75f)
        val distance = (radius / tan(22.5 * PI / 180) * 1.6).toFloat()

        near(distance, shot.eye.z)
        near(0f, shot.target.x)
        assertEquals(distance * 0.01, shot.near, 1e-4)
        assertEquals(distance * 10.0, shot.far, 1e-3)
    }

    @Test
    fun framing_looks_at_the_box_centre_from_in_front() {
        val shot = CameraFraming.frame(WorldBox(Vec3(-1.5f, 2f, 0f), Vec3(0.5f, 0.5f, 0.5f)))

        assertEquals(Vec3(-1.5f, 2f, 0f), shot.target)
        near(-1.5f, shot.eye.x)
        near(2f, shot.eye.y)
        assertTrue(shot.eye.z > 0f)
    }

    @Test
    fun a_translation_moves_the_centre_and_leaves_the_extent() {
        // Column-major, as TransformManager.getWorldTransform returns it.
        val translate = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 3f, 4f, 5f, 1f)

        val moved = WorldBox.transformed(unitCube, translate)

        assertEquals(Vec3(3f, 4f, 5f), moved.center)
        assertEquals(Vec3(0.5f, 0.5f, 0.5f), moved.halfExtent)
    }

    @Test
    fun a_quarter_turn_about_z_swaps_the_x_and_y_extents() {
        val box = WorldBox(Vec3(0f, 0f, 0f), Vec3(2f, 1f, 0.5f))
        // 90° about z: x' = -y, y' = x.
        val turn = floatArrayOf(0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        val turned = WorldBox.transformed(box, turn)

        near(1f, turned.halfExtent.x)
        near(2f, turned.halfExtent.y)
        near(0.5f, turned.halfExtent.z)
    }

    @Test
    fun the_union_of_two_boxes_contains_both() {
        val left = WorldBox(Vec3(-1.5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))
        val right = WorldBox(Vec3(1.5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f))

        val both = left.union(right)

        assertEquals(Vec3(-2f, -0.5f, -0.5f), both.lower)
        assertEquals(Vec3(2f, 0.5f, 0.5f), both.upper)
    }

    @Test
    fun a_zero_duration_flight_lands_at_once() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))
        val flight = CameraFlight(from, to, durationNanos = 0L)

        assertEquals(to, flight.at(123L))
        assertTrue(flight.finished)
    }

    @Test
    fun a_headless_frame_time_of_zero_lands_at_once_because_it_carries_no_clock() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(5f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))

        assertEquals(to, CameraFlight(from, to, durationNanos = 600_000_000L).at(0L))
    }

    @Test
    fun the_clock_starts_at_the_first_frame_and_eases_through_the_midpoint() {
        val from = CameraFraming.frame(unitCube)
        val to = CameraFraming.frame(WorldBox(Vec3(4f, 0f, 0f), Vec3(0.5f, 0.5f, 0.5f)))
        val flight = CameraFlight(from, to, durationNanos = 1_000L)

        assertEquals(from.target, flight.at(10_000L).target)
        // Smoothstep is exactly half way at half time.
        near(2f, flight.at(10_500L).target.x)
        assertFalse(flight.finished)
        assertEquals(to, flight.at(11_000L))
        assertTrue(flight.finished)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: FAIL — `WorldBox`, `CameraFraming`, `CameraFlight` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** A point or direction in world space. */
internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    fun length(): Float = sqrt(x * x + y * y + z * z)
}

private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

/** An axis-aligned box in world space, described the way Filament describes one. */
internal data class WorldBox(val center: Vec3, val halfExtent: Vec3) {
    val lower: Vec3 get() = center - halfExtent
    val upper: Vec3 get() = center + halfExtent

    fun union(other: WorldBox): WorldBox = fromCorners(
        Vec3(min(lower.x, other.lower.x), min(lower.y, other.lower.y), min(lower.z, other.lower.z)),
        Vec3(max(upper.x, other.upper.x), max(upper.y, other.upper.y), max(upper.z, other.upper.z)),
    )

    companion object {
        fun fromCorners(lower: Vec3, upper: Vec3) = WorldBox((lower + upper) * 0.5f, (upper - lower) * 0.5f)

        /**
         * [local] moved into world space by the column-major 4×4 [m] that
         * `TransformManager.getWorldTransform` returns. The same result as Filament's C++
         * `rigidTransform`: the centre goes through the matrix, the half-extent through |M|.
         */
        fun transformed(local: WorldBox, m: FloatArray): WorldBox {
            require(m.size == 16) { "expected a 4x4 matrix, got ${m.size} values" }
            fun at(row: Int, col: Int) = m[col * 4 + row]
            val c = local.center
            val h = local.halfExtent
            return WorldBox(
                center = Vec3(
                    at(0, 0) * c.x + at(0, 1) * c.y + at(0, 2) * c.z + at(0, 3),
                    at(1, 0) * c.x + at(1, 1) * c.y + at(1, 2) * c.z + at(1, 3),
                    at(2, 0) * c.x + at(2, 1) * c.y + at(2, 2) * c.z + at(2, 3),
                ),
                halfExtent = Vec3(
                    abs(at(0, 0)) * h.x + abs(at(0, 1)) * h.y + abs(at(0, 2)) * h.z,
                    abs(at(1, 0)) * h.x + abs(at(1, 1)) * h.y + abs(at(1, 2)) * h.z,
                    abs(at(2, 0)) * h.x + abs(at(2, 1)) * h.y + abs(at(2, 2)) * h.z,
                ),
            )
        }
    }
}

/** Where the camera is, what it looks at, and its clip planes. */
internal data class CameraShot(val eye: Vec3, val target: Vec3, val near: Double, val far: Double)

internal object CameraFraming {
    const val FOV_DEGREES = 45.0

    /**
     * The shot that frames [box] from in front, looking down -Z.
     *
     * Identical to `frameAsset` on both platforms, so focusing on the whole model and
     * loading it produce the same view.
     */
    fun frame(box: WorldBox): CameraShot {
        val radius = box.halfExtent.length()
        val distance = if (radius <= 0f) 1f else (radius / tan(FOV_DEGREES / 2 * PI / 180) * 1.6).toFloat()
        return CameraShot(
            eye = box.center + Vec3(0f, 0f, distance),
            target = box.center,
            near = distance * 0.01,
            far = distance * 10.0,
        )
    }
}

/**
 * An eased move between two shots, driven by frame timestamps.
 *
 * The clock starts at the first frame that asks, because the caller of `focusCamera` has
 * no frame time. A time of zero is what headless rendering passes — it carries no clock —
 * so it lands at once, which is what lets contract tests assert on a focused camera.
 */
internal class CameraFlight(
    private val from: CameraShot,
    private val to: CameraShot,
    private val durationNanos: Long,
) {
    private var startNanos: Long? = null

    var finished: Boolean = false
        private set

    fun at(nowNanos: Long): CameraShot {
        if (durationNanos <= 0L || nowNanos <= 0L) return land()
        val start = startNanos ?: nowNanos.also { startNanos = it }
        val t = ((nowNanos - start).toDouble() / durationNanos).coerceIn(0.0, 1.0)
        if (t >= 1.0) return land()
        val eased = (t * t * (3 - 2 * t)).toFloat()
        // The planes span both shots during the move, so nothing is clipped half way.
        return CameraShot(
            eye = lerp(from.eye, to.eye, eased),
            target = lerp(from.target, to.target, eased),
            near = minOf(from.near, to.near),
            far = maxOf(from.far, to.far),
        )
    }

    private fun land(): CameraShot {
        finished = true
        return to
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: PASS on the JVM host and the iOS simulator (the existing iOS contract tests still pass too).

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament/src/commonMain shared/renderer-filament/src/commonTest
git commit -m "feat(renderer-filament): add the camera framing maths both platforms share"
```

---

### Task 3: Focusing the camera — the contract, the fake, and iOS

**Files:**
- Modify: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/AnatomyRendererContract.kt`
- Modify: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRenderer.kt`
- Modify: `shared/renderer-api/src/commonTest/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRendererTest.kt`
- Modify: `ios-renderer/include/anatomy_renderer.h`, `ios-renderer/src/AnatomyRenderer.mm`
- Modify: `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.kt`
- Modify: `shared/renderer-filament/src/iosTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt`
- Modify: `shared/renderer-filament/src/androidDeviceTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt` (compile only; its `@Test` line is Task 4)

**Interfaces:**
- Consumes: Task 2's `WorldBox`, `CameraFraming`, `CameraFlight`, `CameraShot`.
- Produces: a working `focusCamera(structure, durationMs)` on the fake and iOS; `FakeAnatomyRenderer.focused: StructureId?`; contract members `offCentreStructure`, `pickCentre(renderer)`, `verifyFocusingTheCameraCentresAStructure()`; C functions `ar_nodes_bounds` and `ar_set_camera`.

The test that proves focus without seeing pixels: the toy pack is three cubes in a row, the median one at the centre. A centre pick reports the median cube. After focusing on the left one, a centre pick must report the left one.

- [ ] **Step 1: Add the contract case**

In `AnatomyRendererContract`, after `pickMiss`:

```kotlin
    /** A structure the fixture contains that is **not** under the viewport centre at first. */
    protected abstract val offCentreStructure: StructureId

    /** Drives the implementation to pick the exact centre of the viewport. */
    protected abstract suspend fun pickCentre(renderer: AnatomyRenderer)
```

and with the other `verify…` methods:

```kotlin
    /**
     * Focusing frames a structure in the middle of the view. Proven by picking: the centre
     * of the viewport starts on [hitStructure] and must report [offCentreStructure] once
     * the camera has moved to it. Zero duration, so no frame clock is involved.
     */
    suspend fun verifyFocusingTheCameraCentresAStructure() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.focusCamera(offCentreStructure, durationMs = 0)
        settle(renderer)
        pickCentre(renderer)
        settle(renderer)

        val picked = awaitEvent(renderer) { it is RendererEvent.Picked }
        assertEquals(offCentreStructure, (picked as RendererEvent.Picked).structure, "structure at the centre after focusing")
    }
```

- [ ] **Step 2: Teach the fake what focusing means**

In `FakeAnatomyRenderer`, beside `cameraPose`:

```kotlin
    /** The structure the camera was last asked to frame. */
    var focused: StructureId? = null
        private set
```

and replace `focusCamera`:

```kotlin
    override fun focusCamera(structure: StructureId, durationMs: Int) {
        focused = structure
    }
```

In `FakeAnatomyRendererTest`'s contract subclass:

```kotlin
    override val offCentreStructure = StructureId("a02-2-00-000-columna-vertebralis-median")

    /** The fake's centre is whatever it was last asked to frame, or the hit structure. */
    override suspend fun pickCentre(renderer: AnatomyRenderer) {
        val fake = renderer as FakeAnatomyRenderer
        fake.emitPick(fake.focused ?: hitStructure)
    }

    @Test fun centres_a_focused_structure() = runTest { verifyFocusingTheCameraCentresAStructure() }
```

- [ ] **Step 3: Wire the two Filament subclasses**

In **both** `iosTest` and `androidDeviceTest` `FilamentAnatomyRendererContractTest`:

```kotlin
    /** The left cube of the toy row, 1.5 units left of centre. */
    override val offCentreStructure = StructureId("a02-4-01-001-scapula-left")

    override suspend fun pickCentre(renderer: AnatomyRenderer) {
        (renderer as FilamentAnatomyRenderer).pickAt(VIEWPORT / 2f, VIEWPORT / 2f)
    }
```

In the **iOS** subclass only, add:

```kotlin
    @Test fun centres_a_focused_structure() = runBlocking { verifyFocusingTheCameraCentresAStructure() }
```

- [ ] **Step 4: Run them to verify the fake passes and iOS fails**

Run: `./gradlew :shared:renderer-api:allTests :shared:renderer-filament:allTests`
Expected: `renderer-api` PASS (the fake is consistent). `renderer-filament` iOS `centres_a_focused_structure` FAIL with `kotlin.NotImplementedError`.

- [ ] **Step 5: Add the two C functions to the seam**

In `ios-renderer/include/anatomy_renderer.h`, after `ar_clear_opacity`:

```c
/**
 * World-space bounds of the named nodes, as a centre and a half-extent. With count == 0,
 * the bounds of the whole asset. Returns false when nothing matched.
 */
bool ar_nodes_bounds(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                     float out_center[3], float out_half_extent[3]);

/** Places the camera, with a 45° vertical field of view and the viewport's aspect. */
void ar_set_camera(ar_renderer_ref renderer, const float eye[3], const float target[3],
                   double near_plane, double far_plane);
```

In `ios-renderer/src/AnatomyRenderer.mm`, make sure `<filament/Box.h>`, `<filament/RenderableManager.h>` and `<filament/TransformManager.h>` are included, then add:

```objc
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
```

`rigidTransform` is the friend function declared in the vendored `ios-renderer/build/filament/include/filament/Box.h`; `getAxisAlignedBoundingBox` returns `const Box&` in `RenderableManager.h`. If either name differs, read those headers rather than guessing.

- [ ] **Step 6: Implement focusCamera on iOS**

In `FilamentAnatomyRenderer` (iosMain), add beside `ghostAlpha`:

```kotlin
    /** The shot last placed, so a flight starts where the camera is. Null after any reframe. */
    private var shot: CameraShot? = null
    private var flight: CameraFlight? = null
```

Replace `focusCamera`:

```kotlin
    override fun focusCamera(structure: StructureId, durationMs: Int) {
        val nodes = nodesByStructure[structure] ?: return // a group draws nothing to frame
        val box = bounds(nodes) ?: return
        val to = CameraFraming.frame(box)
        val from = shot ?: bounds(emptyList())?.let(CameraFraming::frame) ?: to
        flight = CameraFlight(from, to, durationMs * 1_000_000L)
        if (durationMs <= 0) stepCamera(0L)
    }

    private fun stepCamera(frameTimeNanos: Long) {
        val current = flight ?: return
        place(current.at(frameTimeNanos))
        if (current.finished) flight = null
    }

    private fun place(next: CameraShot) = memScoped {
        val eye = allocArray<FloatVar>(3)
        val target = allocArray<FloatVar>(3)
        eye[0] = next.eye.x; eye[1] = next.eye.y; eye[2] = next.eye.z
        target[0] = next.target.x; target[1] = next.target.y; target[2] = next.target.z
        ar_set_camera(handle, eye, target, next.near, next.far)
        shot = next
    }

    /** World bounds of [nodes], or of the whole asset when empty. */
    private fun bounds(nodes: List<String>): WorldBox? = memScoped {
        val center = allocArray<FloatVar>(3)
        val half = allocArray<FloatVar>(3)
        val found = if (nodes.isEmpty()) {
            ar_nodes_bounds(handle, null, 0u, center, half)
        } else {
            val names = allocArray<CPointerVar<ByteVar>>(nodes.size)
            nodes.forEachIndexed { index, name -> names[index] = name.cstr.getPointer(this) }
            ar_nodes_bounds(handle, names, nodes.size.toULong(), center, half)
        }
        if (!found) null else WorldBox(Vec3(center[0], center[1], center[2]), Vec3(half[0], half[1], half[2]))
    }
```

In `renderFrame`, before `ar_render_frame`:

```kotlin
        stepCamera(frameTimeNanos)
```

In `forgetPerPackState`, and at the end of `attachHeadless` and `attachLayer` (the C side reframes the whole asset there):

```kotlin
        shot = null
        flight = null
```

Add the imports `kotlinx.cinterop.FloatVar` and `kotlinx.cinterop.get` / `set` if the compiler asks for them.

- [ ] **Step 7: Run to verify iOS passes**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: PASS — 12 iOS contract tests (11 + `centres_a_focused_structure`) plus Task 2's tests on both targets.

- [ ] **Step 8: Commit**

```bash
git add shared/renderer-api ios-renderer/include ios-renderer/src shared/renderer-filament
git commit -m "feat(renderer): focus the camera on a structure, on the fake and on iOS"
```

---

### Task 4: Focusing the camera on Android

**Files:**
- Modify: `shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.android.kt`
- Modify: `shared/renderer-filament/src/androidDeviceTest/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: Task 2's maths; Task 3's contract case.
- Produces: a working Android `focusCamera`.

- [ ] **Step 1: Add the failing contract line**

In the Android subclass:

```kotlin
    @Test fun centres_a_focused_structure() = runBlocking { verifyFocusingTheCameraCentresAStructure() }
```

- [ ] **Step 2: Run it on the emulator to verify it fails**

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: `centres_a_focused_structure` FAIL with `kotlin.NotImplementedError`.

- [ ] **Step 3: Implement it**

Add the import `com.google.android.filament.Box`. Beside the other state:

```kotlin
    private var shot: CameraShot? = null
    private var flight: CameraFlight? = null
```

Replace `focusCamera` and add the helpers:

```kotlin
    override fun focusCamera(structure: StructureId, durationMs: Int) {
        val nodes = nodesByStructure[structure] ?: return // a group draws nothing to frame
        val box = nodeBounds(nodes) ?: return
        val to = CameraFraming.frame(box)
        val from = shot ?: assetBounds()?.let(CameraFraming::frame) ?: to
        flight = CameraFlight(from, to, durationMs * 1_000_000L)
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
```

In `renderFrame`, before `renderer.beginFrame`:

```kotlin
        stepCamera(frameTimeNanos)
```

In `frameAsset` (it reframes the whole asset on load and resize) and in `releaseAsset`, reset:

```kotlin
        shot = null
        flight = null
```

Note the Android `renderFrame` default is `System.nanoTime()`, not zero — which is why `focusCamera` steps immediately when `durationMs <= 0` rather than relying on the frame time.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: 11 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament
git commit -m "feat(renderer-filament): focus the camera on a structure on Android"
```

---

### Task 5: The atlas answers "which systems, and what is in each"

Screen 07 lists systems and hides or ghosts whole systems; isolation needs every structure in the atlas. `AtlasRepository` has none of that. The fixture has one system, which cannot exercise a per-system toggle.

**Files:**
- Modify: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/dao/StructureDao.kt`
- Modify: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/AtlasRepository.kt`
- Modify: `shared/core-data/src/commonMain/kotlin/com/ptk/anatomypro/core/data/repository/RoomAtlasRepository.kt`
- Modify: `shared/core-data/src/commonTest/kotlin/com/ptk/anatomypro/core/data/NotBuiltRepositoriesTest.kt` (its `StandInAtlasRepository`)
- Modify: `shared/core-data/src/iosTest/kotlin/com/ptk/anatomypro/core/data/DatabaseTest.kt`
- Modify: `shared/core-data-fake/src/commonMain/kotlin/com/ptk/anatomypro/core/data/fake/Fixture.kt`, `FakeAtlasRepository.kt`, `FakeQuizRepository.kt`
- Modify: `shared/core-data-fake/src/commonTest/kotlin/com/ptk/anatomypro/core/data/fake/FakeAtlasRepositoryTest.kt`, `FixtureTest.kt`
- Modify: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/AtlasViewModelTest.kt`

**Interfaces:**
- Consumes: `SystemId` (core-model).
- Produces: on `AtlasRepository`: `suspend fun systems(): List<SystemId>`, `suspend fun structuresIn(system: SystemId): Set<StructureId>`, `suspend fun allStructures(): Set<StructureId>`. `FixtureStructure.system: String`. Fixture system ids `skeletal-system` and `muscular-system`, and a new branch `muscular` → `musculi-thoracis` → four muscles.

- [ ] **Step 1: Write the failing Room test**

Add to `DatabaseTest` (iosTest). It installs its own two-system manifest so the existing tests' fixture is untouched:

```kotlin
    @Test
    fun lists_the_systems_and_what_belongs_to_each() = runTest {
        PackInstaller(database).install(TWO_SYSTEMS, version = 1, meshUri = "file:///m.glb")
        val repository = com.ptk.anatomypro.core.data.repository.RoomAtlasRepository(database)

        assertEquals(
            listOf("muscular-system", "skeletal-system"),
            repository.systems().map { it.value },
        )
        assertEquals(
            setOf("1168-clavicula-left"),
            repository.structuresIn(com.ptk.anatomypro.core.model.SystemId("skeletal-system")).map { it.value }.toSet(),
        )
        assertEquals(
            setOf("1168-clavicula-left", "2001-musculus-subclavius-left"),
            repository.allStructures().map { it.value }.toSet(),
        )
    }
```

and at the bottom of the file:

```kotlin
private const val TWO_SYSTEMS = """
{
  "pack_id": "two-systems",
  "structures": [
    {
      "structure_id": "1168-clavicula-left", "ta2_id": "1168",
      "english": "Clavicle", "latin": "Clavicula", "definition": null,
      "system": "skeletal-system", "region": "trunk",
      "parent_id": null, "laterality": "L",
      "is_group": false, "nodes": ["1168__clavicula__L"], "triangles": 900
    },
    {
      "structure_id": "2001-musculus-subclavius-left", "ta2_id": "2001",
      "english": "Subclavius", "latin": "Musculus subclavius", "definition": null,
      "system": "muscular-system", "region": "trunk",
      "parent_id": null, "laterality": "L",
      "is_group": false, "nodes": ["2001__musculus_subclavius__L"], "triangles": 600
    }
  ]
}
"""
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:core-data:allTests`
Expected: FAIL — `systems`, `structuresIn`, `allStructures` unresolved.

- [ ] **Step 3: Add the queries and the contract**

`StructureDao`, under the taxonomy queries:

```kotlin
    /** Every system the installed atlas has structures in (screen 07). */
    @Query("SELECT DISTINCT systemId FROM structure WHERE systemId IS NOT NULL ORDER BY systemId")
    suspend fun systems(): List<String>

    @Query("SELECT id FROM structure WHERE systemId = :systemId")
    suspend fun idsInSystem(systemId: String): List<String>

    @Query("SELECT id FROM structure")
    suspend fun allIds(): List<String>
```

`AtlasRepository`, after `search`:

```kotlin
    /** The systems the installed atlas has structures in, by id (screen 07). */
    suspend fun systems(): List<SystemId>

    /** Every structure in [system], groups included: hiding a group hides nothing extra. */
    suspend fun structuresIn(system: SystemId): Set<StructureId>

    /** Every structure in the atlas: what isolation hides everything else of. */
    suspend fun allStructures(): Set<StructureId>
```

with `import com.ptk.anatomypro.core.model.SystemId`.

`RoomAtlasRepository`:

```kotlin
    override suspend fun systems(): List<SystemId> = dao.systems().map(::SystemId)

    override suspend fun structuresIn(system: SystemId): Set<StructureId> =
        dao.idsInSystem(system.value).mapTo(mutableSetOf(), ::StructureId)

    override suspend fun allStructures(): Set<StructureId> =
        dao.allIds().mapTo(mutableSetOf(), ::StructureId)
```

`StandInAtlasRepository` in `NotBuiltRepositoriesTest`:

```kotlin
    override suspend fun systems() = emptyList<com.ptk.anatomypro.core.model.SystemId>()
    override suspend fun structuresIn(system: com.ptk.anatomypro.core.model.SystemId) = emptySet<StructureId>()
    override suspend fun allStructures() = emptySet<StructureId>()
```

- [ ] **Step 4: Run to verify Room passes**

Run: `./gradlew :shared:core-data:allTests`
Expected: PASS on both targets. (`core-data-fake` does not compile yet — Step 6 fixes it.)

- [ ] **Step 5: Write the failing fake tests**

In `FakeAtlasRepositoryTest`, change the roots test and add three:

```kotlin
    @Test
    fun roots_are_the_structures_with_no_parent() = runTest {
        assertEquals(listOf("skeletal", "muscular"), repository.roots("pl").map { it.id.value })
    }

    @Test
    fun the_fixture_has_two_systems_so_a_toggle_can_be_exercised() = runTest {
        assertEquals(listOf("skeletal-system", "muscular-system"), repository.systems().map { it.value })
    }

    @Test
    fun a_system_contains_its_groups_and_its_leaves() = runTest {
        val muscles = repository.structuresIn(com.ptk.anatomypro.core.model.SystemId("muscular-system"))

        assertTrue(StructureId("musculi-thoracis") in muscles)
        assertTrue(StructureId("musculus-pectoralis-major") in muscles)
        assertTrue(StructureId("costa-vii") !in muscles)
    }

    @Test
    fun every_structure_is_in_the_atlas_wide_set() = runTest {
        assertEquals(AtlasFixture.all.size, repository.allStructures().size)
    }
```

In `FixtureTest`, add:

```kotlin
    @Test
    fun the_muscle_group_has_four_so_it_can_make_a_quiz_question() {
        assertEquals(4, AtlasFixture.childrenOf(StructureId("musculi-thoracis")).size)
    }

    @Test
    fun a_detail_reports_the_structures_own_system() {
        assertEquals("muscular-system", AtlasFixture.detail(StructureId("musculus-subclavius"), "en")?.systemId)
    }
```

- [ ] **Step 6: Extend the fixture and the fakes**

In `FixtureStructure`, after `isGroup`:

```kotlin
    /** The system id, in the form real packs use. */
    val system: String = "skeletal-system",
```

In `AtlasFixture`, after `cervicalVertebrae`:

```kotlin
    private val muscular = FixtureStructure(
        id = StructureId("muscular"),
        parent = null,
        names = mapOf("la" to "Systema musculare", "pl" to "Układ mięśniowy", "en" to "Muscular system"),
        definition = "The muscles of the body.",
        isGroup = true,
        system = "muscular-system",
    )

    private val thoracicMuscles = FixtureStructure(
        id = StructureId("musculi-thoracis"),
        parent = StructureId("muscular"),
        names = mapOf("la" to "Musculi thoracis", "pl" to "Mięśnie klatki piersiowej", "en" to "Thoracic muscles"),
        definition = "The muscles of the chest wall.",
        isGroup = true,
        system = "muscular-system",
    )

    /** Four, so the group can make a four-option quiz question like the others. */
    private val muscles: List<FixtureStructure> = listOf(
        Triple("musculus-pectoralis-major", "Musculus pectoralis major", "Mięsień piersiowy większy" to "Pectoralis major"),
        Triple("musculus-pectoralis-minor", "Musculus pectoralis minor", "Mięsień piersiowy mniejszy" to "Pectoralis minor"),
        Triple("musculus-serratus-anterior", "Musculus serratus anterior", "Mięsień zębaty przedni" to "Serratus anterior"),
        Triple("musculus-subclavius", "Musculus subclavius", "Mięsień podobojczykowy" to "Subclavius"),
    ).map { (id, latin, local) ->
        FixtureStructure(
            id = StructureId(id),
            parent = thoracicMuscles.id,
            names = mapOf("la" to latin, "pl" to local.first, "en" to local.second),
            definition = "$latin.",
            isGroup = false,
            system = "muscular-system",
        )
    }
```

Change `all`:

```kotlin
    val all: List<FixtureStructure> =
        listOf(skeletal, costae, cervicales) + ribs + cervicalVertebrae +
            listOf(muscular, thoracicMuscles) + muscles
```

In `detail(...)`, replace `systemId = "skeletal",` with `systemId = structure.system,`.

In `FakeAtlasRepository`:

```kotlin
    override suspend fun systems(): List<SystemId> = behaviour.respond {
        AtlasFixture.all.map { it.system }.distinct().map(::SystemId)
    }

    override suspend fun structuresIn(system: SystemId): Set<StructureId> = behaviour.respond {
        AtlasFixture.all.filter { it.system == system.value }.mapTo(mutableSetOf()) { it.id }
    }

    override suspend fun allStructures(): Set<StructureId> = behaviour.respond {
        AtlasFixture.all.mapTo(mutableSetOf()) { it.id }
    }
```

with `import com.ptk.anatomypro.core.model.SystemId`.

In `FakeQuizRepository.topics`, replace `system = SystemId("skeletal"),` with `system = SystemId(group.system),`.

- [ ] **Step 7: Update the atlas test the new root changes**

In `AtlasViewModelTest`, the first two tests now see two roots:

```kotlin
        assertEquals(listOf("Skeletal system", "Muscular system"), model.state.value.rows.map { it.summary.name })
```

```kotlin
        assertEquals(
            listOf("Skeletal system", "Ribs", "Cervical vertebrae", "Muscular system"),
            model.state.value.rows.map { it.summary.name },
        )
        assertEquals(listOf(0, 1, 1, 0), model.state.value.rows.map { it.depth })
```

- [ ] **Step 8: Run everything that reads the fixture**

Run: `./gradlew :shared:core-data:allTests :shared:core-data-fake:allTests :shared:feature-atlas:allTests :shared:feature-search:allTests :shared:feature-settings:allTests`
Expected: PASS on both targets.

- [ ] **Step 9: Commit**

```bash
git add shared/core-data shared/core-data-fake shared/feature-atlas
git commit -m "feat(core-data): list the atlas's systems and what belongs to each"
```

---

### Task 6: What the renderer should show, as a pure function

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/scene/Scene.kt`
- Create: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/scene/SceneResolverTest.kt`
- Create: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/scene/ApplyRenderStateTest.kt`

**Interfaces:**
- Consumes: `IsolationPolicy` and `Isolation` (existing, `com.ptk.anatomypro.feature.atlas`); `AnatomyRenderer`, `FakeAnatomyRenderer` (renderer-api).
- Produces, in package `com.ptk.anatomypro.feature.atlas.scene`: `enum class LayerMode { Visible, Ghosted, Hidden }`; `data class RenderState(hidden: Set<StructureId>, ghosted: Set<StructureId>, ghostAlpha: Float)` with `RenderState.None`; `data class FocusRequest(structure: StructureId, durationMs: Int, serial: Int)`; `object SceneResolver { fun resolve(layers: Map<SystemId, LayerMode>, membership: Map<SystemId, Set<StructureId>>, isolation: Isolation?, ghostAlpha: Float): RenderState }`; `fun applyRenderState(renderer: AnatomyRenderer, previous: RenderState, next: RenderState)`.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.Isolation
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneResolverTest {

    private val skin = SystemId("regions-of-human-body")
    private val muscle = SystemId("muscular-system")
    private val bone = SystemId("skeletal-system")
    private val epigastric = StructureId("regio-epigastrica")
    private val pectoralis = StructureId("musculus-pectoralis-major")
    private val clavicle = StructureId("clavicula-left")
    private val scapula = StructureId("scapula-left")
    private val membership = mapOf(
        skin to setOf(epigastric),
        muscle to setOf(pectoralis),
        bone to setOf(clavicle, scapula),
    )

    @Test
    fun nothing_set_shows_everything() {
        assertEquals(RenderState.None, SceneResolver.resolve(emptyMap(), membership, isolation = null, ghostAlpha = 0.3f))
    }

    @Test
    fun a_hidden_system_hides_its_structures_and_a_ghosted_one_ghosts_them() {
        val state = SceneResolver.resolve(
            layers = mapOf(skin to LayerMode.Hidden, muscle to LayerMode.Ghosted, bone to LayerMode.Visible),
            membership = membership,
            isolation = null,
            ghostAlpha = 0.3f,
        )

        assertEquals(setOf(epigastric), state.hidden)
        assertEquals(setOf(pectoralis), state.ghosted)
        assertEquals(0.3f, state.ghostAlpha)
    }

    @Test
    fun isolation_overrides_the_layers_because_it_is_the_narrower_request() {
        val state = SceneResolver.resolve(
            layers = mapOf(bone to LayerMode.Hidden),
            membership = membership,
            isolation = Isolation(focus = clavicle, ghosted = setOf(scapula), hidden = setOf(epigastric, pectoralis)),
            ghostAlpha = 0.34f,
        )

        assertEquals(setOf(epigastric, pectoralis), state.hidden)
        assertEquals(setOf(scapula), state.ghosted)
        assertEquals(0.34f, state.ghostAlpha)
    }

    @Test
    fun an_isolation_without_a_focus_falls_back_to_the_layers() {
        val state = SceneResolver.resolve(
            layers = mapOf(skin to LayerMode.Hidden),
            membership = membership,
            isolation = Isolation(focus = null, ghosted = emptySet(), hidden = emptySet()),
            ghostAlpha = 0.3f,
        )

        assertEquals(setOf(epigastric), state.hidden)
    }
}
```

- [ ] **Step 2: Write the failing applier test**

```kotlin
package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.FakeAnatomyRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The renderer's verbs are per call — setVisibility(x, false) then setVisibility(y, false)
 * hides both — so moving from one RenderState to the next is a diff. These tests hold the
 * fake to the end state, whatever the start.
 */
class ApplyRenderStateTest {

    private val a = StructureId("a")
    private val b = StructureId("b")
    private val c = StructureId("c")

    private fun assertShows(renderer: FakeAnatomyRenderer, state: RenderState) {
        assertEquals(state.hidden, renderer.hidden, "hidden")
        assertEquals(state.ghosted, renderer.ghosted, "ghosted")
        if (state.ghosted.isNotEmpty()) assertEquals(state.ghostAlpha, renderer.ghostAlpha, "alpha")
    }

    @Test
    fun from_nothing_to_a_state() {
        val renderer = FakeAnatomyRenderer()
        val next = RenderState(hidden = setOf(a), ghosted = setOf(b), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, next)

        assertShows(renderer, next)
    }

    @Test
    fun a_structure_moving_from_hidden_to_ghosted_is_shown_and_then_ghosted() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = setOf(a, b), ghosted = emptySet(), ghostAlpha = 0.3f)
        val second = RenderState(hidden = setOf(b), ghosted = setOf(a, c), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, second)

        assertShows(renderer, second)
    }

    @Test
    fun a_changed_alpha_is_resent_even_when_the_set_is_unchanged() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = emptySet(), ghosted = setOf(a), ghostAlpha = 0.3f)
        val second = first.copy(ghostAlpha = 0.5f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, second)

        assertEquals(0.5f, renderer.ghostAlpha)
    }

    @Test
    fun back_to_nothing_clears_everything() {
        val renderer = FakeAnatomyRenderer()
        val first = RenderState(hidden = setOf(a), ghosted = setOf(b), ghostAlpha = 0.3f)

        applyRenderState(renderer, RenderState.None, first)
        applyRenderState(renderer, first, RenderState.None)

        assertShows(renderer, RenderState.None)
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: FAIL — `RenderState`, `SceneResolver`, `applyRenderState` unresolved.

- [ ] **Step 4: Write Scene.kt**

```kotlin
package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.Isolation
import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/** How one system is drawn (screen 07). Each mode has a text label, never only a colour (§12). */
enum class LayerMode { Visible, Ghosted, Hidden }

/** Everything the renderer needs to know about visibility, as sets it understands. */
data class RenderState(
    val hidden: Set<StructureId>,
    val ghosted: Set<StructureId>,
    val ghostAlpha: Float,
) {
    companion object {
        val None = RenderState(hidden = emptySet(), ghosted = emptySet(), ghostAlpha = 1f)
    }
}

/**
 * Asks the camera to frame [structure]. [serial] makes asking for the same structure twice
 * a new request, so a screen can re-frame something the user scrolled away from.
 */
data class FocusRequest(val structure: StructureId, val durationMs: Int, val serial: Int)

object SceneResolver {

    /**
     * Layers and isolation in one answer. Isolation wins when it has a focus: it is the
     * narrower, more recent request, and the prototype shows it overriding the panel.
     */
    fun resolve(
        layers: Map<SystemId, LayerMode>,
        membership: Map<SystemId, Set<StructureId>>,
        isolation: Isolation?,
        ghostAlpha: Float,
    ): RenderState {
        if (isolation?.focus != null) {
            return RenderState(isolation.hidden, isolation.ghosted, ghostAlpha)
        }
        fun membersIn(mode: LayerMode) = layers.filterValues { it == mode }.keys
            .flatMapTo(mutableSetOf()) { membership[it].orEmpty() }

        val hidden = membersIn(LayerMode.Hidden)
        val ghosted = membersIn(LayerMode.Ghosted) - hidden
        if (hidden.isEmpty() && ghosted.isEmpty()) return RenderState.None
        return RenderState(hidden, ghosted, ghostAlpha)
    }
}

/**
 * Moves [renderer] from [previous] to [next] with the fewest calls. Show and un-ghost come
 * first, so a structure moving between the two sets is never briefly in both.
 */
fun applyRenderState(renderer: AnatomyRenderer, previous: RenderState, next: RenderState) {
    val show = previous.hidden - next.hidden
    if (show.isNotEmpty()) renderer.setVisibility(show, visible = true)

    val unghost = previous.ghosted - next.ghosted
    if (unghost.isNotEmpty()) renderer.setOpacity(unghost, alpha = 1f)

    val hide = next.hidden - previous.hidden
    if (hide.isNotEmpty()) renderer.setVisibility(hide, visible = false)

    val alphaChanged = next.ghostAlpha != previous.ghostAlpha
    val ghost = if (alphaChanged) next.ghosted else next.ghosted - previous.ghosted
    if (ghost.isNotEmpty()) renderer.setOpacity(ghost, next.ghostAlpha)
}
```

- [ ] **Step 5: Run them to verify they pass**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: PASS on both targets.

- [ ] **Step 6: Commit**

```bash
git add shared/feature-atlas
git commit -m "feat(feature-atlas): resolve layers and isolation to what the renderer shows"
```

---

### Task 7: AtlasSceneViewModel — one owner of what the renderer shows

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/scene/AtlasSceneViewModel.kt`
- Create: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/scene/AtlasSceneViewModelTest.kt`

**Interfaces:**
- Consumes: Task 5's repository methods; Task 6's types; `IsolationPolicy`.
- Produces: `class AtlasSceneViewModel(repository: AtlasRepository) : ViewModel()` with `panel: StateFlow<LayerPanelUiState>`, `render: StateFlow<RenderState>`, `cameraFocus: StateFlow<FocusRequest?>`, and `setLayer(system, mode)`, `resetLayers()`, `setIsolation(enabled)`, `setGhostPercent(percent)`, `onStructureSelected(id)`, `focusCamera(id)`. `data class LayerRow(system: SystemId, mode: LayerMode)`; `data class LayerPanelUiState(rows: List<LayerRow>, isolate: Boolean, focus: StructureId?, ghostPercent: Int, isLoading: Boolean)`; constants `DEFAULT_GHOST_PERCENT = 30`, `MIN_GHOST_PERCENT = 10`, `MAX_GHOST_PERCENT = 60`, `FOCUS_DURATION_MS = 600`; `SYSTEM_DISPLAY_ORDER: List<SystemId>`.

- [ ] **Step 1: Write the failing test**

It covers spec §9's three states for screen 07 — all visible; systems toggled; one isolated with neighbours ghosted — plus the camera requests screen 21 depends on.

```kotlin
package com.ptk.anatomypro.feature.atlas.scene

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtlasSceneViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val muscle = SystemId("muscular-system")
    private val bone = SystemId("skeletal-system")
    private val seventhRib = StructureId("costa-vii")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model() = AtlasSceneViewModel(FakeAtlasRepository())

    @Test
    fun everything_starts_visible_with_one_row_per_system() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(setOf(bone, muscle), model.panel.value.rows.map { it.system }.toSet())
        assertTrue(model.panel.value.rows.all { it.mode == LayerMode.Visible })
        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun rows_follow_the_display_order_from_the_body_surface_inwards() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        // Muscle before bone, as the prototype lists them.
        assertEquals(listOf(muscle, bone), model.panel.value.rows.map { it.system })
    }

    @Test
    fun hiding_a_system_hides_its_structures_and_only_them() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setLayer(muscle, LayerMode.Hidden)
        advanceUntilIdle()

        assertTrue(StructureId("musculus-subclavius") in model.render.value.hidden)
        assertTrue(seventhRib !in model.render.value.hidden)
        assertEquals(LayerMode.Hidden, model.panel.value.rows.single { it.system == muscle }.mode)
    }

    @Test
    fun ghosting_a_system_uses_the_panels_opacity() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setLayer(bone, LayerMode.Ghosted)
        advanceUntilIdle()

        assertTrue(seventhRib in model.render.value.ghosted)
        assertEquals(0.3f, model.render.value.ghostAlpha)
    }

    @Test
    fun isolating_the_selection_ghosts_its_siblings_and_hides_the_rest() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        val render = model.render.value
        assertTrue(StructureId("costa-vi") in render.ghosted)
        assertTrue(StructureId("musculus-subclavius") in render.hidden)
        assertTrue(seventhRib !in render.hidden && seventhRib !in render.ghosted)
    }

    @Test
    fun isolating_frames_the_isolated_structure() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        assertEquals(seventhRib, model.cameraFocus.value?.structure)
    }

    @Test
    fun isolation_with_nothing_selected_changes_nothing() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setIsolation(true)
        advanceUntilIdle()

        assertEquals(RenderState.None, model.render.value)
    }

    @Test
    fun the_opacity_is_clamped_to_what_still_reads_as_a_shell() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.setGhostPercent(95)
        advanceUntilIdle()
        assertEquals(MAX_GHOST_PERCENT, model.panel.value.ghostPercent)

        model.setGhostPercent(0)
        advanceUntilIdle()
        assertEquals(MIN_GHOST_PERCENT, model.panel.value.ghostPercent)
    }

    @Test
    fun reset_shows_everything_and_ends_isolation() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.setLayer(muscle, LayerMode.Hidden)
        model.onStructureSelected(seventhRib)
        model.setIsolation(true)
        advanceUntilIdle()

        model.resetLayers()
        advanceUntilIdle()

        assertEquals(RenderState.None, model.render.value)
        assertEquals(false, model.panel.value.isolate)
    }

    @Test
    fun asking_twice_for_the_same_structure_is_two_requests() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.focusCamera(seventhRib)
        val first = model.cameraFocus.value
        model.focusCamera(seventhRib)

        assertNotEquals(first, model.cameraFocus.value)
        assertEquals(FOCUS_DURATION_MS, model.cameraFocus.value?.durationMs)
    }

    @Test
    fun selecting_does_not_move_the_camera_by_itself() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onStructureSelected(seventhRib)
        advanceUntilIdle()

        assertNull(model.cameraFocus.value)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: FAIL — `AtlasSceneViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.ptk.anatomypro.feature.atlas.scene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.IsolationPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEFAULT_GHOST_PERCENT = 30
const val MIN_GHOST_PERCENT = 10
const val MAX_GHOST_PERCENT = 60
const val FOCUS_DURATION_MS = 600

/**
 * The prototype lists systems from the body's surface inwards: skin, then muscle, then
 * bone. Ids the list does not know go last, alphabetically, so new content still shows.
 */
val SYSTEM_DISPLAY_ORDER: List<SystemId> = listOf(
    "regions-of-human-body", "muscular-system", "visceral-systems", "cardiovascular-system",
    "lymphoid-organs", "nervous-system-sense-organs", "joints", "skeletal-system",
).map(::SystemId)

data class LayerRow(val system: SystemId, val mode: LayerMode)

data class LayerPanelUiState(
    val rows: List<LayerRow> = emptyList(),
    val isolate: Boolean = false,
    val focus: StructureId? = null,
    val ghostPercent: Int = DEFAULT_GHOST_PERCENT,
    val isLoading: Boolean = true,
)

/**
 * What the renderer shows, for every screen that draws the model.
 *
 * App-scoped rather than per screen: a system hidden on screen 07 stays hidden on the
 * atlas, and a structure focused in tree mode (screen 21) is framed when the model is next
 * on screen. Screens only ever read [render] and [cameraFocus]; this is the one writer.
 */
class AtlasSceneViewModel(private val repository: AtlasRepository) : ViewModel() {

    private val _panel = MutableStateFlow(LayerPanelUiState())
    val panel: StateFlow<LayerPanelUiState> = _panel.asStateFlow()

    private val _render = MutableStateFlow(RenderState.None)
    val render: StateFlow<RenderState> = _render.asStateFlow()

    private val _cameraFocus = MutableStateFlow<FocusRequest?>(null)
    val cameraFocus: StateFlow<FocusRequest?> = _cameraFocus.asStateFlow()

    private var membership: Map<SystemId, Set<StructureId>> = emptyMap()
    private var everything: Set<StructureId> = emptySet()
    private var serial = 0

    init {
        viewModelScope.launch {
            val systems = repository.systems()
            membership = systems.associateWith { repository.structuresIn(it) }
            everything = repository.allStructures()
            val ordered = systems.sortedWith(
                compareBy<SystemId>({ SYSTEM_DISPLAY_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.value }),
            )
            _panel.value = _panel.value.copy(
                rows = ordered.map { LayerRow(it, LayerMode.Visible) },
                isLoading = false,
            )
            resolve()
        }
    }

    fun setLayer(system: SystemId, mode: LayerMode) {
        _panel.value = _panel.value.copy(
            rows = _panel.value.rows.map { if (it.system == system) it.copy(mode = mode) else it },
        )
        launchResolve()
    }

    fun resetLayers() {
        _panel.value = _panel.value.copy(
            rows = _panel.value.rows.map { it.copy(mode = LayerMode.Visible) },
            isolate = false,
        )
        launchResolve()
    }

    fun setIsolation(enabled: Boolean) {
        _panel.value = _panel.value.copy(isolate = enabled)
        val focus = _panel.value.focus
        if (enabled && focus != null) focusCamera(focus)
        launchResolve()
    }

    fun setGhostPercent(percent: Int) {
        _panel.value = _panel.value.copy(ghostPercent = percent.coerceIn(MIN_GHOST_PERCENT, MAX_GHOST_PERCENT))
        launchResolve()
    }

    /** A structure was chosen anywhere — model, tree, list. The camera stays where it is. */
    fun onStructureSelected(id: StructureId?) {
        _panel.value = _panel.value.copy(focus = id)
        launchResolve()
    }

    /** An explicit request to frame [id]: tree mode, or isolation turning on. */
    fun focusCamera(id: StructureId) {
        _cameraFocus.value = FocusRequest(id, FOCUS_DURATION_MS, ++serial)
    }

    private fun launchResolve() {
        viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        val panel = _panel.value
        val focus = panel.focus
        val isolation = if (panel.isolate && focus != null) {
            IsolationPolicy.resolve(focus, siblingsOf(focus), everything)
        } else {
            null
        }
        _render.value = SceneResolver.resolve(
            layers = panel.rows.associate { it.system to it.mode },
            membership = membership,
            isolation = isolation,
            ghostAlpha = panel.ghostPercent / 100f,
        )
    }

    /** The structures under the same parent — the neighbours isolation ghosts. */
    private suspend fun siblingsOf(id: StructureId): Set<StructureId> {
        val parent = repository.detail(id, LATIN)?.ancestors?.lastOrNull() ?: return emptySet()
        return repository.children(parent.id, LATIN).mapTo(mutableSetOf()) { it.id }
    }

    private companion object {
        const val LATIN = "la"
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: PASS on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-atlas
git commit -m "feat(feature-atlas): one app-scoped owner of what the renderer shows"
```

---

### Task 8: The canvas draws the scene

`AnatomyCanvas` builds its own renderer and only accepts a highlight, so nothing can hide, ghost or move the camera. It now takes a `RenderState` and a `FocusRequest`. Both are applied only once the pack has loaded: before that the renderer has no node names to resolve, and a state sent early would be lost.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.kt`
- Modify: `shared/src/androidMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.ios.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AtlasTab.kt`

**Interfaces:**
- Consumes: `RenderState`, `FocusRequest`, `applyRenderState`, `AtlasSceneViewModel`.
- Produces: `AnatomyCanvas(modifier, highlighted, render: RenderState, focus: FocusRequest?, onPicked, onStats)`; `AtlasTab(..., scene: AtlasSceneViewModel?, ...)`; an app-scoped scene ViewModel created in `MainScaffold`.

- [ ] **Step 1: Widen the expect declaration**

```kotlin
@Composable
expect fun AnatomyCanvas(
    modifier: Modifier,
    highlighted: StructureId?,
    render: RenderState,
    focus: FocusRequest?,
    onPicked: (StructureId?) -> Unit,
    onStats: (CanvasStats) -> Unit,
)
```

with imports `com.ptk.anatomypro.feature.atlas.scene.RenderState` and `FocusRequest`.

- [ ] **Step 2: Apply them in both actuals**

Add the two parameters to each `actual fun AnatomyCanvas` signature, then add after the existing `Picked` collector in **both** files:

```kotlin
    // The renderer resolves structures to nodes from the loaded pack, so nothing can be
    // hidden, ghosted or framed before it arrives; a state sent early would be dropped.
    var packLoaded by remember(renderer) { mutableStateOf(false) }
    val applied = remember(renderer) { arrayOf(RenderState.None) }

    LaunchedEffect(renderer) {
        renderer.events.filterIsInstance<RendererEvent.PackLoaded>().collect {
            applied[0] = RenderState.None
            packLoaded = true
        }
    }

    LaunchedEffect(renderer, render, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        applyRenderState(renderer, applied[0], render)
        applied[0] = render
    }

    LaunchedEffect(renderer, focus, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        focus?.let { renderer.focusCamera(it.structure, it.durationMs) }
    }
```

Add the imports `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `com.ptk.anatomypro.feature.atlas.scene.RenderState`, `com.ptk.anatomypro.feature.atlas.scene.FocusRequest`, `com.ptk.anatomypro.feature.atlas.scene.applyRenderState`, and `com.ptk.anatomypro.renderer.api.RendererEvent` where missing.

- [ ] **Step 3: Create the scene above the NavHost and pass it down**

In `MainScaffold` (`App.kt`), before `Column`:

```kotlin
    // App-scoped: a system hidden on screen 07 stays hidden on the atlas, and a structure
    // focused in tree mode is framed when the model is next on screen.
    val scene: AtlasSceneViewModel? = dependencies.atlas?.let { atlas ->
        viewModel(key = "atlas-scene") { AtlasSceneViewModel(atlas) }
    }
```

Pass `scene = scene` to `AtlasTab`. In `AtlasTab.kt`, add `scene: AtlasSceneViewModel?` to `AtlasTab` and `BrowseRoute`. At the top of `BrowseRoute`, collect the scene — the stand-in flows are remembered, or a null scene would create a new flow on every recomposition:

```kotlin
    val noRender = remember { MutableStateFlow(RenderState.None) }
    val noFocus = remember { MutableStateFlow<FocusRequest?>(null) }
    val render by (scene?.render ?: noRender).collectAsState()
    val focus by (scene?.cameraFocus ?: noFocus).collectAsState()
```

(imports: `kotlinx.coroutines.flow.MutableStateFlow`, `com.ptk.anatomypro.feature.atlas.scene.AtlasSceneViewModel`, `RenderState`, `FocusRequest`). Then replace the canvas call:

```kotlin
            canvas = { canvasModifier ->
                AnatomyCanvas(
                    modifier = canvasModifier,
                    highlighted = state.selected,
                    render = render,
                    focus = focus,
                    onPicked = { picked ->
                        model.onPickedInModel(picked)
                        scene?.onStructureSelected(picked)
                    },
                    onStats = { stats = it },
                )
            },
```

and in `onRowSelected`, after `model.onRowSelected(summary)`:

```kotlin
                scene?.onStructureSelected(summary.id.takeUnless { summary.isGroup })
```

- [ ] **Step 4: Build both apps and run every test**

```bash
./gradlew allTests :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL. Nothing visible changes yet — every layer starts visible.

- [ ] **Step 5: Commit**

```bash
git add shared/src
git commit -m "feat: the canvas draws the scene's visibility and camera requests"
```

---

### Task 9: Screen 07 — the layer panel

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/layers/SystemNames.kt`
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/layers/LayersScreen.kt`
- Modify: `shared/feature-atlas/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AtlasTab.kt`, `App.kt`

**Interfaces:**
- Consumes: `AtlasSceneViewModel`, `LayerPanelUiState`, `LayerMode`, `AnatomyCanvas`.
- Produces: `LayersScreen(state, focusName, canvas, onMode, onReset, onIsolate, onGhostPercent, modifier)`; `LayersRoute(repository, scene, locale)`; `BrowseRoute` and `AtlasTab` gain `onLayers: () -> Unit`; `@Composable fun systemNames(system: SystemId): SystemName` with `data class SystemName(latin: String, local: String)`; a `composable<AtlasRoute.Layers>` destination reached from a "LAYERS" action in the atlas header.

- [ ] **Step 1: Add the strings**

Append to `shared/feature-atlas/src/commonMain/composeResources/values/strings.xml`:

```xml
    <string name="layers_title">Layers</string>
    <string name="layers_reset">RESET ALL</string>
    <string name="layers_mode_visible">ON</string>
    <string name="layers_mode_ghosted">GHOST</string>
    <string name="layers_mode_hidden">OFF</string>
    <string name="layers_state_visible">Fully visible</string>
    <string name="layers_state_ghosted">Ghosted at %1$d %%</string>
    <string name="layers_state_hidden">%1$s · hidden</string>
    <string name="layers_isolation_heading">ISOLATION</string>
    <string name="layers_isolate">Isolate selected structure</string>
    <string name="layers_isolate_target">%1$s · neighbours ghosted</string>
    <string name="layers_isolate_none">Select a structure first</string>
    <string name="layers_ghost_opacity">Ghost opacity</string>
    <string name="layers_ghost_value">%1$d %%</string>
    <string name="layers_ghost_less">Less opaque</string>
    <string name="layers_ghost_more">More opaque</string>
    <string name="layers_canvas_isolated">%1$s ISOLATED · NEIGHBOURS AT %2$d %%</string>
    <string name="system_regions_of_human_body">Regions of the body</string>
    <string name="system_muscular_system">Muscular system</string>
    <string name="system_visceral_systems">Visceral systems</string>
    <string name="system_cardiovascular_system">Cardiovascular system</string>
    <string name="system_lymphoid_organs">Lymphoid organs</string>
    <string name="system_nervous_system_sense_organs">Nervous system and sense organs</string>
    <string name="system_joints">Joints</string>
    <string name="system_skeletal_system">Skeletal system</string>
```

and to `values-pl/strings.xml`:

```xml
    <string name="layers_title">Warstwy</string>
    <string name="layers_reset">RESETUJ</string>
    <string name="layers_mode_visible">WŁ.</string>
    <string name="layers_mode_ghosted">CIEŃ</string>
    <string name="layers_mode_hidden">WYŁ.</string>
    <string name="layers_state_visible">W pełni widoczny</string>
    <string name="layers_state_ghosted">Półprzezroczysty, %1$d %%</string>
    <string name="layers_state_hidden">%1$s · ukryty</string>
    <string name="layers_isolation_heading">IZOLACJA</string>
    <string name="layers_isolate">Izoluj wybraną strukturę</string>
    <string name="layers_isolate_target">%1$s · sąsiedzi półprzezroczyści</string>
    <string name="layers_isolate_none">Najpierw wybierz strukturę</string>
    <string name="layers_ghost_opacity">Krycie cienia</string>
    <string name="layers_ghost_value">%1$d %%</string>
    <string name="layers_ghost_less">Mniej kryjący</string>
    <string name="layers_ghost_more">Bardziej kryjący</string>
    <string name="layers_canvas_isolated">%1$s IZOLOWANA · SĄSIEDZI %2$d %%</string>
    <string name="system_regions_of_human_body">Okolice ciała</string>
    <string name="system_muscular_system">Układ mięśniowy</string>
    <string name="system_visceral_systems">Narządy wewnętrzne</string>
    <string name="system_cardiovascular_system">Układ sercowo-naczyniowy</string>
    <string name="system_lymphoid_organs">Narządy limfatyczne</string>
    <string name="system_nervous_system_sense_organs">Układ nerwowy i narządy zmysłów</string>
    <string name="system_joints">Stawy</string>
    <string name="system_skeletal_system">Układ kostny</string>
```

Append to `shared/src/commonMain/composeResources/values/strings.xml`: `<string name="atlas_layers">LAYERS</string>`; and to `values-pl`: `<string name="atlas_layers">WARSTWY</string>`.

- [ ] **Step 2: Write SystemNames.kt**

The Latin names are content, not UI copy — Latin is the canonical key, the same in every interface language — so they are a map, not resources. **They are unverified medical terms** and belong on the reviewer's list (design spec §7).

```kotlin
package com.ptk.anatomypro.feature.atlas.layers

import androidx.compose.runtime.Composable
import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.system_cardiovascular_system
import anatomypro.shared.feature_atlas.generated.resources.system_joints
import anatomypro.shared.feature_atlas.generated.resources.system_lymphoid_organs
import anatomypro.shared.feature_atlas.generated.resources.system_muscular_system
import anatomypro.shared.feature_atlas.generated.resources.system_nervous_system_sense_organs
import anatomypro.shared.feature_atlas.generated.resources.system_regions_of_human_body
import anatomypro.shared.feature_atlas.generated.resources.system_skeletal_system
import anatomypro.shared.feature_atlas.generated.resources.system_visceral_systems
import com.ptk.anatomypro.core.model.SystemId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class SystemName(val latin: String, val local: String)

private class Known(val latin: String, val local: StringResource)

private val KNOWN: Map<String, Known> = mapOf(
    "regions-of-human-body" to Known("Regiones corporis", Res.string.system_regions_of_human_body),
    "muscular-system" to Known("Systema musculare", Res.string.system_muscular_system),
    "visceral-systems" to Known("Splanchnologia", Res.string.system_visceral_systems),
    "cardiovascular-system" to Known("Systema cardiovasculare", Res.string.system_cardiovascular_system),
    "lymphoid-organs" to Known("Organa lymphoidea", Res.string.system_lymphoid_organs),
    "nervous-system-sense-organs" to Known("Systema nervosum et organa sensuum", Res.string.system_nervous_system_sense_organs),
    "joints" to Known("Juncturae", Res.string.system_joints),
    "skeletal-system" to Known("Systema skeletale", Res.string.system_skeletal_system),
)

/** A system's Latin name and its name in the interface language. Unknown ids show as themselves. */
@Composable
fun systemNames(system: SystemId): SystemName {
    val known = KNOWN[system.value] ?: return SystemName(system.value, system.value)
    return SystemName(known.latin, stringResource(known.local))
}
```

- [ ] **Step 3: Write LayersScreen.kt**

Each row offers its three modes as three labelled 44-dp targets, so a mode is never conveyed by colour alone (§12).

```kotlin
package com.ptk.anatomypro.feature.atlas.layers

import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.layers_canvas_isolated
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_less
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_more
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_opacity
import anatomypro.shared.feature_atlas.generated.resources.layers_ghost_value
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate_none
import anatomypro.shared.feature_atlas.generated.resources.layers_isolate_target
import anatomypro.shared.feature_atlas.generated.resources.layers_isolation_heading
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_ghosted
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_hidden
import anatomypro.shared.feature_atlas.generated.resources.layers_mode_visible
import anatomypro.shared.feature_atlas.generated.resources.layers_reset
import anatomypro.shared.feature_atlas.generated.resources.layers_state_ghosted
import anatomypro.shared.feature_atlas.generated.resources.layers_state_hidden
import anatomypro.shared.feature_atlas.generated.resources.layers_state_visible
import anatomypro.shared.feature_atlas.generated.resources.layers_title
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import com.ptk.anatomypro.core.model.SystemId
import com.ptk.anatomypro.feature.atlas.scene.LayerMode
import com.ptk.anatomypro.feature.atlas.scene.LayerPanelUiState
import com.ptk.anatomypro.feature.atlas.scene.LayerRow
import org.jetbrains.compose.resources.stringResource

private const val GHOST_STEP = 5

/** Prototype screen 07: the model above, the panel below. */
@Composable
fun LayersScreen(
    state: LayerPanelUiState,
    focusName: String?,
    canvas: @Composable (Modifier) -> Unit,
    onMode: (SystemId, LayerMode) -> Unit,
    onReset: () -> Unit,
    onIsolate: (Boolean) -> Unit,
    onGhostPercent: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            canvas(Modifier.fillMaxSize())
            if (state.isolate && focusName != null) {
                Text(
                    text = stringResource(Res.string.layers_canvas_isolated, focusName.uppercase(), state.ghostPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(Res.string.layers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.layers_reset),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onReset).padding(top = 14.dp),
                )
            }

            for (row in state.rows) LayerRowView(row, state.ghostPercent, onMode)

            Text(stringResource(Res.string.layers_isolation_heading), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.layers_isolate), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = focusName?.let { stringResource(Res.string.layers_isolate_target, it) }
                            ?: stringResource(Res.string.layers_isolate_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
                Switch(checked = state.isolate, onCheckedChange = onIsolate, enabled = state.focus != null)
            }

            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.layers_ghost_opacity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Step("−", stringResource(Res.string.layers_ghost_less)) { onGhostPercent(state.ghostPercent - GHOST_STEP) }
                Text(stringResource(Res.string.layers_ghost_value, state.ghostPercent), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
                Step("+", stringResource(Res.string.layers_ghost_more)) { onGhostPercent(state.ghostPercent + GHOST_STEP) }
            }
        }
    }
}

@Composable
private fun LayerRowView(row: LayerRow, ghostPercent: Int, onMode: (SystemId, LayerMode) -> Unit) {
    val names = systemNames(row.system)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(names.latin, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when (row.mode) {
                    LayerMode.Visible -> stringResource(Res.string.layers_state_visible)
                    LayerMode.Ghosted -> stringResource(Res.string.layers_state_ghosted, ghostPercent)
                    LayerMode.Hidden -> stringResource(Res.string.layers_state_hidden, names.local)
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
        for (mode in LayerMode.entries) {
            val isSelected = row.mode == mode
            Text(
                text = stringResource(
                    when (mode) {
                        LayerMode.Visible -> Res.string.layers_mode_visible
                        LayerMode.Ghosted -> Res.string.layers_mode_ghosted
                        LayerMode.Hidden -> Res.string.layers_mode_hidden
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Accent else TextTertiary,
                modifier = Modifier
                    .heightIn(min = 44.dp).widthIn(min = 52.dp)
                    .semantics { role = Role.RadioButton; selected = isSelected }
                    .clickable { onMode(row.system, mode) }
                    .padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun Step(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick),
    ) { Text(glyph, style = MaterialTheme.typography.titleLarge, color = Accent) }
}
```

- [ ] **Step 4: Add the route and the entry point**

In `AtlasTab.kt`, add an `onLayers: () -> Unit` parameter to `AtlasTab` and `BrowseRoute` (`AtlasTab` passes it through), and in `BrowseRoute`'s header row, immediately before the search action:

```kotlin
            Text(
                text = stringResource(Res.string.atlas_layers),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onLayers).padding(top = 14.dp),
            )
```

(thread `onLayers` through `AtlasTab`), and add:

```kotlin
@Composable
fun LayersRoute(
    repository: AtlasRepository?,
    scene: AtlasSceneViewModel?,
    locale: String,
) {
    if (repository == null || scene == null) {
        Opening()
        return
    }
    val panel by scene.panel.collectAsState()
    val render by scene.render.collectAsState()
    val focus by scene.cameraFocus.collectAsState()
    var focusName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(panel.focus, locale) {
        focusName = panel.focus?.let { repository.summary(it, locale)?.name }
    }
    LayersScreen(
        state = panel,
        focusName = focusName,
        canvas = { modifier ->
            AnatomyCanvas(
                modifier = modifier,
                highlighted = panel.focus,
                render = render,
                focus = focus,
                onPicked = scene::onStructureSelected,
                onStats = {},
            )
        },
        onMode = scene::setLayer,
        onReset = scene::resetLayers,
        onIsolate = scene::setIsolation,
        onGhostPercent = scene::setGhostPercent,
    )
}
```

In `App.kt`'s NavHost:

```kotlin
                composable<AtlasRoute.Layers> {
                    LayersRoute(repository = dependencies.atlas, scene = scene, locale = locale)
                }
```

and pass `onLayers = { navController.navigate(AtlasRoute.Layers) }` to `AtlasTab`.

- [ ] **Step 5: Build, test, and check by hand on the emulator**

```bash
./gradlew allTests :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64
```

Then install the debug APK and confirm, on the real pack:
1. Atlas header shows WARSTWY; it opens the panel with one row per system, skin first, bone last.
2. Skin → WYŁ.: the white surface shell disappears; picking where it was now selects the muscle beneath.
3. Muscle → CIEŃ: muscles become a pale shell; tapping one still selects it (ghosts stay pickable).
4. Select a structure, turn isolation on: neighbours ghost, everything else hides, the camera frames it, the canvas caption names it.
5. Back to the atlas: the hidden layers stay hidden there.
6. RESETUJ: everything returns.
7. Switch the interface to English on the Profile tab and come back: labels change, the scene stays.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "feat: screen 07, the layer panel"
```

---

### Task 10: StructureTreeViewModel — the hierarchy, one level at a time

The prototype's screen 21 is not screen 04's expandable tree: it shows one level, with the path above it, and moves deeper or up a level. That is the shape a screen reader navigates well — a short list, a known position — which is the point of the screen.

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/tree/StructureTreeViewModel.kt`
- Create: `shared/feature-atlas/src/commonTest/kotlin/com/ptk/anatomypro/feature/atlas/tree/StructureTreeViewModelTest.kt`

**Interfaces:**
- Consumes: `AtlasRepository.roots`, `children`; `AtlasError`.
- Produces: `class StructureTreeViewModel(repository: AtlasRepository, locale: String)` with `state: StateFlow<TreeUiState>`, `onFocus(index)`, `onEnter(index)`, `onUp()`. `data class TreeUiState(path: List<StructureSummary>, items: List<StructureSummary>, focusedIndex: Int?, isLoading: Boolean, error: AtlasError?)` with `level: Int`, `focused: StructureSummary?`, `announcement: TreeAnnouncement?`. `sealed interface TreeAnnouncement { data class Focused(name, position, total); data class Level(level, name: String?, count) }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ptk.anatomypro.feature.atlas.tree

import com.ptk.anatomypro.core.data.fake.FakeAtlasRepository
import com.ptk.anatomypro.core.data.fake.FakeBehaviour
import com.ptk.anatomypro.feature.atlas.AtlasError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StructureTreeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun model() = StructureTreeViewModel(FakeAtlasRepository(), locale = "en")

    private fun StructureTreeViewModel.names() = state.value.items.map { it.name }

    @Test
    fun starts_at_the_top_level_with_nothing_focused() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        assertEquals(listOf("Skeletal system", "Muscular system"), model.names())
        assertEquals(1, model.state.value.level)
        assertNull(model.state.value.focused)
    }

    @Test
    fun entering_a_structure_lists_its_children_and_announces_the_level() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onEnter(1) // Muscular system
        advanceUntilIdle()
        model.onEnter(0) // Thoracic muscles
        advanceUntilIdle()

        assertEquals(4, model.state.value.items.size)
        assertEquals(3, model.state.value.level)
        assertEquals(listOf("Muscular system", "Thoracic muscles"), model.state.value.path.map { it.name })
        assertEquals(TreeAnnouncement.Level(level = 3, name = "Thoracic muscles", count = 4), model.state.value.announcement)
    }

    @Test
    fun focusing_announces_the_name_and_the_position() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()
        model.onEnter(0)
        advanceUntilIdle()

        model.onFocus(0)

        assertEquals("Pectoralis major", model.state.value.focused?.name)
        assertEquals(TreeAnnouncement.Focused(name = "Pectoralis major", position = 1, total = 4), model.state.value.announcement)
    }

    @Test
    fun a_leaf_cannot_be_entered() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()
        model.onEnter(0)
        advanceUntilIdle()

        model.onEnter(0) // a muscle: no children
        advanceUntilIdle()

        assertEquals(3, model.state.value.level)
    }

    @Test
    fun going_up_returns_to_the_parent_level_with_the_parent_focused() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.onEnter(1)
        advanceUntilIdle()

        model.onUp()
        advanceUntilIdle()

        assertEquals(1, model.state.value.level)
        assertEquals("Muscular system", model.state.value.focused?.name)
    }

    @Test
    fun up_at_the_top_does_nothing() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()

        model.onUp()
        advanceUntilIdle()

        assertEquals(1, model.state.value.level)
    }

    @Test
    fun a_failing_atlas_is_an_error_not_an_empty_level() = runTest(dispatcher) {
        val model = StructureTreeViewModel(
            FakeAtlasRepository(FakeBehaviour(failure = { IllegalStateException("no database") })),
            locale = "en",
        )
        advanceUntilIdle()

        assertEquals(AtlasError.LoadFailed, model.state.value.error)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: FAIL — `StructureTreeViewModel` unresolved.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.ptk.anatomypro.feature.atlas.tree

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.StructureSummary
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.feature.atlas.AtlasError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen reader should say next. A kind and values; the screen picks the words. */
sealed interface TreeAnnouncement {
    data class Focused(val name: String, val position: Int, val total: Int) : TreeAnnouncement
    data class Level(val level: Int, val name: String?, val count: Int) : TreeAnnouncement
}

data class TreeUiState(
    /** From the top level down to the structure whose children are listed. Empty at the top. */
    val path: List<StructureSummary> = emptyList(),
    val items: List<StructureSummary> = emptyList(),
    val focusedIndex: Int? = null,
    val announcement: TreeAnnouncement? = null,
    val isLoading: Boolean = true,
    val error: AtlasError? = null,
) {
    /** 1 at the top, as the prototype counts it ("POZIOM 3"). */
    val level: Int get() = path.size + 1
    val focused: StructureSummary? get() = focusedIndex?.let(items::getOrNull)
}

/** Prototype screen 21: the hierarchy as a sequence of short lists (design spec §12). */
class StructureTreeViewModel(
    private val repository: AtlasRepository,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TreeUiState())
    val state: StateFlow<TreeUiState> = _state.asStateFlow()

    init {
        show(path = emptyList(), focusId = null)
    }

    fun onFocus(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        _state.value = current.copy(
            focusedIndex = index,
            announcement = TreeAnnouncement.Focused(item.name, position = index + 1, total = current.items.size),
        )
    }

    fun onEnter(index: Int) {
        val current = _state.value
        val item = current.items.getOrNull(index) ?: return
        if (!item.hasChildren) return
        show(path = current.path + item, focusId = null)
    }

    fun onUp() {
        val current = _state.value
        if (current.path.isEmpty()) return
        show(path = current.path.dropLast(1), focusId = current.path.last().id.value)
    }

    private fun show(path: List<StructureSummary>, focusId: String?) {
        viewModelScope.launch {
            runCatching {
                val parent = path.lastOrNull()
                if (parent == null) repository.roots(locale) else repository.children(parent.id, locale)
            }.onSuccess { items ->
                val focused = focusId?.let { id -> items.indexOfFirst { it.id.value == id } }?.takeIf { it >= 0 }
                _state.value = TreeUiState(
                    path = path,
                    items = items,
                    focusedIndex = focused,
                    announcement = TreeAnnouncement.Level(path.size + 1, path.lastOrNull()?.name, items.size),
                    isLoading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = AtlasError.LoadFailed)
            }
        }
    }
}
```

Note `going_up_returns_to_the_parent_level_with_the_parent_focused` expects the parent focused but announcement remains the level announcement; the test asserts only `focused`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :shared:feature-atlas:allTests`
Expected: PASS on both targets.

- [ ] **Step 5: Commit**

```bash
git add shared/feature-atlas
git commit -m "feat(feature-atlas): walk the hierarchy one level at a time, with announcements"
```

---

### Task 11: Screen 21 — structure tree mode

When the existing **Tryb drzewa struktur** setting is on, the Atlas tab starts on this list instead of the canvas — as the setting's own subtitle says ("Lista zamiast modelu 3D"). Focusing a row asks the scene to frame it, so the model shows that structure when the canvas is next on screen (design spec §12: "selecting a structure focuses the camera").

**Files:**
- Create: `shared/feature-atlas/src/commonMain/kotlin/com/ptk/anatomypro/feature/atlas/tree/TreeScreen.kt`
- Modify: `shared/feature-atlas/src/commonMain/composeResources/values/strings.xml`, `values-pl/strings.xml`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/AtlasTab.kt`, `App.kt`

**Interfaces:**
- Consumes: Task 10's ViewModel; `AtlasSceneViewModel.focusCamera`, `onStructureSelected`.
- Produces: `TreeScreen(state, onFocus, onEnter, onUp, onOpen, modifier)`; `TreeRoute(repository, scene, locale, onOpenDetail)`.

- [ ] **Step 1: Add the strings**

`values/strings.xml`:

```xml
    <string name="tree_badge">TREE MODE</string>
    <string name="tree_level">LEVEL %1$d · %2$s</string>
    <string name="tree_level_top">LEVEL 1</string>
    <plurals name="tree_count">
        <item quantity="one">%1$d structure · swipe right to go deeper</item>
        <item quantity="other">%1$d structures · swipe right to go deeper</item>
    </plurals>
    <string name="tree_focused_badge">FOCUSED %1$d/%2$d</string>
    <string name="tree_announce_focused">%1$s, %2$d of %3$d</string>
    <plurals name="tree_announce_level">
        <item quantity="one">Level %1$d, %2$s, %3$d structure</item>
        <item quantity="other">Level %1$d, %2$s, %3$d structures</item>
    </plurals>
    <string name="tree_up">Up a level</string>
    <string name="tree_open">Open structure</string>
    <string name="tree_enter">Go deeper</string>
    <string name="tree_top_name">top</string>
```

`values-pl/strings.xml`:

```xml
    <string name="tree_badge">TRYB DRZEWA</string>
    <string name="tree_level">POZIOM %1$d · %2$s</string>
    <string name="tree_level_top">POZIOM 1</string>
    <plurals name="tree_count">
        <item quantity="one">%1$d struktura · przesuń w prawo, aby wejść głębiej</item>
        <item quantity="few">%1$d struktury · przesuń w prawo, aby wejść głębiej</item>
        <item quantity="many">%1$d struktur · przesuń w prawo, aby wejść głębiej</item>
        <item quantity="other">%1$d struktury · przesuń w prawo, aby wejść głębiej</item>
    </plurals>
    <string name="tree_focused_badge">WYBRANA %1$d/%2$d</string>
    <string name="tree_announce_focused">%1$s, %2$d z %3$d</string>
    <plurals name="tree_announce_level">
        <item quantity="one">Poziom %1$d, %2$s, %3$d struktura</item>
        <item quantity="few">Poziom %1$d, %2$s, %3$d struktury</item>
        <item quantity="many">Poziom %1$d, %2$s, %3$d struktur</item>
        <item quantity="other">Poziom %1$d, %2$s, %3$d struktury</item>
    </plurals>
    <string name="tree_up">Poziom wyżej</string>
    <string name="tree_open">Otwórz strukturę</string>
    <string name="tree_enter">Wejdź głębiej</string>
    <string name="tree_top_name">początek</string>
```

- [ ] **Step 2: Write TreeScreen.kt**

The announcement is a polite live region, so TalkBack and VoiceOver read it when it changes. Each row is one focusable unit; "go deeper" is both a visible 44-dp target and a custom accessibility action, so it works by swipe as well as by tap.

```kotlin
package com.ptk.anatomypro.feature.atlas.tree

import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_focused
import anatomypro.shared.feature_atlas.generated.resources.tree_announce_level
import anatomypro.shared.feature_atlas.generated.resources.tree_badge
import anatomypro.shared.feature_atlas.generated.resources.tree_count
import anatomypro.shared.feature_atlas.generated.resources.tree_enter
import anatomypro.shared.feature_atlas.generated.resources.tree_focused_badge
import anatomypro.shared.feature_atlas.generated.resources.tree_level
import anatomypro.shared.feature_atlas.generated.resources.tree_level_top
import anatomypro.shared.feature_atlas.generated.resources.tree_open
import anatomypro.shared.feature_atlas.generated.resources.tree_top_name
import anatomypro.shared.feature_atlas.generated.resources.tree_up
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.Accent
import com.ptk.anatomypro.core.designsystem.TextTertiary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Prototype screen 21: an equivalent of the canvas, not a fallback (design spec §12). */
@Composable
fun TreeScreen(
    state: TreeUiState,
    onFocus: (Int) -> Unit,
    onEnter: (Int) -> Unit,
    onUp: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterLabel = stringResource(Res.string.tree_enter)

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.tree_badge), style = MaterialTheme.typography.labelSmall, color = Accent)
        Text(
            text = state.path.lastOrNull()?.let { stringResource(Res.string.tree_level, state.level, (it.latinName ?: it.name).uppercase()) }
                ?: stringResource(Res.string.tree_level_top),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Text(
            text = pluralStringResource(Res.plurals.tree_count, state.items.size, state.items.size),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )

        // Spoken, not shown: the polite live region is what a screen reader announces.
        Text(
            text = announcementText(state.announcement),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.items, key = { _, item -> item.id.value }) { index, item ->
                val isFocused = index == state.focusedIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .semantics {
                            selected = isFocused
                            if (item.hasChildren) {
                                customActions = listOf(CustomAccessibilityAction(enterLabel) { onEnter(index); true })
                            }
                        }
                        .clickable { onFocus(index) },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.latinName ?: item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal)
                        if (item.latinName != null && item.latinName != item.name) {
                            Text(item.name, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        }
                        if (isFocused) {
                            Text(
                                stringResource(Res.string.tree_focused_badge, index + 1, state.items.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = Accent,
                            )
                        }
                    }
                    if (item.hasChildren) {
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = Accent,
                            modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clickable { onEnter(index) }.padding(top = 8.dp, start = 16.dp),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onUp, enabled = state.path.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.tree_up))
            }
            Button(onClick = onOpen, enabled = state.focused != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.tree_open))
            }
        }
    }
}

@Composable
private fun announcementText(announcement: TreeAnnouncement?): String = when (announcement) {
    null -> ""
    is TreeAnnouncement.Focused ->
        stringResource(Res.string.tree_announce_focused, announcement.name, announcement.position, announcement.total)
    is TreeAnnouncement.Level -> pluralStringResource(
        Res.plurals.tree_announce_level,
        announcement.count,
        announcement.level,
        announcement.name ?: stringResource(Res.string.tree_top_name),
        announcement.count,
    )
}
```

- [ ] **Step 3: Add TreeRoute and switch the Atlas tab on the setting**

In `AtlasTab.kt`:

```kotlin
@Composable
fun TreeRoute(
    repository: AtlasRepository?,
    scene: AtlasSceneViewModel?,
    locale: String,
    onOpenDetail: (StructureId) -> Unit,
) {
    if (repository == null) {
        Opening()
        return
    }
    val model: StructureTreeViewModel = viewModel(key = "tree-$locale") { StructureTreeViewModel(repository, locale) }
    val state by model.state.collectAsState()
    TreeScreen(
        state = state,
        onFocus = { index ->
            model.onFocus(index)
            state.items.getOrNull(index)?.let { item ->
                scene?.onStructureSelected(item.id.takeUnless { item.isGroup })
                scene?.focusCamera(item.id)
            }
        },
        onEnter = model::onEnter,
        onUp = model::onUp,
        onOpen = { state.focused?.let { onOpenDetail(it.id) } },
    )
}
```

In `App.kt`, the `composable<AtlasRoute.Browse>` entry becomes:

```kotlin
                composable<AtlasRoute.Browse> {
                    if (state.settings.structureTreeMode) {
                        TreeRoute(
                            repository = dependencies.atlas,
                            scene = scene,
                            locale = locale,
                            onOpenDetail = openDetail,
                        )
                    } else {
                        AtlasTab(
                            repository = dependencies.atlas,
                            scene = scene,
                            locale = locale,
                            latinOnly = state.settings.nameDisplay == NameDisplay.LatinOnly,
                            onOpenDetail = openDetail,
                            onSearch = { navController.navigate(AtlasRoute.Search) },
                            onLayers = { navController.navigate(AtlasRoute.Layers) },
                        )
                    }
                }
```

`focusCamera` on a group is a no-op in both renderers — a group draws nothing of its own — so focusing a group row moves no camera. That is accepted for now (see Out of scope).

- [ ] **Step 4: Build, test, and check by hand**

```bash
./gradlew allTests :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64
```

On the emulator:
1. Profile → turn on **Tryb drzewa struktur**; the Atlas tab shows TRYB DRZEWA and POZIOM 1.
2. Go deeper twice with ›; the level line and the count (Polish plural) follow.
3. Tap a leaf: it shows WYBRANA n/m; "Otwórz strukturę" opens its detail; back returns to the same level.
4. Turn tree mode off: the canvas returns framed on the structure last focused.
5. Turn on TalkBack (Settings › Accessibility). Focus a row and confirm it speaks "<name>, n z m"; use the "Wejdź głębiej" action from the TalkBack actions menu.
6. Switch the interface to English: the level, count and buttons change; the current level is kept.

- [ ] **Step 5: Commit**

```bash
git add shared
git commit -m "feat: screen 21, structure tree mode"
```

---

### Task 12: Record what now exists

**Files:**
- Modify: `docs/state-of-play.md`
- Modify: `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` (§26.8)

- [ ] **Step 1: Update §26.8**

Replace the bullet beginning "`IsolationPolicy` has no production caller yet" with: `IsolationPolicy` now has its production caller — screen 07 through `AtlasSceneViewModel` — and the chain taxonomy → `Isolation` → renderer → shim runs end to end on both platforms. Replace "Android still throws `TODO()` on both verbs" with the Task 1 commit hash and "verified by the instrumented contract on the API 36 emulator; not measured on hardware".

- [ ] **Step 2: Update the state of play**

Under "What exists", record: screens 07 and 21 built; `focusCamera` on both platforms; Android hide/ghost verified on the emulator. Under "Not verified": nothing on iOS hardware; iOS draws only the toy pack, so screens 07 and 21 are exercised there against three cubes.

- [ ] **Step 3: Run the full check**

```bash
./gradlew check
./gradlew :shared:renderer-filament:connectedAndroidDeviceTest
```

Expected: BUILD SUCCESSFUL; 11 Android contract tests, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs: record screens 07 and 21, and camera focus on both platforms"
```

---

## Done when

- Screen 07 hides, ghosts and shows each system and isolates the selection, on the real pack on Android and on the toy pack on iOS; layer state survives navigation and a language switch.
- Screen 21 walks the hierarchy level by level, announces focus and level through a live region, and frames the focused structure on the canvas.
- `focusCamera` passes the shared contract on the fake, iOS and Android; Android `setVisibility` / `setOpacity` pass all of it.
- `./gradlew check` passes; no UI literal outside `composeResources`.

## Out of scope, recorded

- Spatial relations ("Relacje przestrzenne") and the per-row descriptor on screen 21: no data source exists.
- Framing a **group** in tree mode: groups draw nothing, so the camera does not move. Framing a group would mean framing the union of its descendants.
- `setCameraPose`, orbit and pan.
- The real pack on iOS.
- The Latin system names in `SystemNames.kt` are unverified content and go on the reviewer's list (design spec §7).

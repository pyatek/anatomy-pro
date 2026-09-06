# Phase 0 Module Slice — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the four Kotlin Multiplatform modules and the shared build configuration that Phase 0's feasibility gate needs, with the renderer seam in place and every module covered by tests that run without a GPU.

**Architecture:** A `build-logic` included build supplies two convention plugins so each module's build file stays about four lines. Four modules sit under `shared/` in a strict dependency line — `core-model` ← `renderer-api` ← `renderer-filament`, with `core-designsystem` depending on `renderer-api` for highlight types. The existing `shared` module becomes an umbrella that assembles `Shared.framework` for iOS. Both Filament implementations are deliberate stubs; implementing them *is* Phase 0.

**Tech Stack:** Kotlin 2.4.10, AGP 9.0.1, Gradle 9.1, Compose Multiplatform 1.11.1, kotlinx-coroutines 1.10.2, SQLDelight and Ktor deliberately absent until Phase 1.

**Spec:** `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` — §3.1 (target layout), §4 (renderer boundary), §5 (data model), §12 (accessibility), §15 (testing), §16 (phasing), and §20 (this slice's scope and rationale).

## Global Constraints

Every task's requirements implicitly include this section.

- **Versions are fixed by `gradle/libs.versions.toml`.** Kotlin `2.4.10`, AGP `9.0.1`, Compose Multiplatform `1.11.1`, Gradle `9.1`. Do not bump any of them in this plan.
- **Android:** `compileSdk = 36`, `minSdk = 30`, `targetSdk = 36`, `jvmTarget = JVM_11`.
- **iOS targets:** `iosArm64` and `iosSimulatorArm64` only. No `iosX64` — the project targets Apple Silicon simulators.
- **Package root:** `com.ptk.anatomypro`. Each module uses a sub-package matching its name (`core.model`, `renderer.api`, `renderer.filament`, `core.designsystem`), and its Android `namespace` matches that package exactly.
- **Dependency direction (spec §3.1):** nothing may depend on `renderer-filament` except the `shared` umbrella and the platform entry points. Feature modules — none exist yet — may never depend on each other.
- **Highlighting uses outline and luminance, never hue alone** (spec §12). This is enforced by a test in Task 5, not by convention.
- **`@JvmInline value class` in `commonMain` requires an explicit `import kotlin.jvm.JvmInline`.** Verified during planning; without it Kotlin/Native fails with `Unresolved reference 'JvmInline'`.
- **Never run `./gradlew updateDaemonJvm`.** It regenerates `gradle/gradle-daemon-jvm.properties`, which makes Android Studio sync fail instantly and silently on macOS 26. See the initial commit message for the full diagnosis.
- **Commits carry no `Co-Authored-By` or `Claude-Session` trailers.** Subject and body only.
- Expect the warning `Kotlin does not yet support 25 JDK target, falling back to Kotlin JVM_24` when building `build-logic` on a JDK 25 daemon. It is benign and is not pinned away, because Gradle toolchain auto-download is not configured in this build (no foojay resolver) and pinning would break on machines without a local JDK 21.

---

### Task 1: `build-logic` included build and convention plugins

Creates the shared build configuration every later task depends on. Nothing else in this plan works until this task is green.

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/anatomypro.kmp.library.gradle.kts`
- Create: `build-logic/src/main/kotlin/anatomypro.kmp.compose.gradle.kts`
- Modify: `gradle/libs.versions.toml` (add plugin-marker library aliases and coroutines)
- Modify: `settings.gradle.kts` (add `includeBuild`)
- Modify: `README.md` (record the `updateDaemonJvm` hazard)

**Interfaces:**
- Consumes: nothing.
- Produces: two plugin ids applied by every later module — `anatomypro.kmp.library` (Kotlin Multiplatform + Android library, iOS targets, JVM 11) and `anatomypro.kmp.compose` (the former, plus Compose Multiplatform and the Compose compiler). Neither sets an Android `namespace`; every consuming module must set its own.

- [ ] **Step 1: Add catalog entries for the plugin markers and coroutines**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
coroutines = "1.10.2"
```

Add to `[libraries]` (these are the plugin *marker* artifacts, needed on `build-logic`'s compile classpath so the convention plugins can reference the plugin ids; coordinates verified against the local Gradle cache):

```toml
androidKmpLibrary-gradlePlugin = { module = "com.android.kotlin.multiplatform.library:com.android.kotlin.multiplatform.library.gradle.plugin", version.ref = "agp" }
kotlinMultiplatform-gradlePlugin = { module = "org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin", version.ref = "kotlin" }
composeMultiplatform-gradlePlugin = { module = "org.jetbrains.compose:org.jetbrains.compose.gradle.plugin", version.ref = "composeMultiplatform" }
composeCompiler-gradlePlugin = { module = "org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: Create the `build-logic` build**

`build-logic/settings.gradle.kts` — note it re-declares the version catalog, because an included build does not inherit the root build's catalog:

```kotlin
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
```

`build-logic/build.gradle.kts`:

```kotlin
plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.androidKmpLibrary.gradlePlugin)
    implementation(libs.kotlinMultiplatform.gradlePlugin)
    implementation(libs.composeMultiplatform.gradlePlugin)
    implementation(libs.composeCompiler.gradlePlugin)
}
```

- [ ] **Step 3: Write the base convention plugin**

`build-logic/src/main/kotlin/anatomypro.kmp.library.gradle.kts`. The `VersionCatalogsExtension` lookup is the documented workaround for precompiled script plugins, which cannot use the generated `libs` accessor:

```kotlin
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }
}
```

- [ ] **Step 4: Write the Compose convention plugin**

`build-logic/src/main/kotlin/anatomypro.kmp.compose.gradle.kts`:

```kotlin
plugins {
    id("anatomypro.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
```

If applying a sibling precompiled plugin by id fails to resolve, the fallback is to drop the first line here and have Compose modules list both ids in their own `plugins` block instead:
`plugins { id("anatomypro.kmp.library"); id("anatomypro.kmp.compose") }`.

- [ ] **Step 5: Wire the included build into the root build**

In `settings.gradle.kts`, add `includeBuild("build-logic")` as the first line inside the existing `pluginManagement` block, before `repositories`:

```kotlin
pluginManagement {
    includeBuild("build-logic")

    repositories {
```

- [ ] **Step 6: Verify `build-logic` compiles**

Run: `cd build-logic && ../gradlew compileKotlin && cd ..`
Expected: `BUILD SUCCESSFUL`, with the benign JDK 25 target warning. A failure here means a plugin marker coordinate is wrong — recheck Step 1 against `~/.gradle/caches/modules-2/files-2.1/`.

- [ ] **Step 7: Verify the root build still configures**

Run: `./gradlew projects`
Expected: `BUILD SUCCESSFUL`, listing `:androidApp` and `:shared` unchanged. This proves `includeBuild` did not disturb existing plugin resolution.

- [ ] **Step 8: Record the daemon-JVM hazard in the README**

Append to `README.md`:

```markdown
## Build notes

Do not run `./gradlew updateDaemonJvm`. It writes `gradle/gradle-daemon-jvm.properties`,
whose Daemon JVM criteria make Android Studio sync fail instantly and silently on
macOS 26 with `Service 'SystemInfo' is not available`. The IDE runs the Gradle Tooling
API client with native-platform services disabled, and the criteria file sends the
launcher into toolchain provisioning, which needs those services. The CLI is unaffected,
so the failure looks like a broken project. Set the daemon JVM through Android Studio's
Gradle JDK setting instead.

Shared build configuration lives in `build-logic/` as the `anatomypro.kmp.library` and
`anatomypro.kmp.compose` convention plugins. Module build files should stay short; if one
grows past a handful of lines, the configuration probably belongs in a convention plugin.
```

- [ ] **Step 9: Commit**

```bash
git add build-logic gradle/libs.versions.toml settings.gradle.kts README.md
git commit -m "build: add build-logic convention plugins

Four modules land next, growing to twelve by Phase 4, each needing
identical Kotlin Multiplatform and Android library configuration.
Centralising it now avoids editing every module to retrofit it later.

buildSrc was rejected because any edit to it invalidates the
configuration cache, which this build enables."
```

---

### Task 2: `core-model` — domain identity types

Spec §5 defines a `Structure` record with names, definitions, and verification maps. This task ships **identity types only**; the full record arrives with `core-data` in Phase 1, because it is meaningless without persistence to hold it (spec §20.2).

**Files:**
- Create: `shared/core-model/build.gradle.kts`
- Create: `shared/core-model/src/commonMain/kotlin/com/ptk/anatomypro/core/model/Ids.kt`
- Create: `shared/core-model/src/commonMain/kotlin/com/ptk/anatomypro/core/model/Laterality.kt`
- Create: `shared/core-model/src/commonMain/kotlin/com/ptk/anatomypro/core/model/MeshRef.kt`
- Create: `shared/core-model/src/commonMain/kotlin/com/ptk/anatomypro/core/model/VerificationState.kt`
- Test: `shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/StructureIdTest.kt`
- Test: `shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/LateralityTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: the `anatomypro.kmp.library` plugin id from Task 1.
- Produces: `StructureId(value: String)`, `SystemId(value: String)`, `RegionId(value: String)`, `PackId(value: String)` — all `@JvmInline value class` over `String`; `Laterality` enum with `LEFT`, `RIGHT`, `MEDIAN` and `fun opposite(): Laterality?`; `MeshRef(packId: PackId, nodeName: String)`; `VerificationState` enum with `UNVERIFIED`, `VERIFIED`, `DISPUTED`.

- [ ] **Step 1: Register the module**

Create `shared/core-model/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.library") }

kotlin { android { namespace = "com.ptk.anatomypro.core.model" } }
```

In `settings.gradle.kts`, add below `include(":shared")`:

```kotlin
include(":shared:core-model")
```

- [ ] **Step 2: Write the failing tests**

`shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/StructureIdTest.kt`:

```kotlin
package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StructureIdTest {

    @Test
    fun accepts_a_lowercase_kebab_slug() {
        assertEquals("scapula-left", StructureId("scapula-left").value)
    }

    @Test
    fun rejects_blank() {
        assertFailsWith<IllegalArgumentException> { StructureId("") }
    }

    @Test
    fun rejects_uppercase_because_ids_are_stable_slugs_and_case_drift_would_split_them() {
        assertFailsWith<IllegalArgumentException> { StructureId("Scapula") }
    }

    @Test
    fun rejects_spaces_and_underscores() {
        assertFailsWith<IllegalArgumentException> { StructureId("scapula left") }
        assertFailsWith<IllegalArgumentException> { StructureId("scapula_left") }
    }
}
```

`shared/core-model/src/commonTest/kotlin/com/ptk/anatomypro/core/model/LateralityTest.kt`:

```kotlin
package com.ptk.anatomypro.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LateralityTest {

    @Test
    fun left_and_right_are_opposites() {
        assertEquals(Laterality.RIGHT, Laterality.LEFT.opposite())
        assertEquals(Laterality.LEFT, Laterality.RIGHT.opposite())
    }

    @Test
    fun median_has_no_opposite() {
        assertNull(Laterality.MEDIAN.opposite())
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :shared:core-model:allTests`
Expected: FAIL — unresolved references `StructureId` and `Laterality`.

- [ ] **Step 4: Write the implementation**

`Ids.kt`:

```kotlin
package com.ptk.anatomypro.core.model

import kotlin.jvm.JvmInline

private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

private fun requireSlug(value: String, kind: String): String {
    require(value.matches(SLUG)) {
        "$kind must be a lowercase kebab-case slug, was: '$value'"
    }
    return value
}

/** Stable identifier for an anatomical structure. Never reused once assigned (spec §5). */
@JvmInline
value class StructureId(val value: String) {
    init { requireSlug(value, "StructureId") }
}

@JvmInline
value class SystemId(val value: String) {
    init { requireSlug(value, "SystemId") }
}

@JvmInline
value class RegionId(val value: String) {
    init { requireSlug(value, "RegionId") }
}

/** Unit of download, entitlement, and verification release (spec §6). */
@JvmInline
value class PackId(val value: String) {
    init { requireSlug(value, "PackId") }
}
```

`Laterality.kt`:

```kotlin
package com.ptk.anatomypro.core.model

/**
 * Left and right scapula are two meshes but one concept (spec §5). A quiz asking for
 * "scapula" must accept either, while a harder question may ask for a specific side.
 */
enum class Laterality {
    LEFT,
    RIGHT,
    MEDIAN;

    /** The mirrored side, or null for median structures, which have none. */
    fun opposite(): Laterality? = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        MEDIAN -> null
    }
}
```

`MeshRef.kt`:

```kotlin
package com.ptk.anatomypro.core.model

/**
 * One node of geometry inside a pack. A structure holds a *list* of these: the vertebral
 * column and muscle groups span many nodes, and assuming one mesh per structure breaks on
 * real data (spec §5).
 */
data class MeshRef(val packId: PackId, val nodeName: String) {
    init { require(nodeName.isNotBlank()) { "nodeName must not be blank" } }
}
```

`VerificationState.kt`:

```kotlin
package com.ptk.anatomypro.core.model

/**
 * Tracked per locale, not per structure: a Latin name may be correct while its Polish
 * translation is wrong, and a single flag would force re-verifying everything whenever one
 * language is corrected (spec §5).
 *
 * Only VERIFIED structures may be used as quiz answers (spec §7).
 */
enum class VerificationState {
    UNVERIFIED,
    VERIFIED,
    DISPUTED,
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:core-model:allTests`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add shared/core-model settings.gradle.kts
git commit -m "feat(core-model): add domain identity types

Identity types only. The full Structure record from spec section 5
arrives with core-data in Phase 1, because names, definitions, and
verification maps are meaningless without persistence to hold them.

Ids validate as lowercase kebab slugs at construction. Spec section 5
requires ids be stable and never reused, and case drift would silently
split one structure into two."
```

---

### Task 3: `renderer-api` — the renderer boundary

Spec §4 calls this the most important architectural line in the system. The interface is reproduced from the spec; the fake is what makes spec §15's "no GPU in CI" claim true.

**Files:**
- Create: `shared/renderer-api/build.gradle.kts`
- Create: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/AnatomyRenderer.kt`
- Create: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/RendererEvent.kt`
- Create: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/RenderTypes.kt`
- Create: `shared/renderer-api/src/commonMain/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRenderer.kt`
- Test: `shared/renderer-api/src/commonTest/kotlin/com/ptk/anatomypro/renderer/api/FakeAnatomyRendererTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `StructureId`, `SystemId`, `PackId` from Task 2.
- Produces: `AnatomyRenderer` (the spec §4 interface); `RendererEvent` sealed hierarchy; `MeshSource(uri: String)`; `HighlightStyle(outlineArgb: Int, outlineWidthDp: Float, fillLuminanceShift: Float)`; `CameraPose(targetX, targetY, targetZ, distance, azimuthDeg, elevationDeg: Float)`; `FakeAnatomyRenderer` with `emitted: List<RendererEvent>`, `isolated: StructureId?`, `highlighted: Set<StructureId>`, and the test hook `emitPick(structure: StructureId?)`.

Note: `FakeAnatomyRenderer` lives in `commonMain`, **not** `commonTest`. Kotlin Multiplatform has no working equivalent of Java test fixtures, so a fake confined to a test source set cannot be consumed by other modules' tests — which is exactly what spec §15 requires of it. See spec §20.3.

- [ ] **Step 1: Register the module**

Create `shared/renderer-api/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.renderer.api" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core-model"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

In `settings.gradle.kts`, add below the `core-model` line:

```kotlin
include(":shared:renderer-api")
```

- [ ] **Step 2: Write the failing contract test**

`FakeAnatomyRendererTest.kt`. This is the contract every real renderer must also satisfy, so it is written against the interface, not the fake's internals:

```kotlin
package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeAnatomyRendererTest {

    private val pack = PackId("skeletal-thorax")
    private val scapula = StructureId("scapula-left")

    @Test
    fun signals_ready_then_pack_loaded_when_a_pack_is_loaded() = runTest {
        val renderer = FakeAnatomyRenderer()

        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        assertEquals(
            listOf(RendererEvent.Ready, RendererEvent.PackLoaded(pack)),
            renderer.emitted,
        )
    }

    @Test
    fun reports_a_pick_as_an_event_so_the_quiz_loop_is_testable_without_a_gpu() = runTest {
        val renderer = FakeAnatomyRenderer()
        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        renderer.emitPick(scapula)

        assertEquals(RendererEvent.Picked(scapula), renderer.emitted.last())
    }

    @Test
    fun reports_a_miss_as_a_pick_of_nothing() = runTest {
        val renderer = FakeAnatomyRenderer()

        renderer.emitPick(null)

        assertEquals(RendererEvent.Picked(null), renderer.emitted.last())
    }

    @Test
    fun holds_isolation_and_highlight_state_so_a_lost_surface_can_be_replayed() = runTest {
        val renderer = FakeAnatomyRenderer()

        val style = HighlightStyle(outlineArgb = 0xFF1E88E5.toInt(), outlineWidthDp = 2f, fillLuminanceShift = 0.25f)

        renderer.isolate(scapula, ghostNeighbours = true)
        renderer.highlight(setOf(scapula), style)

        assertEquals(scapula, renderer.isolated)
        assertEquals(setOf(scapula), renderer.highlighted)
    }

    @Test
    fun forgets_a_pack_state_on_unload() = runTest {
        val renderer = FakeAnatomyRenderer()
        renderer.loadPack(pack, MeshSource("file:///packs/skeletal-thorax.glb"))

        renderer.unloadPack(pack)

        assertTrue(renderer.loadedPacks.isEmpty())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :shared:renderer-api:allTests`
Expected: FAIL — unresolved references `FakeAnatomyRenderer`, `MeshSource`, `RendererEvent`, `HighlightStyle`.

- [ ] **Step 4: Write the types**

`RenderTypes.kt`:

```kotlin
package com.ptk.anatomypro.renderer.api

import kotlin.jvm.JvmInline

/** Where a pack's glTF payload can be read from, already resolved to a concrete location. */
@JvmInline
value class MeshSource(val uri: String) {
    init { require(uri.isNotBlank()) { "MeshSource uri must not be blank" } }
}

/**
 * How a highlighted structure is drawn.
 *
 * Carries both an outline and a luminance shift because spec §12 forbids conveying
 * highlight state through hue alone — that is unreadable to colour-blind users. Concrete
 * token values live in core-designsystem; this module owns only the shape.
 */
data class HighlightStyle(
    val outlineArgb: Int,
    val outlineWidthDp: Float,
    val fillLuminanceShift: Float,
) {
    init {
        require(outlineWidthDp > 0f) { "outlineWidthDp must be positive" }
        require(fillLuminanceShift in -1f..1f) { "fillLuminanceShift must be in -1..1" }
    }
}

/** Camera position in orbit terms around a focus point. */
data class CameraPose(
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val distance: Float,
    val azimuthDeg: Float,
    val elevationDeg: Float,
) {
    init { require(distance > 0f) { "distance must be positive" } }
}
```

- [ ] **Step 5: Write the event hierarchy**

`RendererEvent.kt`, reproduced from spec §4:

```kotlin
package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId

sealed interface RendererEvent {
    data object Ready : RendererEvent
    data class Picked(val structure: StructureId?) : RendererEvent
    data class PackLoaded(val pack: PackId) : RendererEvent
    data class LoadProgress(val pack: PackId, val fraction: Float) : RendererEvent
    data class MemoryPressure(val residentBytes: Long) : RendererEvent
    data class Error(val code: String, val message: String) : RendererEvent
}
```

- [ ] **Step 6: Write the interface**

`AnatomyRenderer.kt`, reproduced from spec §4:

```kotlin
package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow

/**
 * Draws and picks geometry. Nothing else.
 *
 * Kotlin owns all state; the renderer is a pure projection of it and holds no truth the
 * app cannot reconstruct. That is what lets a lost render surface be recovered by replaying
 * app state, and what makes a three.js implementation a drop-in fallback (spec §4, §14).
 */
interface AnatomyRenderer {
    val events: Flow<RendererEvent>

    suspend fun loadPack(pack: PackId, source: MeshSource)
    suspend fun unloadPack(pack: PackId)

    fun setSystemVisibility(system: SystemId, visible: Boolean)
    fun setOpacity(structures: Set<StructureId>, alpha: Float)
    fun isolate(structure: StructureId?, ghostNeighbours: Boolean)
    fun highlight(structures: Set<StructureId>, style: HighlightStyle)

    fun focusCamera(structure: StructureId, durationMs: Int)
    fun setCameraPose(pose: CameraPose)

    fun setPickingEnabled(enabled: Boolean)
}
```

- [ ] **Step 7: Write the fake**

`FakeAnatomyRenderer.kt`. It uses a replaying `MutableSharedFlow` so tests can assert on `emitted` synchronously without collecting:

```kotlin
package com.ptk.anatomypro.renderer.api

import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.core.model.SystemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory [AnatomyRenderer] for tests and Compose previews.
 *
 * Deliberately in commonMain rather than commonTest: Kotlin Multiplatform has no working
 * equivalent of Java test fixtures, so a fake confined to a test source set could not be
 * used by other modules — which is the whole point of it (spec §15, §20.3).
 */
class FakeAnatomyRenderer(replay: Int = 64) : AnatomyRenderer {

    private val _events = MutableSharedFlow<RendererEvent>(replay = replay)
    override val events: Flow<RendererEvent> = _events.asSharedFlow()

    /** Every event emitted so far, newest last. */
    val emitted: List<RendererEvent> get() = _events.replayCache

    var loadedPacks: Set<PackId> = emptySet()
        private set
    var isolated: StructureId? = null
        private set
    var highlighted: Set<StructureId> = emptySet()
        private set
    var highlightStyle: HighlightStyle? = null
        private set
    var hiddenSystems: Set<SystemId> = emptySet()
        private set
    var cameraPose: CameraPose? = null
        private set
    var pickingEnabled: Boolean = true
        private set

    override suspend fun loadPack(pack: PackId, source: MeshSource) {
        if (loadedPacks.isEmpty()) _events.emit(RendererEvent.Ready)
        loadedPacks = loadedPacks + pack
        _events.emit(RendererEvent.PackLoaded(pack))
    }

    override suspend fun unloadPack(pack: PackId) {
        loadedPacks = loadedPacks - pack
    }

    override fun setSystemVisibility(system: SystemId, visible: Boolean) {
        hiddenSystems = if (visible) hiddenSystems - system else hiddenSystems + system
    }

    override fun setOpacity(structures: Set<StructureId>, alpha: Float) = Unit

    override fun isolate(structure: StructureId?, ghostNeighbours: Boolean) {
        isolated = structure
    }

    override fun highlight(structures: Set<StructureId>, style: HighlightStyle) {
        highlighted = structures
        highlightStyle = style
    }

    override fun focusCamera(structure: StructureId, durationMs: Int) = Unit

    override fun setCameraPose(pose: CameraPose) {
        cameraPose = pose
    }

    override fun setPickingEnabled(enabled: Boolean) {
        pickingEnabled = enabled
    }

    /** Test hook: simulate the user tapping [structure], or empty space when null. */
    suspend fun emitPick(structure: StructureId?) {
        _events.emit(RendererEvent.Picked(structure))
    }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-api:allTests`
Expected: PASS, 5 tests.

- [ ] **Step 9: Commit**

```bash
git add shared/renderer-api settings.gradle.kts
git commit -m "feat(renderer-api): add the renderer boundary and its fake

Spec section 4's interface, verbatim, plus the fake that makes section
15's no-GPU-in-CI claim true. The contract test is written against the
interface rather than the fake's internals, so the Filament
implementations can be held to the same tests.

The fake ships in commonMain, not commonTest: Kotlin Multiplatform has
no working test-fixtures equivalent, so a fake in a test source set
cannot be consumed by the modules that need it."
```

---

### Task 4: `renderer-filament` — the platform seam

Both implementations are stubs that throw. Implementing them **is** Phase 0; this task builds the socket so the dependency direction and the fallback seam are settled before the risky work starts (spec §20.3).

**Files:**
- Create: `shared/renderer-filament/build.gradle.kts`
- Create: `shared/renderer-filament/src/commonMain/kotlin/com/ptk/anatomypro/renderer/filament/AnatomyRendererFactory.kt`
- Create: `shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/AnatomyRendererFactory.android.kt`
- Create: `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/AnatomyRendererFactory.ios.kt`
- Create: `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentBridge.kt`
- Test: `shared/renderer-filament/src/commonTest/kotlin/com/ptk/anatomypro/renderer/filament/AnatomyRendererFactoryTest.kt`
- Create: `ios-renderer/README.md`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `AnatomyRenderer` from Task 3.
- Produces: `expect fun createAnatomyRenderer(): AnatomyRenderer`, throwing `NotImplementedError` on both platforms until Phase 0 implements it; on iOS, `FilamentBridge` (the protocol Swift implements) and `FilamentBridgeRegistry.bridge` (the injection point).

- [ ] **Step 1: Register the module**

Create `shared/renderer-filament/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.library") }

kotlin {
    android { namespace = "com.ptk.anatomypro.renderer.filament" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:renderer-api"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

In `settings.gradle.kts`, add:

```kotlin
include(":shared:renderer-filament")
```

- [ ] **Step 2: Write the failing test**

`AnatomyRendererFactoryTest.kt`. This test pins that the factory exists and is callable, and documents the stub. **Phase 0's first act is to change this test** — it is a tripwire, not a permanent assertion:

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AnatomyRendererFactoryTest {

    /**
     * The factory is a deliberate stub until Phase 0 implements the Filament hosts.
     * When Phase 0 lands, delete this test and replace it with the renderer-api contract
     * tests run against the real implementation.
     */
    @Test
    fun factory_is_wired_but_not_yet_implemented() {
        assertFailsWith<NotImplementedError> { createAnatomyRenderer() }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: FAIL — unresolved reference `createAnatomyRenderer`.

- [ ] **Step 4: Write the expect declaration**

`AnatomyRendererFactory.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

/**
 * Builds the platform's Filament-backed renderer.
 *
 * Nothing may depend on this module except the shared umbrella and the platform entry
 * points (spec §3.1). Keeping the dependency here and not in feature modules is what makes
 * the three.js fallback in spec §14 a link-time decision rather than a rewrite.
 */
expect fun createAnatomyRenderer(): AnatomyRenderer
```

- [ ] **Step 5: Write the Android actual**

`AnatomyRendererFactory.android.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

actual fun createAnatomyRenderer(): AnatomyRenderer =
    TODO("Phase 0: host Filament through SceneView and implement AnatomyRenderer")
```

- [ ] **Step 6: Write the iOS actual and the Swift bridge seam**

`AnatomyRendererFactory.ios.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.renderer.api.AnatomyRenderer

actual fun createAnatomyRenderer(): AnatomyRenderer =
    TODO("Phase 0: implement AnatomyRenderer over FilamentBridgeRegistry.bridge")
```

`FilamentBridge.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

/**
 * The narrow seam the Swift/Obj-C++ Filament host implements.
 *
 * Kotlin declares the protocol and Swift injects an implementation at startup, so the
 * Kotlin framework has no compile-time dependency on Filament (spec §4.1). Node names, not
 * StructureIds, cross this boundary: mapping node names back to structures is the app's
 * job, and keeping that knowledge out of the shim is what keeps the shim narrow.
 */
interface FilamentBridge {
    fun initialize()
    fun loadModel(uri: String)
    fun unloadModel(uri: String)
    fun setNodeVisibility(nodeNames: List<String>, visible: Boolean)
    fun setHighlight(nodeNames: List<String>, outlineArgb: Int, luminanceShift: Float)
    fun pickAt(xPx: Float, yPx: Float)
    fun dispose()
}

/** Where the Swift host registers itself before the first renderer is created. */
object FilamentBridgeRegistry {
    var bridge: FilamentBridge? = null
}
```

- [ ] **Step 7: Create the iOS host placeholder**

`ios-renderer/README.md`:

```markdown
# ios-renderer

The Swift and Objective-C++ Filament host for iOS. Empty until Phase 0.

This target implements `FilamentBridge` (declared in Kotlin, in the `renderer-filament`
module's `iosMain`) and registers itself with `FilamentBridgeRegistry.bridge` during app
startup. The Kotlin framework never links Filament directly, which makes the graphics
provider a link-time choice rather than a code change (spec §4.1).

Retiring the risk that this target is unworkable is the entire purpose of Phase 0. If it
proves unworkable, the documented fallback is three.js in a WKWebView behind the same
`AnatomyRenderer` interface (spec §14).
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :shared:renderer-filament:allTests`
Expected: PASS, 1 test. (`TODO(...)` throws `NotImplementedError`, which is what the test asserts.)

- [ ] **Step 9: Verify the iOS source set compiles**

Run: `./gradlew :shared:renderer-filament:compileKotlinIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL`. This proves the `expect`/`actual` pair matches on Apple targets, which a host-only test run would not catch.

- [ ] **Step 10: Commit**

```bash
git add shared/renderer-filament ios-renderer settings.gradle.kts
git commit -m "feat(renderer-filament): add the platform seam as deliberate stubs

Both actuals throw. Implementing them is Phase 0 itself; this commit
builds the socket so the dependency direction and the three.js fallback
seam are settled before the risky work starts.

Node names rather than StructureIds cross the Swift bridge. Mapping node
names to structures is the app's job, and keeping that out of the shim is
what keeps the shim narrow enough to reimplement."
```

---

### Task 5: `core-designsystem` — theme and accessible highlight tokens

Spec §12 treats accessibility as a requirement, not polish. The rule that highlighting never relies on hue alone is enforced here by a test, so a future contributor cannot quietly violate it.

**Files:**
- Create: `shared/core-designsystem/build.gradle.kts`
- Create: `shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/Color.kt`
- Create: `shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/HighlightTokens.kt`
- Create: `shared/core-designsystem/src/commonMain/kotlin/com/ptk/anatomypro/core/designsystem/Theme.kt`
- Test: `shared/core-designsystem/src/commonTest/kotlin/com/ptk/anatomypro/core/designsystem/HighlightTokensTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `HighlightStyle` from Task 3; the `anatomypro.kmp.compose` plugin from Task 1.
- Produces: `AnatomyTheme(darkTheme: Boolean, content: @Composable () -> Unit)`; `HighlightTokens.Selected`, `HighlightTokens.QuizTarget`, `HighlightTokens.Disputed` as `HighlightStyle` values; `relativeLuminance(argb: Int): Float`.

- [ ] **Step 1: Register the module**

Create `shared/core-designsystem/build.gradle.kts`:

```kotlin
plugins { id("anatomypro.kmp.compose") }

kotlin {
    android { namespace = "com.ptk.anatomypro.core.designsystem" }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:renderer-api"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

`libs.compose.ui` is needed for `androidx.compose.ui.graphics.Color`. If the module fails to configure with an unknown-plugin error, the Compose convention plugin did not apply — revisit Task 1 Step 4's fallback.

In `settings.gradle.kts`, add:

```kotlin
include(":shared:core-designsystem")
```

- [ ] **Step 2: Write the failing test**

`HighlightTokensTest.kt`. This is the accessibility rule as an executable assertion:

```kotlin
package com.ptk.anatomypro.core.designsystem

import com.ptk.anatomypro.renderer.api.HighlightStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class HighlightTokensTest {

    private val tokens = listOf(
        "Selected" to HighlightTokens.Selected,
        "QuizTarget" to HighlightTokens.QuizTarget,
        "Disputed" to HighlightTokens.Disputed,
    )

    @Test
    fun every_token_shifts_luminance_so_state_survives_colour_blindness() {
        for ((name, style) in tokens) {
            assertTrue(
                abs(style.fillLuminanceShift) >= 0.15f,
                "$name relies on hue alone; spec §12 requires a luminance shift too",
            )
        }
    }

    @Test
    fun tokens_are_distinguishable_from_each_other_without_hue() {
        for (i in tokens.indices) {
            for (j in i + 1 until tokens.size) {
                val (nameA, a) = tokens[i]
                val (nameB, b) = tokens[j]
                val luminanceDelta = abs(relativeLuminance(a.outlineArgb) - relativeLuminance(b.outlineArgb))
                val shiftDelta = abs(a.fillLuminanceShift - b.fillLuminanceShift)
                val widthDelta = abs(a.outlineWidthDp - b.outlineWidthDp)
                assertTrue(
                    luminanceDelta >= 0.10f || shiftDelta >= 0.15f || widthDelta >= 1f,
                    "$nameA and $nameB differ only in hue and would be identical to a " +
                        "colour-blind user; spec §12",
                )
            }
        }
    }

    @Test
    fun relative_luminance_is_zero_for_black_and_one_for_white() {
        assertTrue(relativeLuminance(0xFF000000.toInt()) < 0.01f)
        assertTrue(relativeLuminance(0xFFFFFFFF.toInt()) > 0.99f)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: FAIL — unresolved references `HighlightTokens` and `relativeLuminance`.

- [ ] **Step 4: Write the luminance helper and palette**

`Color.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG relative luminance of an ARGB colour, 0f (black) to 1f (white).
 *
 * Used to prove highlight states stay distinguishable without hue (spec §12).
 */
fun relativeLuminance(argb: Int): Float {
    fun channel(shift: Int): Float {
        val srgb = ((argb shr shift) and 0xFF) / 255f
        return if (srgb <= 0.03928f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}

internal val AnatomyBlue = Color(0xFF1E88E5)
internal val AnatomyAmber = Color(0xFFFFB300)
internal val AnatomyCrimson = Color(0xFFC62828)
internal val AnatomyBone = Color(0xFFF3EFE7)
internal val AnatomyCharcoal = Color(0xFF1C1B1F)
```

- [ ] **Step 5: Write the highlight tokens**

`HighlightTokens.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import com.ptk.anatomypro.renderer.api.HighlightStyle

/**
 * The concrete highlight values the renderer draws.
 *
 * Each token pairs an outline with a luminance shift. Spec §12 forbids conveying state by
 * hue alone, and `HighlightTokensTest` enforces that mechanically — if you add a token,
 * give it a luminance shift or the build fails.
 */
object HighlightTokens {

    /** The structure the user tapped. */
    val Selected = HighlightStyle(
        outlineArgb = AnatomyBlue.toArgbInt(),
        outlineWidthDp = 2f,
        fillLuminanceShift = 0.25f,
    )

    /** The structure a quiz is asking about. Brighter and thicker than [Selected]. */
    val QuizTarget = HighlightStyle(
        outlineArgb = AnatomyAmber.toArgbInt(),
        outlineWidthDp = 3.5f,
        fillLuminanceShift = 0.45f,
    )

    /** A structure whose naming is disputed. Darkened rather than brightened. */
    val Disputed = HighlightStyle(
        outlineArgb = AnatomyCrimson.toArgbInt(),
        outlineWidthDp = 2f,
        fillLuminanceShift = -0.30f,
    )
}
```

Add this helper to `Color.kt` so tokens can convert Compose colours to the plain `Int` the renderer boundary uses (the boundary stays free of Compose types on purpose):

```kotlin
internal fun Color.toArgbInt(): Int {
    fun component(value: Float): Int = (value * 255f + 0.5f).toInt() and 0xFF
    return (component(alpha) shl 24) or
        (component(red) shl 16) or
        (component(green) shl 8) or
        component(blue)
}
```

- [ ] **Step 6: Write the theme**

`Theme.kt`:

```kotlin
package com.ptk.anatomypro.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AnatomyBlue,
    secondary = AnatomyAmber,
    error = AnatomyCrimson,
    background = AnatomyBone,
)

private val DarkColors = darkColorScheme(
    primary = AnatomyBlue,
    secondary = AnatomyAmber,
    error = AnatomyCrimson,
    background = AnatomyCharcoal,
)

@Composable
fun AnatomyTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:core-designsystem:allTests`
Expected: PASS, 3 tests.

- [ ] **Step 8: Commit**

```bash
git add shared/core-designsystem settings.gradle.kts
git commit -m "feat(core-designsystem): add theme and accessible highlight tokens

Spec section 12 forbids conveying highlight state through hue alone.
HighlightTokensTest enforces that mechanically rather than by review:
a token added without a luminance shift, or indistinguishable from an
existing token to a colour-blind user, fails the build.

Tokens convert to plain ARGB ints at the boundary so renderer-api stays
free of Compose types and remains usable by a non-Compose renderer."
```

---

### Task 6: Umbrella rewiring and the Phase 0 harness

Turns `shared` into the umbrella that assembles `Shared.framework`, removes the wizard demo, and replaces `App.kt` with an explicitly throwaway harness.

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`
- Delete: `shared/src/commonMain/kotlin/com/ptk/anatomypro/Greeting.kt`
- Delete: `shared/src/commonMain/kotlin/com/ptk/anatomypro/GreetingUtil.kt`
- Delete: `shared/src/commonTest/kotlin/com/ptk/anatomypro/SharedCommonTest.kt`
- Delete: `shared/src/androidHostTest/kotlin/com/ptk/anatomypro/SharedLogicAndroidHostTest.kt`
- Delete: `shared/src/iosTest/kotlin/com/ptk/anatomypro/SharedLogicIOSTest.kt`
- Modify: `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` (tick §20.2 as delivered)

**Interfaces:**
- Consumes: all four modules from Tasks 2–5.
- Produces: `Shared.framework` exporting `core-model` and `renderer-api` to Swift; `App()` as the Phase 0 harness composable.

Keep `Platform.kt` and its two actuals — they are genuinely useful and unrelated to the demo.

- [ ] **Step 1: Rewire the umbrella build file**

In `shared/build.gradle.kts`, replace the `framework` block so it exports the modules Swift needs to see, and add the project dependencies. `export` requires the dependency be declared with `api`, not `implementation`:

```kotlin
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":shared:core-model"))
            export(project(":shared:renderer-api"))
        }
    }
```

Then in the `sourceSets` block, add to `commonMain.dependencies`:

```kotlin
            api(project(":shared:core-model"))
            api(project(":shared:renderer-api"))
            implementation(project(":shared:core-designsystem"))
            implementation(project(":shared:renderer-filament"))
```

- [ ] **Step 2: Delete the wizard demo**

```bash
git rm shared/src/commonMain/kotlin/com/ptk/anatomypro/Greeting.kt \
       shared/src/commonMain/kotlin/com/ptk/anatomypro/GreetingUtil.kt \
       shared/src/commonTest/kotlin/com/ptk/anatomypro/SharedCommonTest.kt \
       shared/src/androidHostTest/kotlin/com/ptk/anatomypro/SharedLogicAndroidHostTest.kt \
       shared/src/iosTest/kotlin/com/ptk/anatomypro/SharedLogicIOSTest.kt
```

- [ ] **Step 3: Replace `App.kt` with the harness**

`shared/src/commonMain/kotlin/com/ptk/anatomypro/App.kt`. It calls the stub factory and renders the failure honestly, so running the app on either platform reports exactly how far Phase 0 has got:

```kotlin
package com.ptk.anatomypro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ptk.anatomypro.core.designsystem.AnatomyTheme
import com.ptk.anatomypro.renderer.filament.createAnatomyRenderer

/**
 * Phase 0 harness. Throwaway.
 *
 * Its only job is to prove the module graph links and runs on both platforms. It is
 * replaced by feature-atlas in Phase 1 (spec §16).
 */
@Composable
fun App() {
    AnatomyTheme {
        val rendererStatus = remember {
            runCatching { createAnatomyRenderer() }
                .fold(
                    onSuccess = { "Renderer ready: ${it::class.simpleName}" },
                    onFailure = { "Renderer not implemented yet — this is Phase 0's job" },
                )
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Anatomy Pro", style = MaterialTheme.typography.headlineMedium)
                Text(getPlatform().name, style = MaterialTheme.typography.bodyMedium)
                Text(rendererStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 4: Run the whole test suite**

Run: `./gradlew check`
Expected: PASS. Fifteen test functions across the four modules, each executed once per target (Android host and both iOS targets), so the reported count is higher. No GPU is involved — this is spec §15's core claim, now demonstrable.

- [ ] **Step 5: Verify both platforms build**

Run: `./gradlew :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: `BUILD SUCCESSFUL` for both. The framework link is the real check that `export` and `api` are correctly paired.

- [ ] **Step 6: Mark the slice delivered in the spec**

In `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`, find the line immediately below the §20.2 module table that begins "Note that `core-model` ships identity types only". Insert this paragraph directly *above* that line:

```markdown
Delivered 2026-09-06. Phase 0 begins from here: the next change to this repository should
be a real `AnatomyRenderer` implementation, not another module.
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(shared): rewire umbrella and replace demo with Phase 0 harness

shared becomes the umbrella that assembles Shared.framework, exporting
core-model and renderer-api so Swift can see those types. The wizard's
Greeting demo and its three tests are removed; Platform stays.

App is an explicitly throwaway harness that calls the stub factory and
reports the failure, so running it on either platform shows exactly how
far Phase 0 has got. feature-atlas replaces it in Phase 1."
```

---

## Definition of done

- [ ] `./gradlew check` passes, running all fifteen test functions with no GPU.
- [ ] `./gradlew :androidApp:assembleDebug` and `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` both succeed.
- [ ] Android Studio syncs the project without error.
- [ ] Every module build file is four lines or fewer of configuration beyond its dependencies.
- [ ] Nothing depends on `renderer-filament` except `shared`.
- [ ] `git log` shows six commits, none carrying attribution trailers.

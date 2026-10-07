# Solid Outline Highlighting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw a solid outline, in the style's colour and width, around every highlighted structure on Android and iOS, so that no state is told apart by hue alone (design §12).

**Architecture:** The highlighted entities of each outline group are added to a second, small scene and rendered into a half-resolution off-screen mask. After the main view, an overlay view draws one full-screen triangle per group with a material of our own, which colours every pixel that is outside the mask but within the outline width of it. The material is compiled by a new build stage from a pinned `matc` and reaches both renderers as generated Kotlin.

**Tech Stack:** Kotlin Multiplatform, Filament 1.75.1 (Java bindings on Android, Objective-C++ behind a C seam on iOS), `matc` from the Filament 1.75.1 macOS release, Gradle custom tasks, kotlinx.cinterop, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-10-07-outline-highlighting-design.md`. Design spec `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` §4 (the renderer boundary), §12, §26.4 (one owner for the material slot), §26.7, §33.

## Global Constraints

- **Repository root is `~/StudioProjects/AnatomyPro`**, branch `feature/outline-highlighting`. Never edit `~/Projekty/anatomy pro`.
- **Tests run on two targets.** `./gradlew :shared:<module>:allTests` runs the JVM host and the iOS simulator. A test that passes on one and not the other is a failure.
- **The Android renderer contract is instrumented.** Start the emulator (`emulator -avd Medium_Phone_API_36.1`), then `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`.
- **Do not run `allTests` while an app is running on a simulator.** The iOS GPU contract test `reports_a_pick` times out under that contention.
- `./gradlew --stop` between long sessions. This machine runs out of memory.
- **No `Co-Authored-By` or `Claude-Session` trailers on commits.**
- **One Filament version.** `matc`, the Android artifacts and the iOS SDK all come from `filament` in `gradle/libs.versions.toml` (1.75.1). A compiled material only loads in the version that made it.
- **The renderer is told, never asked** (design §4). `AnatomyRenderer` does not change in this plan.
- **One owner for the material slot** (design §26.4). Outlines never call `setMaterialInstanceAt` on a pack's entities; the mask draws them with the materials they already hold.
- **Solid only.** `HighlightStyle.outlineStyle` is not read; `DASHED` draws solid.
- **The outline shows through** whatever is in front of the structure.
- **The fill is unchanged.** `HighlightPaint` and the tint are not touched.
- **The outline colour is written unconverted**: the four floats are `argb / 255` and are set with the plain `setParameter`, never `RgbaType::sRGB`. The contract matches frame bytes against the style's bytes.
- **Out of scope:** dashed outlines; screen 12's "unfinished" marker; task 5D17 (colour spaces); `fillArgb`; measurement on hardware (task CF82).

## How this plan differs from the spec

Three points changed while the code was read. The spec is amended in Task 1, Step 1 so the two agree.

1. **No throwaway spike.** A probe that answers the spec's four questions is the same two hundred lines as the Android `OutlinePass`. Task 4 is therefore the gate: it is built test-first, and its "What a failure means" table says which assumption a failing test has disproved and when to stop for the owner.
2. **The material travels as generated Kotlin, on both platforms.** `FilamentAnatomyRenderer` on Android is constructed without a `Context`, so it cannot open an asset. One generated `OutlineMaterialData` in `commonMain` serves both; iOS hands the bytes across the seam.
3. **`ar_attach_layer` does not change.** Widths cross the seam already in pixels, so the density stays in Kotlin: `attachLayer` takes it, the shim never sees it.

## Review Focus

1. **The surface changes size while something is highlighted** (rotation, split screen): the outline is still drawn, at the new size. — Tasks 4 and 5, `keeps_the_outline_when_the_surface_changes_size`.
2. **The highlight is restyled or moved repeatedly** (tap, tap, tap): the outline follows and the earlier colour leaves no pixel behind. — Task 3, in `verifyAHighlightDrawsAnOutlineInItsColour`.
3. **A highlighted structure is hidden**: it has no outline; shown again, it has one. — Task 3, `verifyAHiddenStructureHasNoOutline`.
4. **The pack is unloaded while something is highlighted**: nothing faults and no outline is left on screen. — Task 3, `verifyUnloadingAPackRemovesItsOutline`.
5. **A density of zero, a negative or non-finite density, or an absurd width**: the outline is drawn at a sane width, between 1 and 32 pixels. — Task 2, `OutlinePlanTest`.

## File Structure

| File | Responsibility |
|---|---|
| `renderer-materials/build.gradle.kts` | Create. Fetch `matc`, compile `outline.mat`, generate `OutlineMaterialData.kt`. |
| `renderer-materials/src/outline.mat` | Create. The outline material. |
| `settings.gradle.kts`, `gradle/libs.versions.toml` | Modify. The new project; the macOS release checksum. |
| `shared/renderer-filament/build.gradle.kts` | Modify. Compile the generated source into `commonMain`. |
| `shared/renderer-filament/src/commonMain/.../OutlinePlan.kt` | Create. A highlight map as outline groups, in pixels. |
| `shared/renderer-filament/src/commonMain/.../FramePixels.kt` | Create. Counting the pixels of a colour in a frame. |
| `shared/renderer-filament/src/commonTest/.../OutlineMaterialDataTest.kt`, `OutlinePlanTest.kt`, `FramePixelsTest.kt` | Create. |
| `shared/renderer-api/.../AnatomyRendererContract.kt` | Modify. The `countPixels` hook and four outline cases. |
| `shared/renderer-api/.../FakeAnatomyRenderer.kt` | Modify. `outlinePixels`. |
| `shared/renderer-api/src/commonTest/.../FakeAnatomyRendererTest.kt` | Modify. |
| `shared/renderer-filament/src/androidMain/.../OutlinePass.android.kt` | Create. The outline's Filament objects on Android. |
| `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt` | Modify. Density, wiring, `captureFrame`. |
| `shared/renderer-filament/src/androidDeviceTest/.../FilamentAnatomyRendererContractTest.kt` | Modify. |
| `ios-renderer/src/OutlinePass.h`, `ios-renderer/src/OutlinePass.mm` | Create. The same, in the shim. |
| `ios-renderer/include/anatomy_renderer.h`, `ios-renderer/src/AnatomyRenderer.mm`, `ios-renderer/CMakeLists.txt` | Modify. The seam and the wiring. |
| `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt` | Modify. |
| `shared/renderer-filament/src/iosTest/.../FilamentAnatomyRendererContractTest.kt` | Modify. |
| `shared/src/androidMain/.../AnatomyCanvas.android.kt`, `shared/src/iosMain/.../AnatomyCanvas.ios.kt` | Modify. Pass the density. |
| `docs/state-of-play.md`, both specs | Modify. |

Kotlin paths abbreviate `kotlin/com/ptk/anatomypro/renderer/filament/`, `kotlin/com/ptk/anatomypro/renderer/api/` and `kotlin/com/ptk/anatomypro/` as `.../`.

---

### Task 1: The material, and the build stage that compiles it

**Files:**
- Modify: `docs/superpowers/specs/2026-10-07-outline-highlighting-design.md`
- Create: `renderer-materials/build.gradle.kts`, `renderer-materials/src/outline.mat`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `shared/renderer-filament/build.gradle.kts`
- Test: `shared/renderer-filament/src/commonTest/.../OutlineMaterialDataTest.kt`

**Interfaces:**
- Produces: `internal object OutlineMaterialData { val bytes: ByteArray }` in package `com.ptk.anatomypro.renderer.filament`, visible to every source set of `:shared:renderer-filament`. The material has three parameters: `mask` (sampler2d), `color` (float4, straight alpha, unconverted) and `reach` (float2, the outline width as a fraction of the surface per axis).

- [ ] **Step 1: Amend the spec**

In `docs/superpowers/specs/2026-10-07-outline-highlighting-design.md`:

Replace §4.3's body with:

```markdown
`ar_add_highlight` gains the outline colour as four floats and a width in pixels. The shim
merges groups with the same outline into one mask. `ar_set_outline_material` hands the
compiled material to the shim once, after `ar_create`. `ar_capture_frame` exists for tests:
it renders a frame and copies its pixels out. `ar_attach_layer` does not change: widths
cross the seam in pixels, so the density stays in Kotlin. No callback is added.
```

Replace §4.4's body with:

```markdown
`attachSurface` on Android and `attachLayer` on iOS (the Kotlin function, not the seam) take
pixels-per-dp from the host (`resources.displayMetrics.density`; the screen's scale).
Headless attachment uses 1.
```

In §4.5 replace the two bullets beginning "**Android** takes it" and "**iOS** takes it" with:

```markdown
- **Generate.** A task writes `OutlineMaterialData.kt`, the compiled material as base64 in
  a Kotlin object, into a generated source directory that `:shared:renderer-filament`
  compiles into `commonMain`. Both platforms read the same bytes. An Android asset would
  need a `Context` the renderer is not constructed with, and the iOS simulator tests have
  no app bundle to read a resource from.
```

In §3, at the end of the paragraph beginning "**Per group, a mask.**", add:

```markdown
The renderer's clear colour has alpha 0 so that a mask starts transparent; the swap chain
is opaque on both platforms, so the screen is unaffected.
```

Replace §7's item 1 with:

```markdown
1. **The gate is the Android pass**, not a throwaway spike: a probe that answers the four
   questions is the same code as the pass. It is built test-first, and the plan says which
   assumption each failing test disproves. If an entity cannot be in two scenes, work stops
   and the technique is reopened with the owner before anything else is built.
```

and renumber nothing else; items 2 to 8 stand.

- [ ] **Step 2: Write the failing test**

`shared/renderer-filament/src/commonTest/kotlin/com/ptk/anatomypro/renderer/filament/OutlineMaterialDataTest.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlineMaterialDataTest {

    @Test
    fun the_compiled_material_is_a_filament_package() {
        val bytes = OutlineMaterialData.bytes

        // Every .filamat begins with this chunk tag.
        assertEquals("SREV_TAM", bytes.copyOfRange(0, 8).decodeToString())
        // Three backends' shaders: tens of kilobytes. A stub would be a few hundred bytes.
        assertTrue(bytes.size > 10_000, "only ${bytes.size} bytes")
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :shared:renderer-filament:testAndroidHostTest`
Expected: FAIL to compile, `Unresolved reference 'OutlineMaterialData'`.

- [ ] **Step 4: Pin the macOS release**

In `gradle/libs.versions.toml`, under `filamentIosSha256`, add:

```toml
# The macOS release carries the host tools; matc compiles our materials. Same version as
# the runtimes: a compiled material only loads in the Filament that made it.
filamentMacSha256 = "703afaa7068af98a47b505f02a35ed30aae83bbcbfd6ca138d0b62fa1e38da88"
```

In `settings.gradle.kts`, after `include(":ios-renderer")`, add:

```kotlin
include(":renderer-materials")
```

- [ ] **Step 5: Write the material**

`renderer-materials/src/outline.mat`:

```
// The outline of whatever a mask holds, drawn outside it.
//
// Drawn on a full-screen triangle over the main view. For each pixel it reads the mask at
// the pixel and on two rings around it; a pixel outside the mask whose rings touch the mask
// gets the outline colour. Two rings because one, at the full width, steps over a feature
// thinner than the outline.
material {
    name : outline,
    shadingModel : unlit,
    blending : transparent,
    vertexDomain : device,
    depthWrite : false,
    depthCulling : false,
    culling : none,
    parameters : [
        { type : sampler2d, name : mask },
        // Straight alpha, and written unconverted so that a frame holds these bytes.
        { type : float4, name : color },
        // The outline width as a fraction of the surface, per axis.
        { type : float2, name : reach }
    ]
}

fragment {
    const int TAPS = 12;
    const vec2 RING[12] = vec2[12](
        vec2( 1.000,  0.000), vec2( 0.866,  0.500), vec2( 0.500,  0.866),
        vec2( 0.000,  1.000), vec2(-0.500,  0.866), vec2(-0.866,  0.500),
        vec2(-1.000,  0.000), vec2(-0.866, -0.500), vec2(-0.500, -0.866),
        vec2( 0.000, -1.000), vec2( 0.500, -0.866), vec2( 0.866, -0.500)
    );

    void material(inout MaterialInputs material) {
        prepareMaterial(material);
        vec2 uv = uvToRenderTargetUV(getNormalizedViewportCoord().xy);
        float inside = texture(materialParams_mask, uv).a;
        float near = 0.0;
        for (int i = 0; i < TAPS; i++) {
            vec2 step = RING[i] * materialParams.reach;
            near = max(near, texture(materialParams_mask, uv + step).a);
            near = max(near, texture(materialParams_mask, uv + step * 0.5).a);
        }
        float alpha = smoothstep(0.05, 0.5, near) * (1.0 - smoothstep(0.05, 0.5, inside))
            * materialParams.color.a;
        material.baseColor = vec4(materialParams.color.rgb * alpha, alpha);
    }
}
```

This source compiles with `matc` 1.75.1 for all three backends (checked while planning).

- [ ] **Step 6: Write the build stage**

`renderer-materials/build.gradle.kts`:

```kotlin
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

/**
 * Compiles Filament materials of our own, and hands them to the renderers as Kotlin.
 *
 * Filament bakes a material's shaders at build time with `matc`, a host tool the iOS binary
 * release does not carry. This project fetches the macOS release for the pinned version,
 * compiles each `.mat`, and writes the result as a Kotlin object that
 * `:shared:renderer-filament` compiles into commonMain — one artifact for both platforms.
 * It applies no Kotlin plugin and produces no JVM artifacts.
 */

val pinnedFilamentVersion = libs.versions.filament.get()
val filamentMacSha256 = libs.versions.filamentMacSha256.get()

/** Downloads the pinned macOS release and unpacks `matc` from it. */
abstract class FetchMatc : DefaultTask() {

    @get:Input abstract val version: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val toolsDirectory: DirectoryProperty
    @get:Internal abstract val archive: RegularFileProperty

    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun fetch() {
        check(System.getProperty("os.name").startsWith("Mac")) {
            "matc is fetched from the macOS release; building materials elsewhere needs that " +
                "platform's tools archive and a checksum of its own."
        }
        val tgz = archive.get().asFile
        if (!tgz.isFile || digestOf(tgz) != sha256.get()) {
            tgz.parentFile.mkdirs()
            val url = "https://github.com/google/filament/releases/download/" +
                "v${version.get()}/filament-v${version.get()}-mac.tgz"
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input ->
                tgz.outputStream().use(input::copyTo)
            }
            val actual = digestOf(tgz)
            check(actual == sha256.get()) {
                "Filament ${version.get()} macOS checksum mismatch.\n" +
                    "  expected ${sha256.get()}\n" +
                    "  actual   $actual"
            }
        }

        val destination = toolsDirectory.get().asFile
        destination.deleteRecursively()
        fs.copy {
            from(archives.tarTree(archives.gzip(tgz))) { include("filament/bin/matc") }
            eachFile { relativePath = RelativePath(true, name) }
            includeEmptyDirs = false
            into(destination)
        }
        File(destination, "matc").setExecutable(true)
    }

    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Runs `matc` on one material for the mobile platform and every backend we ship on. */
abstract class CompileMaterial : DefaultTask() {

    @get:InputFile abstract val source: RegularFileProperty
    @get:InputDirectory abstract val toolsDirectory: DirectoryProperty
    @get:OutputFile abstract val output: RegularFileProperty

    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun compile() {
        output.get().asFile.parentFile.mkdirs()
        exec.exec {
            commandLine(
                File(toolsDirectory.get().asFile, "matc").absolutePath,
                "--platform", "mobile",
                // OpenGL ES and Vulkan for Android, Metal for iOS.
                "--api", "opengl", "--api", "vulkan", "--api", "metal",
                "-o", output.get().asFile.absolutePath,
                source.get().asFile.absolutePath,
            )
        }
    }
}

/** Writes a compiled material as a Kotlin object holding it in base64. */
abstract class GenerateMaterialSource : DefaultTask() {

    @get:InputFile abstract val material: RegularFileProperty
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val objectName: Property<String>
    @get:OutputDirectory abstract val sourceDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        // A JVM string constant holds at most 65,535 bytes, so the text is cut into pieces.
        val chunks = Base64.getEncoder().encodeToString(material.get().asFile.readBytes()).chunked(16_000)
        val directory = File(sourceDirectory.get().asFile, packageName.get().replace('.', '/'))
        directory.deleteRecursively()
        directory.mkdirs()
        File(directory, "${objectName.get()}.kt").writeText(
            buildString {
                appendLine("package ${packageName.get()}")
                appendLine()
                appendLine("import kotlin.io.encoding.Base64")
                appendLine()
                appendLine("/** Generated by :renderer-materials. Do not edit. */")
                appendLine("internal object ${objectName.get()} {")
                appendLine("    val bytes: ByteArray by lazy { Base64.decode(CHUNKS.joinToString(\"\")) }")
                appendLine()
                appendLine("    private val CHUNKS = listOf(")
                chunks.forEach { appendLine("        \"$it\",") }
                appendLine("    )")
                appendLine("}")
            }
        )
    }
}

val fetchMatc by tasks.registering(FetchMatc::class) {
    description = "Downloads and verifies the pinned Filament macOS release, for matc."
    version.set(pinnedFilamentVersion)
    sha256.set(filamentMacSha256)
    archive.set(layout.buildDirectory.file("filament-download/filament-v$pinnedFilamentVersion-mac.tgz"))
    toolsDirectory.set(layout.buildDirectory.dir("tools"))
}

val compileOutline by tasks.registering(CompileMaterial::class) {
    description = "Compiles outline.mat for OpenGL ES, Vulkan and Metal."
    source.set(layout.projectDirectory.file("src/outline.mat"))
    toolsDirectory.set(fetchMatc.flatMap { it.toolsDirectory })
    output.set(layout.buildDirectory.file("materials/outline.filamat"))
}

tasks.register<GenerateMaterialSource>("generateMaterialSource") {
    description = "Writes the compiled outline material as Kotlin for :shared:renderer-filament."
    material.set(compileOutline.flatMap { it.output })
    packageName.set("com.ptk.anatomypro.renderer.filament")
    objectName.set("OutlineMaterialData")
    sourceDirectory.set(layout.buildDirectory.dir("generated/kotlin"))
}
```

If the Kotlin compiler reports that `Base64` needs opt-in, add `appendLine("import kotlin.io.encoding.ExperimentalEncodingApi")` and `appendLine("    @OptIn(ExperimentalEncodingApi::class)")` above the `val bytes` line.

- [ ] **Step 7: Compile the generated source into the renderer module**

In `shared/renderer-filament/build.gradle.kts`, inside `sourceSets { … }`, replace

```kotlin
        commonMain.dependencies { api(project(":shared:renderer-api")) }
```

with

```kotlin
        commonMain {
            // The outline material, compiled by :renderer-materials and written as Kotlin.
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("renderer-materials/build/generated/kotlin"))
            dependencies { api(project(":shared:renderer-api")) }
        }
```

and at the end of the file add:

```kotlin
// The generated material source is an input of every compilation of this module. As with
// the staged native libraries above, the producing task lives in another project, so the
// dependency is declared by path.
tasks.matching { it.name.startsWith("compile") || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(":renderer-materials:generateMaterialSource")
}
```

- [ ] **Step 8: Run the test on both targets**

Run: `./gradlew :shared:renderer-filament:testAndroidHostTest :shared:renderer-filament:iosSimulatorArm64Test --tests '*OutlineMaterialDataTest*'`
Expected: PASS on both. The first run logs `Downloading https://github.com/google/filament/releases/download/v1.75.1/filament-v1.75.1-mac.tgz` (46 MB).

If Gradle fails with "uses this output of task ':renderer-materials:generateMaterialSource' without declaring an explicit or implicit dependency", it names the consuming task; widen the `tasks.matching` predicate in Step 7 to cover that task's name and run again.

- [ ] **Step 9: Prove the stage is not stale**

Run: `./gradlew :renderer-materials:generateMaterialSource` twice.
Expected: the second run reports the three tasks `UP-TO-DATE`. Then change `0.05` to `0.06` in `outline.mat` once, run again, and see `compileOutline` and `generateMaterialSource` execute; change it back.

- [ ] **Step 10: Commit**

```bash
git add renderer-materials settings.gradle.kts gradle/libs.versions.toml shared/renderer-filament/build.gradle.kts shared/renderer-filament/src/commonTest docs/superpowers/specs/2026-10-07-outline-highlighting-design.md
git commit -m "feat(build): a pinned matc compiles the outline material, and the renderers get it as Kotlin"
```

---

### Task 2: The shared rules — outline groups, and counting a colour in a frame

**Files:**
- Create: `shared/renderer-filament/src/commonMain/.../OutlinePlan.kt`, `shared/renderer-filament/src/commonMain/.../FramePixels.kt`
- Test: `shared/renderer-filament/src/commonTest/.../OutlinePlanTest.kt`, `shared/renderer-filament/src/commonTest/.../FramePixelsTest.kt`

**Interfaces:**
- Produces:
  - `data class OutlineGroup(val structures: Set<StructureId>, val red: Float, val green: Float, val blue: Float, val alpha: Float, val widthPx: Float)`
  - `object OutlinePlan { const val MIN_WIDTH_PX = 1f; const val MAX_WIDTH_PX = 32f; fun of(styles: Map<StructureId, HighlightStyle>, pixelsPerDp: Float): List<OutlineGroup> }`
  - `object FramePixels { fun countMatching(rgba: ByteArray, argb: Int, tolerance: Int = 2): Int }`

- [ ] **Step 1: Write the failing tests**

`OutlinePlanTest.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.OutlineStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlinePlanTest {

    private val rib = StructureId("rib")
    private val sternum = StructureId("sternum")
    private val clavicle = StructureId("clavicle")

    private fun style(
        argb: Long = 0xFFFF00FF,
        widthDp: Float = 2f,
        fill: Long = 0xFF000000,
        shift: Float = 0f,
        outline: OutlineStyle = OutlineStyle.SOLID,
    ) = HighlightStyle(argb.toInt(), widthDp, outline, fill.toInt(), shift)

    @Test
    fun nothing_highlighted_is_no_groups() {
        assertTrue(OutlinePlan.of(emptyMap(), pixelsPerDp = 2f).isEmpty())
    }

    @Test
    fun structures_with_the_same_outline_share_a_group() {
        val groups = OutlinePlan.of(mapOf(rib to style(), sternum to style()), pixelsPerDp = 1f)

        assertEquals(listOf(setOf(rib, sternum)), groups.map { it.structures })
    }

    @Test
    fun a_different_fill_or_dash_does_not_split_a_group() {
        val groups = OutlinePlan.of(
            mapOf(
                rib to style(fill = 0xFF112233, shift = 0.25f),
                sternum to style(fill = 0xFF445566, shift = -0.2f, outline = OutlineStyle.DASHED),
            ),
            pixelsPerDp = 1f,
        )

        assertEquals(1, groups.size)
    }

    @Test
    fun a_different_colour_or_width_is_a_group_of_its_own_in_the_order_first_seen() {
        val groups = OutlinePlan.of(
            mapOf(
                rib to style(argb = 0xFFFF00FF, widthDp = 2f),
                sternum to style(argb = 0xFF00FFFF, widthDp = 2f),
                clavicle to style(argb = 0xFFFF00FF, widthDp = 3f),
            ),
            pixelsPerDp = 1f,
        )

        assertEquals(listOf(setOf(rib), setOf(sternum), setOf(clavicle)), groups.map { it.structures })
    }

    @Test
    fun the_colour_is_the_styles_bytes_as_fractions_unconverted() {
        val group = OutlinePlan.of(mapOf(rib to style(argb = 0x80FF0033)), pixelsPerDp = 1f).single()

        assertEquals(1f, group.red)
        assertEquals(0f, group.green)
        assertEquals(0x33 / 255f, group.blue)
        assertEquals(0x80 / 255f, group.alpha)
    }

    @Test
    fun the_width_is_dp_times_the_density() {
        val group = OutlinePlan.of(mapOf(rib to style(widthDp = 2f)), pixelsPerDp = 2.625f).single()

        assertEquals(5.25f, group.widthPx)
    }

    @Test
    fun a_density_that_is_not_a_positive_number_counts_as_one() {
        for (density in listOf(0f, -3f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val group = OutlinePlan.of(mapOf(rib to style(widthDp = 4f)), pixelsPerDp = density).single()

            assertEquals(4f, group.widthPx, "density $density")
        }
    }

    @Test
    fun the_width_stays_between_one_and_thirty_two_pixels() {
        val thin = OutlinePlan.of(mapOf(rib to style(widthDp = 0.1f)), pixelsPerDp = 1f).single()
        val huge = OutlinePlan.of(mapOf(rib to style(widthDp = 500f)), pixelsPerDp = 3f).single()

        assertEquals(OutlinePlan.MIN_WIDTH_PX, thin.widthPx)
        assertEquals(OutlinePlan.MAX_WIDTH_PX, huge.widthPx)
    }
}
```

`FramePixelsTest.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.test.Test
import kotlin.test.assertEquals

class FramePixelsTest {

    private fun frame(vararg rgba: Int) = ByteArray(rgba.size) { rgba[it].toByte() }

    @Test
    fun counts_pixels_of_the_colour_whatever_their_alpha() {
        val pixels = frame(
            255, 0, 255, 255,
            255, 0, 255, 0,
            0, 0, 0, 255,
        )

        assertEquals(2, FramePixels.countMatching(pixels, argb = 0xFFFF00FF.toInt()))
    }

    @Test
    fun allows_each_channel_to_be_off_by_the_tolerance_and_no_more() {
        val pixels = frame(
            253, 2, 254, 255,
            252, 0, 255, 255,
        )

        assertEquals(1, FramePixels.countMatching(pixels, argb = 0xFFFF00FF.toInt(), tolerance = 2))
    }

    @Test
    fun an_empty_frame_has_none_and_a_ragged_tail_is_ignored() {
        assertEquals(0, FramePixels.countMatching(ByteArray(0), argb = 0xFFFF00FF.toInt()))
        assertEquals(1, FramePixels.countMatching(frame(255, 0, 255, 255, 255, 0), argb = 0xFFFF00FF.toInt()))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :shared:renderer-filament:testAndroidHostTest`
Expected: FAIL to compile, `Unresolved reference 'OutlinePlan'` and `'FramePixels'`.

- [ ] **Step 3: Write the rules**

`OutlinePlan.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.HighlightStyle

/**
 * Structures outlined alike: one mask, one colour, one width.
 *
 * The colour is the style's bytes as fractions, unconverted. The outline is written to the
 * frame without tone mapping, so these are the bytes the frame ends up holding.
 */
data class OutlineGroup(
    val structures: Set<StructureId>,
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val widthPx: Float,
)

/**
 * A highlight map as the outlines to draw.
 *
 * In shared code, like [HighlightPaint], so both renderers are handed the same groups and
 * the same numbers. Styles that share an outline colour and width share a group, whatever
 * their fill: a group is a mask, and a mask costs a render target.
 */
object OutlinePlan {

    const val MIN_WIDTH_PX = 1f

    /** The material samples two rings; far beyond this a ring steps over what it should find. */
    const val MAX_WIDTH_PX = 32f

    fun of(styles: Map<StructureId, HighlightStyle>, pixelsPerDp: Float): List<OutlineGroup> {
        // A host that cannot say its density must not make the outline vanish or fill the screen.
        val density = if (pixelsPerDp.isFinite() && pixelsPerDp > 0f) pixelsPerDp else 1f
        return styles.entries
            .groupBy({ it.value.outlineArgb to it.value.outlineWidthDp }, { it.key })
            .map { (outline, structures) ->
                val (argb, widthDp) = outline
                OutlineGroup(
                    structures = structures.toSet(),
                    red = ((argb shr 16) and 0xFF) / 255f,
                    green = ((argb shr 8) and 0xFF) / 255f,
                    blue = (argb and 0xFF) / 255f,
                    alpha = ((argb ushr 24) and 0xFF) / 255f,
                    widthPx = (widthDp * density).coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX),
                )
            }
    }
}
```

`FramePixels.kt`:

```kotlin
package com.ptk.anatomypro.renderer.filament

import kotlin.math.abs

/** Reading a frame the renderer handed back, for tests. Four bytes a pixel: R, G, B, A. */
object FramePixels {

    /**
     * How many pixels are [argb]'s colour, each channel within [tolerance].
     *
     * Alpha is not compared: what a swap chain keeps in it differs by platform, and the
     * question a test asks is what colour was drawn.
     */
    fun countMatching(rgba: ByteArray, argb: Int, tolerance: Int = 2): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        var count = 0
        var index = 0
        while (index + 3 < rgba.size) {
            if (
                abs((rgba[index].toInt() and 0xFF) - red) <= tolerance &&
                abs((rgba[index + 1].toInt() and 0xFF) - green) <= tolerance &&
                abs((rgba[index + 2].toInt() and 0xFF) - blue) <= tolerance
            ) {
                count++
            }
            index += 4
        }
        return count
    }
}
```

- [ ] **Step 4: Run the tests on both targets**

Run: `./gradlew :shared:renderer-filament:testAndroidHostTest :shared:renderer-filament:iosSimulatorArm64Test --tests '*OutlinePlanTest*' --tests '*FramePixelsTest*'`
Expected: PASS, 11 tests on each target.

- [ ] **Step 5: Commit**

```bash
git add shared/renderer-filament/src/commonMain shared/renderer-filament/src/commonTest
git commit -m "feat(renderer): a highlight map as outline groups in pixels, worked out once"
```

---

### Task 3: The contract sees pixels, and the fake answers

**Files:**
- Modify: `shared/renderer-api/src/commonMain/.../AnatomyRendererContract.kt`
- Modify: `shared/renderer-api/src/commonMain/.../FakeAnatomyRenderer.kt`
- Test: `shared/renderer-api/src/commonTest/.../FakeAnatomyRendererTest.kt`

**Interfaces:**
- Produces, on `AnatomyRendererContract`:
  - `protected open suspend fun countPixels(renderer: AnatomyRenderer, argb: Int): Int` — the real renderers override it in Tasks 4 and 5.
  - `suspend fun verifyAHighlightDrawsAnOutlineInItsColour()`
  - `suspend fun verifyAWiderOutlineCoversMorePixels()`
  - `suspend fun verifyAHiddenStructureHasNoOutline()`
  - `suspend fun verifyUnloadingAPackRemovesItsOutline()`
- Produces, on `FakeAnatomyRenderer`: `fun outlinePixels(argb: Int): Int`.

- [ ] **Step 1: Write the failing tests**

In `FakeAnatomyRendererTest.kt`, inside `class FakeAnatomyRendererContractTest`, after `override suspend fun pickCentre`, add:

```kotlin
    override suspend fun countPixels(renderer: AnatomyRenderer, argb: Int): Int =
        (renderer as FakeAnatomyRenderer).outlinePixels(argb)
```

and after the last `@Test` line of that class add:

```kotlin
    @Test fun draws_an_outline_in_the_styles_colour() = runTest { verifyAHighlightDrawsAnOutlineInItsColour() }
    @Test fun draws_a_wider_outline_over_more_pixels() = runTest { verifyAWiderOutlineCoversMorePixels() }
    @Test fun draws_no_outline_for_a_hidden_structure() = runTest { verifyAHiddenStructureHasNoOutline() }
    @Test fun removes_the_outline_with_the_pack() = runTest { verifyUnloadingAPackRemovesItsOutline() }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :shared:renderer-api:testAndroidHostTest`
Expected: FAIL to compile, `Unresolved reference 'outlinePixels'` and `'verifyAHighlightDrawsAnOutlineInItsColour'`.

- [ ] **Step 3: Give the fake an answer**

In `FakeAnatomyRenderer.kt`, after `emitPick`, add:

```kotlin
    /**
     * Test hook: how much outline of colour [argb] a real renderer would have on screen.
     *
     * Not a pixel count — the fake draws nothing — but it moves the way one does: zero with
     * no pack, zero for a hidden structure, more for a wider outline, and only for the
     * colour asked about. That is all the contract's outline cases compare.
     */
    fun outlinePixels(argb: Int): Int {
        if (loadedPacks.isEmpty()) return 0
        return highlights
            .filter { (structure, style) -> structure !in hidden && style.outlineArgb == argb }
            .values
            .sumOf { (it.outlineWidthDp * 100).toInt() }
    }
```

- [ ] **Step 4: Write the contract cases**

In `AnatomyRendererContract.kt`, after `protected open suspend fun settle`, add:

```kotlin
    /**
     * How many pixels of the last drawn frame are [argb]'s colour.
     *
     * A real renderer reads its frame back; the fake answers from what it recorded. The
     * default fails, so a renderer that has not been taught to look cannot pass an outline
     * case by accident.
     */
    protected open suspend fun countPixels(renderer: AnatomyRenderer, argb: Int): Int =
        throw AssertionError("this renderer cannot report what it drew")
```

After `verifyDoesNotFaultWhenHighlightAndGhostInterleave`, add:

```kotlin
    suspend fun verifyAHighlightDrawsAnOutlineInItsColour() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)
        assertEquals(0, countPixels(renderer, OUTLINE_ARGB), "outline-coloured pixels before any highlight")

        renderer.highlight(setOf(hitStructure), OUTLINE_THIN)
        settle(renderer)
        assertPositive(countPixels(renderer, OUTLINE_ARGB), "pixels of the outline colour around a highlighted structure")

        // Restyled: the outline follows, and the first colour leaves nothing behind.
        renderer.highlight(setOf(hitStructure), OUTLINE_OTHER)
        settle(renderer)
        assertEquals(0, countPixels(renderer, OUTLINE_ARGB), "pixels of the first colour after a restyle")
        assertPositive(countPixels(renderer, OTHER_OUTLINE_ARGB), "pixels of the second colour after a restyle")

        renderer.highlight(emptyMap())
        settle(renderer)
        assertEquals(0, countPixels(renderer, OTHER_OUTLINE_ARGB), "outline-coloured pixels after the highlight was cleared")
    }

    suspend fun verifyAWiderOutlineCoversMorePixels() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        // The fill does not change with the width, so whatever grows is the outline.
        renderer.highlight(setOf(hitStructure), OUTLINE_THIN)
        settle(renderer)
        val thin = countPixels(renderer, OUTLINE_ARGB)
        renderer.highlight(setOf(hitStructure), OUTLINE_WIDE)
        settle(renderer)
        val wide = countPixels(renderer, OUTLINE_ARGB)

        if (wide <= thin) {
            throw AssertionError("a wider outline should cover more pixels: thin <$thin>, wide <$wide>")
        }
    }

    suspend fun verifyAHiddenStructureHasNoOutline() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)

        renderer.highlight(setOf(hitStructure), OUTLINE_THIN)
        renderer.setVisibility(setOf(hitStructure), visible = false)
        settle(renderer)
        assertEquals(0, countPixels(renderer, OUTLINE_ARGB), "outline-coloured pixels around a hidden structure")

        renderer.setVisibility(setOf(hitStructure), visible = true)
        settle(renderer)
        assertPositive(countPixels(renderer, OUTLINE_ARGB), "pixels of the outline colour once it is shown again")
    }

    suspend fun verifyUnloadingAPackRemovesItsOutline() = withRenderer { renderer ->
        renderer.loadPack(pack, source)
        settle(renderer)
        renderer.highlight(setOf(hitStructure), OUTLINE_THIN)
        settle(renderer)

        renderer.unloadPack(pack)
        settle(renderer)

        assertEquals(0, countPixels(renderer, OUTLINE_ARGB), "outline-coloured pixels after the pack was unloaded")
        val error = awaitEventOrNull(renderer) { it is RendererEvent.Error }
        assertEquals(null, error, "an error was reported unloading a pack with a highlight")
    }
```

After the private `assertEquals` helper, add:

```kotlin
    private fun assertPositive(count: Int, what: String) {
        if (count <= 0) throw AssertionError("$what: expected some but there were <$count>")
    }
```

In the `companion object`, after `THIRD_HIGHLIGHT`, add:

```kotlin
        /** Pure magenta: no lit surface in a fixture comes out as exactly this. */
        const val OUTLINE_ARGB = 0xFFFF00FF.toInt()
        const val OTHER_OUTLINE_ARGB = 0xFF00FFFF.toInt()

        /**
         * The fill is darkened by half, so the tinted surface itself — which takes its colour
         * from the outline colour — never reaches the outline's own bytes.
         */
        val OUTLINE_THIN = HighlightStyle(
            outlineArgb = OUTLINE_ARGB,
            outlineWidthDp = 4f,
            outlineStyle = OutlineStyle.SOLID,
            fillArgb = OUTLINE_ARGB,
            fillLuminanceShift = -0.5f,
        )
        val OUTLINE_WIDE = OUTLINE_THIN.copy(outlineWidthDp = 12f)
        val OUTLINE_OTHER = OUTLINE_THIN.copy(outlineArgb = OTHER_OUTLINE_ARGB, fillArgb = OTHER_OUTLINE_ARGB)
```

- [ ] **Step 5: Pin the fake's own answer**

In `FakeAnatomyRendererTest.kt`, in `class FakeAnatomyRendererTest`, add:

```kotlin
    @Test
    fun reports_outline_only_for_the_colour_asked_and_only_while_a_pack_is_loaded() = runTest {
        val renderer = FakeAnatomyRenderer()
        val rib = StructureId("rib")
        val magenta = HighlightStyle(0xFFFF00FF.toInt(), 3f, OutlineStyle.SOLID, 0xFF000000.toInt(), 0f)

        renderer.highlight(setOf(rib), magenta)
        assertEquals(0, renderer.outlinePixels(0xFFFF00FF.toInt()))

        renderer.loadPack(PackId("p"), MeshSource("file:///p.glb"))
        assertEquals(300, renderer.outlinePixels(0xFFFF00FF.toInt()))
        assertEquals(0, renderer.outlinePixels(0xFF00FFFF.toInt()))

        renderer.setVisibility(setOf(rib), visible = false)
        assertEquals(0, renderer.outlinePixels(0xFFFF00FF.toInt()))
    }
```

Add any of `HighlightStyle`, `OutlineStyle`, `MeshSource`, `PackId`, `StructureId` and `assertEquals` that the file does not already import.

- [ ] **Step 6: Run the tests on both targets**

Run: `./gradlew :shared:renderer-api:allTests`
Expected: PASS, five more tests per target than before.

Then run: `./gradlew :shared:renderer-filament:compileKotlinIosSimulatorArm64 :shared:renderer-filament:compileAndroidMain`
Expected: PASS. The real contract tests still compile, because `countPixels` has a default.

- [ ] **Step 7: Commit**

```bash
git add shared/renderer-api
git commit -m "test(renderer-api): the contract asks what colour was drawn, and four cases ask it about outlines"
```

---

### Task 4: Android draws the outline — the gate

This task is where the design's assumptions about Filament meet Filament. Read "What a failure means" at the end before starting.

**Files:**
- Create: `shared/renderer-filament/src/androidMain/.../OutlinePass.android.kt`
- Modify: `shared/renderer-filament/src/androidMain/.../FilamentAnatomyRenderer.android.kt`
- Modify: `shared/src/androidMain/.../AnatomyCanvas.android.kt`
- Test: `shared/renderer-filament/src/androidDeviceTest/.../FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: `OutlineMaterialData.bytes` (Task 1); `OutlinePlan.of`, `OutlineGroup`, `FramePixels.countMatching` (Task 2); the four `verify…` cases and `countPixels` (Task 3).
- Produces, on the Android `FilamentAnatomyRenderer`:
  - `fun attachSurface(surface: Any, width: Int, height: Int, refreshHz: Float, pixelsPerDp: Float)`
  - `fun captureFrame(): ByteArray` — test-only; renders a frame and returns it as RGBA bytes, `width * height * 4` long.

- [ ] **Step 1: Write the failing tests**

In the Android `FilamentAnatomyRendererContractTest.kt`, after `override suspend fun settle`, add:

```kotlin
    override suspend fun countPixels(renderer: AnatomyRenderer, argb: Int): Int =
        FramePixels.countMatching((renderer as FilamentAnatomyRenderer).captureFrame(), argb)
```

After the existing `@Test` lines, add:

```kotlin
    @Test fun draws_an_outline_in_the_styles_colour() = runBlocking { verifyAHighlightDrawsAnOutlineInItsColour() }
    @Test fun draws_a_wider_outline_over_more_pixels() = runBlocking { verifyAWiderOutlineCoversMorePixels() }
    @Test fun draws_no_outline_for_a_hidden_structure() = runBlocking { verifyAHiddenStructureHasNoOutline() }
    @Test fun removes_the_outline_with_the_pack() = runBlocking { verifyUnloadingAPackRemovesItsOutline() }

    /** Rotation and split screen hand the renderer a new surface while a structure is highlighted. */
    @Test fun keeps_the_outline_when_the_surface_changes_size() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.attachHeadless(VIEWPORT, VIEWPORT)
            renderer.loadPack(pack, source)
            renderer.highlight(setOf(hitStructure), OUTLINE)
            settle(renderer)
            renderer.attachHeadless(VIEWPORT, VIEWPORT / 2)
            settle(renderer)

            val frame = renderer.captureFrame()
            assertEquals(VIEWPORT * (VIEWPORT / 2) * 4, frame.size)
            assertTrue(FramePixels.countMatching(frame, OUTLINE.outlineArgb) > 0)
        } finally {
            renderer.dispose()
        }
    }
```

In that file's `companion object` add:

```kotlin
        val OUTLINE = HighlightStyle(
            outlineArgb = 0xFFFF00FF.toInt(),
            outlineWidthDp = 4f,
            outlineStyle = OutlineStyle.SOLID,
            fillArgb = 0xFFFF00FF.toInt(),
            fillLuminanceShift = -0.5f,
        )
```

and the imports `com.ptk.anatomypro.renderer.api.HighlightStyle`, `com.ptk.anatomypro.renderer.api.OutlineStyle`, `com.ptk.anatomypro.renderer.api.highlight`, `org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :shared:renderer-filament:compileAndroidDeviceTest` (or the task `connectedAndroidDeviceTest` prints as its compile step)
Expected: FAIL to compile, `Unresolved reference 'captureFrame'`.

- [ ] **Step 3: Write the pass**

`shared/renderer-filament/src/androidMain/kotlin/com/ptk/anatomypro/renderer/filament/OutlinePass.android.kt`:

```kotlin
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
```

- [ ] **Step 4: Wire it into the renderer**

In `FilamentAnatomyRenderer.android.kt`:

Add the imports `com.google.android.filament.Texture` and `java.util.concurrent.atomic.AtomicBoolean`.

After `private var ghostMaterial: MaterialInstance? = null`, add:

```kotlin
    private var pixelsPerDp = 1f
    private var outline: OutlinePass? = null
    private var outlineFailed = false

    /**
     * The outline pass, made the first time something is highlighted.
     *
     * A material that will not load is reported once and the renderer goes on without
     * outlines: the tint still draws, and a missing line must not take the model with it.
     */
    private fun outlinePass(): OutlinePass? {
        outline?.let { return it }
        if (outlineFailed) return null
        return try {
            OutlinePass(engine, OutlineMaterialData.bytes, camera, LAYER_MASK, LAYER_VISIBLE)
                .also { it.resize(width, height); outline = it }
        } catch (failure: RuntimeException) {
            outlineFailed = true
            _events.tryEmit(RendererEvent.Error("outline-unavailable", failure.message ?: "the outline material did not load"))
            null
        }
    }

    /** Tells the outline pass what the highlight map means in entities and pixels. */
    private fun applyOutline() {
        val groups = OutlinePlan.of(highlights, pixelsPerDp)
        if (groups.isEmpty()) {
            outline?.setGroups(emptyList())
            return
        }
        val pass = outlinePass() ?: return
        pass.setGroups(
            groups.map { group ->
                OutlinePass.Spec(
                    entities = group.structures
                        .flatMap { (entitiesByStructure[it] ?: NO_ENTITIES).asIterable() }
                        .toIntArray(),
                    red = group.red,
                    green = group.green,
                    blue = group.blue,
                    alpha = group.alpha,
                    widthPx = group.widthPx,
                )
            }
        )
    }
```

In `init`, change the clear colour's alpha and say why:

```kotlin
        // Alpha 0: an outline mask is cleared with this colour too, and must start
        // transparent. The swap chain is opaque, so the screen is black either way.
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
```

(keep the existing comment above it about why the swap chain is cleared at all).

Replace `attachSurface` with:

```kotlin
    fun attachSurface(surface: Any, width: Int, height: Int, refreshHz: Float, pixelsPerDp: Float) {
        renderer.setDisplayInfo(
            Renderer.DisplayInfo().apply { refreshRate = if (refreshHz > 0f) refreshHz else 60.0f }
        )
        this.pixelsPerDp = pixelsPerDp
        releaseSwapChain()
        configureSurface(engine.createSwapChain(surface), width, height)
    }
```

keeping its KDoc and adding to it: `[pixelsPerDp] is the display's density; outline widths are given in dp.`

In `configureSurface`, after `fitCameraToSurface()`, add:

```kotlin
        outline?.resize(width, height)
        // The density may have come with the surface.
        applyOutline()
```

Replace the body of `renderFrame` after `stepCamera(frameTimeNanos)` with:

```kotlin
        if (!renderer.beginFrame(chain, frameTimeNanos)) return false
        drawViews()
        renderer.endFrame()
        return true
```

and add below it:

```kotlin
    /** Masks first, so the overlay reads this frame's; the overlay last, over the model. */
    private fun drawViews() {
        outline?.renderMasks(renderer)
        renderer.render(view)
        outline?.renderOverlay(renderer)
    }

    /**
     * Test-only: draws a frame and returns it, four bytes a pixel (R, G, B, A), bottom row
     * first. Blocks until the GPU has handed the pixels back.
     */
    fun captureFrame(): ByteArray {
        val chain = swapChain ?: return ByteArray(0)
        val storage = ByteBuffer.allocateDirect(width * height * 4)
        val done = AtomicBoolean(false)
        val descriptor = Texture.PixelBufferDescriptor(
            storage, Texture.Format.RGBA, Texture.Type.UBYTE, 1, 0, 0, width, inlineExecutor,
        ) { done.set(true) }
        var asked = false
        var attempts = 0
        while (!done.get() && attempts++ < CAPTURE_ATTEMPTS) {
            if (renderer.beginFrame(chain, System.nanoTime())) {
                drawViews()
                if (!asked) {
                    renderer.readPixels(0, 0, width, height, descriptor)
                    asked = true
                }
                renderer.endFrame()
            }
            engine.flushAndWait()
        }
        check(done.get()) { "the frame was not read back after $CAPTURE_ATTEMPTS attempts" }
        return ByteArray(storage.capacity()).also { storage.rewind(); storage.get(it) }
    }
```

In the `companion object` add `const val CAPTURE_ATTEMPTS = 16`.

At the end of `applyAppearance()`, after the highlight loop, add:

```kotlin
        applyOutline()
```

`releaseAsset()` already clears `highlights` and calls `applyAppearance()` before it destroys the asset, so the mask scenes let go of the entities first. Do not reorder those lines.

In `dispose()`, after `releaseAsset()`, add:

```kotlin
        outline?.destroy()
        outline = null
```

- [ ] **Step 5: Pass the density from the canvas**

In `shared/src/androidMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.android.kt`, in `surfaceChanged`, replace

```kotlin
                        renderer.attachSurface(holder.surface, width, height, refreshHz)
```

with

```kotlin
                        renderer.attachSurface(holder.surface, width, height, refreshHz, resources.displayMetrics.density)
```

- [ ] **Step 6: Run the contract on the emulator**

Start the emulator: `emulator -avd Medium_Phone_API_36.1`
Run: `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`
Expected: PASS, five more tests than before; every earlier contract test still passes.

If any of the five fails, go to "What a failure means" below before changing anything.

- [ ] **Step 7: Run everything else**

Run: `./gradlew :shared:renderer-filament:allTests :shared:renderer-api:allTests :androidApp:assembleDebug`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/renderer-filament/src/androidMain shared/renderer-filament/src/androidDeviceTest shared/src/androidMain
git commit -m "feat(android): a highlighted structure is outlined, through a mask and an edge pass"
```

**What a failure means**

Each symptom points at one assumption. To see a frame, pull a capture off the emulator: in the failing test, write `captureFrame()` to `InstrumentationRegistry.getInstrumentation().targetContext.cacheDir` as raw bytes and `adb pull` it; view with `python3 -c "from PIL import Image; Image.frombytes('RGBA',(512,512),open('f','rb').read()).transpose(1).save('f.png')"`.

| Symptom | Assumption disproved | What to do |
|---|---|---|
| `Material.Builder().build` throws, or the event `outline-unavailable` arrives | The fetched `matc`'s output loads in the Android runtime | Run `renderer-materials/build/tools/matc --version` and compare with the runtime; check the backend the emulator selected is among `opengl`, `vulkan`. |
| `scene.addEntities` throws, or the count is 0 and a capture shows no line anywhere, and replacing the mask scene's entities with a freshly built test cube gives a line | **An entity cannot be in two scenes** | **Stop. Do not go on to Task 5.** Report to the owner: the spec's fallback is the stencil technique, and that is their decision. |
| The whole screen is the outline colour, or nothing is, and the model is fine | The mask does not start transparent, or opaque materials do not leave alpha 1 | Read the mask back alone: temporarily `renderer.render(slot.view)` to the swap chain. If the background has alpha 1, the clear colour is not what clears a target — give each mask scene a `Skybox` of colour (0,0,0,0) instead. If the model has alpha 0, set the `step` thresholds against `.r + .g + .b` and give mask scenes the sun. |
| The line is there but the model has vanished or is black | The overlay view wipes the main view | Keep `isPostProcessingEnabled = false` and try `overlayView.blendMode = View.BlendMode.OPAQUE` with the material's own blending; if the main view is still wiped, move the quads into the main `scene` with `.priority(7)` and accept tone mapping on the line — then the exact-colour cases need `tolerance` widened, which is a change to bring to the owner. |
| The line is mirrored top to bottom against the model | `uvToRenderTargetUV` is not the right correction on this backend | In `outline.mat` replace it with the plain coordinate, rebuild, and compare on both platforms before choosing. |
| `RenderTarget.Builder().build` throws for want of a depth attachment | A colour-only target is allowed | Add a `DEPTH24` texture with `Texture.Usage.DEPTH_ATTACHMENT` to each slot, attached at `AttachmentPoint.DEPTH`, and release it with the colour. |
| The count is positive before any highlight | A lit surface in the toy asset comes out as pure magenta | Not an outline fault. Change `OUTLINE_ARGB` in the contract to another colour no cube has, in Task 3's file, and say so in the commit. |

---

### Task 5: iOS draws the outline

**Files:**
- Create: `ios-renderer/src/OutlinePass.h`, `ios-renderer/src/OutlinePass.mm`
- Modify: `ios-renderer/CMakeLists.txt`, `ios-renderer/include/anatomy_renderer.h`, `ios-renderer/src/AnatomyRenderer.mm`
- Modify: `shared/renderer-filament/src/iosMain/.../FilamentAnatomyRenderer.kt`
- Modify: `shared/src/iosMain/.../AnatomyCanvas.ios.kt`
- Test: `shared/renderer-filament/src/iosTest/.../FilamentAnatomyRendererContractTest.kt`

**Interfaces:**
- Consumes: as Task 4.
- Produces, in the seam:
  - `void ar_set_outline_material(ar_renderer_ref renderer, const uint8_t* bytes, size_t size);`
  - `void ar_add_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count, const float* tint_rgba, const float* emissive_rgb, const float* outline_rgba, float outline_width_px);`
  - `bool ar_capture_frame(ar_renderer_ref renderer, uint8_t* out_rgba, size_t capacity);`
- Produces, on the iOS `FilamentAnatomyRenderer`:
  - `fun attachLayer(layer: CAMetalLayer, width: Int, height: Int, refreshHz: Float, pixelsPerDp: Float)`
  - `fun captureFrame(): ByteArray`

- [ ] **Step 1: Write the failing tests**

In the iOS `FilamentAnatomyRendererContractTest.kt`, add exactly what Task 4 Step 1 added to the Android one, with `kotlin.test.assertTrue` in place of the JUnit import:

```kotlin
    override suspend fun countPixels(renderer: AnatomyRenderer, argb: Int): Int =
        FramePixels.countMatching((renderer as FilamentAnatomyRenderer).captureFrame(), argb)
```

```kotlin
    @Test fun draws_an_outline_in_the_styles_colour() = runBlocking { verifyAHighlightDrawsAnOutlineInItsColour() }
    @Test fun draws_a_wider_outline_over_more_pixels() = runBlocking { verifyAWiderOutlineCoversMorePixels() }
    @Test fun draws_no_outline_for_a_hidden_structure() = runBlocking { verifyAHiddenStructureHasNoOutline() }
    @Test fun removes_the_outline_with_the_pack() = runBlocking { verifyUnloadingAPackRemovesItsOutline() }

    /** Rotation and split screen hand the renderer a new surface while a structure is highlighted. */
    @Test fun keeps_the_outline_when_the_surface_changes_size() = runBlocking {
        val renderer = FilamentAnatomyRenderer()
        try {
            renderer.attachHeadless(VIEWPORT, VIEWPORT)
            renderer.loadPack(pack, source)
            renderer.highlight(setOf(hitStructure), OUTLINE)
            settle(renderer)
            renderer.attachHeadless(VIEWPORT, VIEWPORT / 2)
            settle(renderer)

            val frame = renderer.captureFrame()
            assertEquals(VIEWPORT * (VIEWPORT / 2) * 4, frame.size)
            assertTrue(FramePixels.countMatching(frame, OUTLINE.outlineArgb) > 0)
        } finally {
            renderer.dispose()
        }
    }
```

and in the `companion object`:

```kotlin
        val OUTLINE = HighlightStyle(
            outlineArgb = 0xFFFF00FF.toInt(),
            outlineWidthDp = 4f,
            outlineStyle = OutlineStyle.SOLID,
            fillArgb = 0xFFFF00FF.toInt(),
            fillLuminanceShift = -0.5f,
        )
```

with the imports `com.ptk.anatomypro.renderer.api.HighlightStyle`, `com.ptk.anatomypro.renderer.api.OutlineStyle`, `com.ptk.anatomypro.renderer.api.highlight`, `kotlin.test.assertTrue`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :shared:renderer-filament:compileTestKotlinIosSimulatorArm64`
Expected: FAIL to compile, `Unresolved reference 'captureFrame'`.

- [ ] **Step 3: Write the pass in the shim**

`ios-renderer/src/OutlinePass.h`:

```cpp
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
```

`ios-renderer/src/OutlinePass.mm`:

```cpp
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
```

If `TextureSampler` has no three-argument constructor in the vendored header (`ios-renderer/build/filament/include/filament/TextureSampler.h`), use `TextureSampler sampler(TextureSampler::MagFilter::LINEAR, TextureSampler::WrapMode::CLAMP_TO_EDGE);`.

In `ios-renderer/CMakeLists.txt` replace

```cmake
add_library(AnatomyRenderer STATIC src/AnatomyRenderer.mm)
```

with

```cmake
add_library(AnatomyRenderer STATIC src/AnatomyRenderer.mm src/OutlinePass.mm)
```

- [ ] **Step 4: Extend the seam**

In `ios-renderer/include/anatomy_renderer.h`:

After `void ar_destroy(ar_renderer_ref renderer);` add:

```c
/*
 * Hands over the compiled outline material, once, before anything is highlighted.
 *
 * The bytes are copied. Without them, or if they do not load, highlights are still tinted
 * and AR_EVENT_ERROR with code "outline-unavailable" is queued once.
 */
void ar_set_outline_material(ar_renderer_ref renderer, const uint8_t* bytes, size_t size);
```

Replace the `ar_add_highlight` declaration and extend its comment:

```c
 * `outline_rgba` is four floats, the outline colour as unconverted fractions of 255, and
 * `outline_width_px` its width in pixels. Groups with the same outline share one mask.
 */
void ar_add_highlight(ar_renderer_ref renderer, const char* const* node_names, size_t count,
                      const float* tint_rgba, const float* emissive_rgb,
                      const float* outline_rgba, float outline_width_px);
```

After the `ar_wait_for_gpu` declaration add:

```c
/*
 * Renders a frame and copies it out, four bytes a pixel (R, G, B, A), bottom row first.
 *
 * Exists for tests. `capacity` must be at least width * height * 4. Blocks until the GPU
 * has handed the pixels back; returns false when it did not, or when the buffer is too small.
 */
bool ar_capture_frame(ar_renderer_ref renderer, uint8_t* out_rgba, size_t capacity);
```

- [ ] **Step 5: Wire the shim**

In `ios-renderer/src/AnatomyRenderer.mm`:

Add `#include "OutlinePass.h"`, `#include <backend/PixelBufferDescriptor.h>` and `#include <atomic>`.

Extend `HighlightGroup`:

```cpp
struct HighlightGroup {
    std::vector<std::string> nodes;
    float4 tint;
    float3 emissive;
    float4 outline;
    float outlineWidthPx;
};
```

In `struct ar_renderer`, after `MaterialInstance* ghostMaterial = nullptr;`, add:

```cpp
    std::vector<uint8_t> outlineMaterial;
    anatomy::OutlinePass* outline = nullptr;
    bool outlineFailed = false;
```

In the anonymous namespace, immediately before `void applyAppearance(ar_renderer* r)`, add:

```cpp
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
```

At the end of `applyAppearance`, after the highlight loop, add `applyOutline(r);`.

`releaseAsset` already clears `r->highlights` and calls `applyAppearance(r)` before `destroyAsset`, so the mask scenes let go of the entities first. Do not reorder those lines.

In `ar_create`, change the clear colour's alpha to 0 and add a line to the comment above it:

```cpp
    // Alpha 0: an outline mask is cleared with this colour too, and must start transparent.
    // The layer is opaque, so the screen is black either way.
    r->renderer->setClearOptions({.clearColor = {0.0f, 0.0f, 0.0f, 0.0f}, .clear = true});
```

In `configureSurface`, after the viewport is set and `r->width` and `r->height` are stored, add:

```cpp
    if (r->outline) r->outline->resize(width, height);
```

In `ar_destroy`, immediately after `releaseAsset(r);`, add:

```cpp
        delete r->outline;
        r->outline = nullptr;
```

After `ar_destroy`, add:

```cpp
void ar_set_outline_material(ar_renderer_ref r, const uint8_t* bytes, size_t size) {
    if (!r || !bytes || size == 0) return;
    r->outlineMaterial.assign(bytes, bytes + size);
}
```

Replace `ar_add_highlight`'s signature and its group construction:

```cpp
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
```

(the rest of the function is unchanged).

Replace the body of `ar_render_frame` after the `beginFrame` check, and add the helper above it in an anonymous namespace:

```cpp
namespace {

/* Masks first, so the overlay reads this frame's; the overlay last, over the model. */
void drawViews(ar_renderer* r) {
    if (r->outline) r->outline->renderMasks(r->renderer);
    r->renderer->render(r->view);
    if (r->outline) r->outline->renderOverlay(r->renderer);
}

} // namespace
```

```cpp
    if (!r->renderer->beginFrame(r->swapChain, vsync_nanos)) return false;
    drawViews(r);
    r->renderer->endFrame();
    return true;
```

After `ar_wait_for_gpu`, add:

```cpp
bool ar_capture_frame(ar_renderer_ref r, uint8_t* out, size_t capacity) {
    if (!r || !r->engine || !r->swapChain || !out) return false;
    const size_t size = size_t(r->width) * size_t(r->height) * 4;
    if (size == 0 || capacity < size) return false;

    // On the heap: if the GPU never answers, the callback may still fire after we return,
    // and must not write to a dead stack frame. Leaked in that case, deliberately.
    auto* done = new std::atomic<bool>(false);
    bool asked = false;
    for (int attempt = 0; attempt < 16 && !done->load(); ++attempt) {
        if (r->renderer->beginFrame(r->swapChain, 0)) {
            drawViews(r);
            if (!asked) {
                backend::PixelBufferDescriptor descriptor(
                    out, size, backend::PixelDataFormat::RGBA, backend::PixelDataType::UBYTE,
                    &immediateHandler(),
                    [](void*, size_t, void* user) { static_cast<std::atomic<bool>*>(user)->store(true); },
                    done);
                r->renderer->readPixels(0, 0, r->width, r->height, std::move(descriptor));
                asked = true;
            }
            r->renderer->endFrame();
        }
        r->engine->flushAndWait();
    }
    const bool ok = done->load();
    if (ok) delete done;
    return ok;
}
```

If that `PixelBufferDescriptor` constructor does not exist, the header `ios-renderer/build/filament/include/backend/PixelBufferDescriptor.h` lists the ones that do; use the one taking a `CallbackHandler*`, a callback and a user pointer.

- [ ] **Step 6: Wire the Kotlin renderer**

In `shared/renderer-filament/src/iosMain/kotlin/com/ptk/anatomypro/renderer/filament/FilamentAnatomyRenderer.kt`:

Add the imports `com.ptk.anatomypro.renderer.filament.cinterop.ar_capture_frame`, `com.ptk.anatomypro.renderer.filament.cinterop.ar_set_outline_material`, `kotlinx.cinterop.addressOf`, `kotlinx.cinterop.convert`, `kotlinx.cinterop.reinterpret`, `kotlinx.cinterop.usePinned`.

After the `handle` property, add:

```kotlin
    init {
        val material = OutlineMaterialData.bytes
        material.usePinned { ar_set_outline_material(handle, it.addressOf(0).reinterpret(), material.size.convert()) }
    }

    private var pixelsPerDp = 1f
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Held so the outline widths can be sent again when the density arrives with a surface. */
    private var highlights: Map<StructureId, HighlightStyle> = emptyMap()
```

Replace `attachHeadless` and `attachLayer`:

```kotlin
    /** Draws offscreen. Used by contract tests, which have no window. */
    fun attachHeadless(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        ar_attach_headless(handle, width.toUInt(), height.toUInt())
        drain()
        keepCameraAcrossSurfaceChange()
    }

    /**
     * Draws into a `CAMetalLayer` owned by the host app.
     *
     * [pixelsPerDp] is the screen's scale; outline widths are given in dp.
     */
    fun attachLayer(layer: CAMetalLayer, width: Int, height: Int, refreshHz: Float, pixelsPerDp: Float) {
        surfaceWidth = width
        surfaceHeight = height
        ar_attach_layer(
            handle,
            interpretCPointer<CPointed>(layer.objcPtr()),
            width.toUInt(),
            height.toUInt(),
            refreshHz,
        )
        drain()
        keepCameraAcrossSurfaceChange()
        if (pixelsPerDp != this.pixelsPerDp) {
            this.pixelsPerDp = pixelsPerDp
            highlight(highlights)
        }
    }
```

After `waitForGpu`, add:

```kotlin
    /**
     * Test-only: draws a frame and returns it, four bytes a pixel (R, G, B, A), bottom row
     * first. Blocks until the GPU has handed the pixels back.
     */
    fun captureFrame(): ByteArray {
        val frame = ByteArray(surfaceWidth * surfaceHeight * 4)
        if (frame.isEmpty()) return frame
        val captured = frame.usePinned {
            ar_capture_frame(handle, it.addressOf(0).reinterpret(), frame.size.convert())
        }
        drain()
        check(captured) { "the frame was not read back" }
        return frame
    }
```

In `forgetPerPackState`, add `highlights = emptyMap()`.

Replace `highlight`:

```kotlin
    override fun highlight(styles: Map<StructureId, HighlightStyle>) {
        highlights = styles.toMap()
        val outlines = OutlinePlan.of(styles, pixelsPerDp)
        // Clear, then one group per style. No frame is drawn in between: this runs to
        // completion on the thread that renders.
        ar_clear_highlight(handle)
        for ((style, structures) in styles.entries.groupBy({ it.value }, { it.key })) {
            val nodes = structures.flatMap { nodesByStructure[it].orEmpty() }
            if (nodes.isEmpty()) continue // groups, and structures this pack does not draw
            val paint = HighlightPaint.of(style)
            // Every structure of one style is in one outline group; the shim merges styles
            // that share an outline.
            val outline = outlines.first { structures.first() in it.structures }
            passNodes(nodes) { names, count ->
                memScoped {
                    val tint = allocArrayOf(paint.red, paint.green, paint.blue, paint.alpha)
                    val emissive = allocArrayOf(paint.emissiveRed, paint.emissiveGreen, paint.emissiveBlue)
                    val line = allocArrayOf(outline.red, outline.green, outline.blue, outline.alpha)
                    ar_add_highlight(handle, names, count, tint, emissive, line, outline.widthPx)
                }
            }
        }
        drain()
    }
```

- [ ] **Step 7: Pass the scale from the canvas**

In `shared/src/iosMain/kotlin/com/ptk/anatomypro/AnatomyCanvas.ios.kt`, replace

```kotlin
                    renderer.attachLayer(layer, widthPx, heightPx, refreshHz)
```

with

```kotlin
                    renderer.attachLayer(layer, widthPx, heightPx, refreshHz, scale.toFloat())
```

- [ ] **Step 8: Run the contract on the simulator**

Make sure no app is running on the simulator.
Run: `./gradlew :shared:renderer-filament:iosSimulatorArm64Test`
Expected: PASS, five more tests than before; every earlier contract test still passes.

A failure here that did not happen on Android is a Metal difference. Task 4's "What a failure means" table applies; the two rows most likely on Metal are the mirrored line and the overlay wiping the main view. A fix in `outline.mat` must then be run on the emulator again (`connectedAndroidDeviceTest`), because the material is shared.

- [ ] **Step 9: Run everything**

Run: `./gradlew allTests :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add ios-renderer shared/renderer-filament/src/iosMain shared/renderer-filament/src/iosTest shared/src/iosMain
git commit -m "feat(ios): a highlighted structure is outlined, by the same mask and edge pass"
```

---

### Task 6: Seen by hand, and written down

**Files:**
- Modify: `docs/state-of-play.md`
- Modify: `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md` (§21.5, §26.7, a new §35)
- Modify: `shared/renderer-filament/src/commonMain/.../HighlightPaint.kt` (its KDoc only)

**Interfaces:**
- Consumes: the running apps from Tasks 4 and 5.

- [ ] **Step 1: Hand check on the Android emulator**

Run: `./gradlew :androidApp:installDebug -Panatomypro.pack=skeletal-trunk`, open the app.

Record each as SEEN, DIFFERS (say how) or NOT SEEN:

1. Atlas: tap a vertebra. It is tinted as before and has a pale line around it.
2. Tap another bone: the line moves with the tint and nothing is left on the first.
3. Tap empty canvas: no line anywhere.
4. Rotate the model so the selected bone is behind the ribs: its line is still drawn, over the ribs.
5. Screen 07: with a bone selected, hide the skeletal system. No line. Show it: the line is back.
6. Screen 07: ghost the skeletal system with a bone selected. The bone is drawn solid and outlined.
7. Rotate the device (or resize the window): the line is still there and still follows the bone.
8. Test tab, a "find the structure" question answered wrongly (screen 12): the expected and the chosen structure each have a line, in two colours.
9. The line's width looks the same on a near bone and a far one.

Take one screenshot of item 1 and one of item 8.

- [ ] **Step 2: Hand check on the iOS simulator**

Build and run `iosApp` on the iPhone 17 simulator. Items 1 to 4, 7 and 9 as above. Items 5, 6 and 8 as far as the iOS build reaches them (the quiz is Android debug only; record 8 as NOT SEEN on iOS with that reason).

Compare the screenshot of item 1 with Android's: say whether the line's colour and width look alike.

- [ ] **Step 3: Write the record**

Add to `docs/superpowers/specs/2026-08-29-anatomy-pro-design.md`, after §34, a section `## 35. Addendum — <date>: solid outlines`, with these subsections, each stating only what was done or seen:

- `### 35.1 What is drawn` — the mask per outline group, the overlay, the half-resolution target, that the outline shows through; one paragraph, pointing at the outline spec for the reasoning.
- `### 35.2 The build stage` — `:renderer-materials`, the pinned macOS release and its checksum entry, that the material reaches both platforms as generated Kotlin, that the stage runs on macOS only.
- `### 35.3 What the plan assumed and what Filament did` — one line for each row of Task 4's "What a failure means" table: held, or what replaced it.
- `### 35.4 Verified` — the test counts (run `./gradlew allTests` and the instrumented suite and quote their totals with the commit hash), and the hand-check items SEEN, per platform.
- `### 35.5 Not verified` — every item recorded DIFFERS or NOT SEEN; frame cost on hardware (unmeasured, task CF82); a physical device of either kind; `DASHED` draws solid.

In §21.5, add a line under its highlighting statement: `Superseded for outlines by §35: solid outlines are drawn on both platforms.`

In §26.7, add at the end: `Landed as §35, solid only.`

In `docs/state-of-play.md`:
- change the date in the title;
- in "What to do next", replace the paragraph headed **Outline highlighting, §12 — solid outlines first.** with one saying solid outlines are drawn on both platforms (§35), what remains for §12 (dashed), and that hardware cost is unmeasured;
- in the screens paragraph, change screen 12's line to say it now has outlines and name the two reasons it is still unfinished (camera framing; colours wait on 5D17);
- under "Not verified", add the outline's hardware cost;
- update the test totals and their commit hash.

In `HighlightPaint.kt`, replace the KDoc sentence "outlines themselves are §12's shader work and are not drawn yet." with "the outline itself is drawn by the outline pass, from [OutlinePlan]."

- [ ] **Step 4: Run the whole suite at head**

Run: `./gradlew allTests --rerun-tasks`, then with the emulator up `./gradlew :shared:renderer-filament:connectedAndroidDeviceTest`.
Expected: PASS. The totals are the ones Step 3 quotes; if they differ, correct the documents.

- [ ] **Step 5: Commit**

```bash
git add docs shared/renderer-filament/src/commonMain
git commit -m "docs: solid outlines are drawn — what was built, assumed, seen and not seen (§35)"
```

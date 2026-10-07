import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins { id("anatomypro.kmp.library") }

/**
 * Linked in dependency order, matching the list staged by `:ios-renderer`.
 *
 * These reach the link because the Kotlin/Native test binary resolves the cinterop's
 * symbols itself — which is the whole reason the seam is a C library rather than a Swift
 * object injected at startup: the contract tests can link the real renderer (spec §15).
 */
private val filamentLinkOrder = listOf(
    "AnatomyRenderer", "gltfio_core", "filament", "backend", "filabridge", "filaflat",
    "ibl", "geometry", "utils", "smol-v", "abseil", "uberarchive", "uberzlib",
    "dracodec", "meshoptimizer", "stb", "basis_transcoder", "ktxreader", "zstd",
    "mikktspace", "image",
)

// OpenGLES is here because libbackend.a ships the GLES driver in the same archive as the
// Metal one, so its symbols must resolve even though this app never selects that backend.
private val iosFrameworks = listOf(
    "Metal", "MetalKit", "OpenGLES", "Foundation", "CoreVideo", "CoreGraphics",
    "QuartzCore", "IOSurface", "UIKit",
)

kotlin {
    android {
        namespace = "com.ptk.anatomypro.renderer.filament"
        // The renderer contract needs a real GPU, which the JVM host test has no access
        // to. This is the Android counterpart of giving up the iOS simulator's standalone
        // spawn: the tests must run on a device or emulator.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val stageLib = rootProject.layout.projectDirectory
            .dir("ios-renderer/build/stage/${target.name}/lib").asFile.absolutePath

        target.compilations.getByName("main").cinterops.create("anatomyRenderer") {
            definitionFile.set(layout.projectDirectory.file("src/nativeInterop/cinterop/anatomyRenderer.def"))
            includeDirs(rootProject.layout.projectDirectory.dir("ios-renderer/include"))
        }

        target.binaries.all {
            linkerOpts("-L$stageLib")
            linkerOpts(filamentLinkOrder.map { "-l$it" })
            linkerOpts(iosFrameworks.flatMap { listOf("-framework", it) })
            linkerOpts("-lc++")
        }
    }

    sourceSets {
        commonMain {
            // The outline material, compiled by :renderer-materials and written as Kotlin.
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("renderer-materials/build/generated/kotlin"))
            dependencies { api(project(":shared:renderer-api")) }
        }
        androidMain.dependencies {
            // Google's own Filament artifacts, pinned to the same version the iOS host
            // links. SceneView was rejected because it keeps its own scene graph, and §4
            // says Kotlin owns all state and the renderer holds no truth of its own.
            implementation(libs.filament.android)
            implementation(libs.filament.gltfio)
            implementation(libs.filament.utils)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// The staged libraries are a link-time input, so every native link waits on the slice it
// consumes. Declared per target rather than globally so a simulator build never has to
// build the device slice.
//
// They must be a declared *input*, not merely a dependency: with only `dependsOn`, Gradle
// cannot see that a rebuilt libAnatomyRenderer.a changes anything, holds the link task
// UP-TO-DATE, and silently keeps linking the previous shim.
tasks.withType<KotlinNativeLink>().configureEach {
    val slice = binary.target.name
    val stage = rootProject.layout.projectDirectory.dir("ios-renderer/build/stage/$slice")
    dependsOn(":ios-renderer:stageNative${slice.replaceFirstChar(Char::titlecase)}")
    inputs.dir(stage)
        .withPropertyName("filamentHost")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

/**
 * These tests need a real Metal device, which the default standalone spawn does not provide.
 *
 * This is the only module whose tests touch the GPU, so the cost of a booted simulator is
 * paid here rather than in the shared convention plugin.
 */
val simulatorDevice = providers.gradleProperty("anatomypro.iosSimulatorDevice").orElse("iPhone 17")

val bootIosSimulator = tasks.register<BootIosSimulator>("bootIosSimulator") {
    description = "Boots the simulator used by the renderer contract tests."
    device.set(simulatorDevice)
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    device.set(simulatorDevice)
    standalone.set(false)
    dependsOn(bootIosSimulator)
}

// The generated material source is an input of every compilation of this module. As with
// the staged native libraries above, the producing task lives in another project, so the
// dependency is declared by path.
tasks.matching { it.name.startsWith("compile") || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(":renderer-materials:generateMaterialSource")
}

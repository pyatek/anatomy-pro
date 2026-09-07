import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * The iOS Filament host: an Objective-C++ static library behind a flat C header.
 *
 * Kotlin/Native reaches Filament through cinterop over that header (spec §4.1), so the
 * Kotlin framework carries no Filament symbols of its own and the graphics provider stays
 * a link-time choice. This project owns only the native build; it applies no Kotlin plugin
 * and produces no JVM artifacts.
 */

val pinnedFilamentVersion = libs.versions.filament.get()
val filamentSha256 = libs.versions.filamentIosSha256.get()

/**
 * Downloads and unpacks the pinned Filament binary release.
 *
 * The SDK is 105 MB unpacked, so it is fetched rather than committed. The checksum is the
 * pin: a release asset that changed underneath us fails the build instead of silently
 * altering what we link against.
 */
abstract class FetchFilament : DefaultTask() {

    @get:Input abstract val version: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:OutputDirectory abstract val sdkDirectory: DirectoryProperty
    @get:Internal abstract val archive: RegularFileProperty

    @get:Inject abstract val archives: ArchiveOperations
    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun fetch() {
        val tgz = archive.get().asFile
        if (!tgz.isFile || digestOf(tgz) != sha256.get()) {
            tgz.parentFile.mkdirs()
            val url = "https://github.com/google/filament/releases/download/" +
                "v${version.get()}/filament-v${version.get()}-ios.tgz"
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input ->
                tgz.outputStream().use(input::copyTo)
            }
            val actual = digestOf(tgz)
            check(actual == sha256.get()) {
                "Filament ${version.get()} checksum mismatch.\n" +
                    "  expected ${sha256.get()}\n" +
                    "  actual   $actual"
            }
        }

        val destination = sdkDirectory.get().asFile
        destination.deleteRecursively()
        fs.copy {
            from(archives.tarTree(archives.gzip(tgz)))
            // The archive nests everything under a `filament/` root; drop it so the output
            // directory is the SDK root itself and consumers need no extra path segment.
            eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
            includeEmptyDirs = false
            into(destination)
        }
    }

    private fun digestOf(file: java.io.File): String {
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

val fetchFilament by tasks.registering(FetchFilament::class) {
    description = "Downloads and verifies the pinned Filament iOS binary release."
    version.set(pinnedFilamentVersion)
    sha256.set(filamentSha256)
    archive.set(layout.buildDirectory.file("filament-download/filament-v$pinnedFilamentVersion-ios.tgz"))
    sdkDirectory.set(layout.buildDirectory.dir("filament"))
}

/**
 * The Filament static libraries we link, in dependency order.
 *
 * Listed explicitly rather than globbed: a link failure naming a missing symbol is a
 * better failure than silently picking up whatever a future release happens to ship.
 */
val filamentLibraries = listOf(
    "gltfio_core", "filament", "backend", "filabridge", "filaflat", "ibl",
    "geometry", "utils", "smol-v", "abseil", "uberarchive", "uberzlib",
    "dracodec", "meshoptimizer", "stb", "basis_transcoder", "ktxreader",
    "zstd", "mikktspace", "image",
)

/** One iOS slice: the SDK to compile against, and the matching xcframework directory. */
data class Slice(val konanName: String, val sysroot: String, val xcframeworkSlice: String)

val slices = listOf(
    Slice("iosSimulatorArm64", "iphonesimulator", "ios-arm64_x86_64-simulator"),
    Slice("iosArm64", "iphoneos", "ios-arm64"),
)

/** Configures and builds the Objective-C++ host for one slice via cmake and ninja. */
abstract class BuildNativeHost : DefaultTask() {

    // Declared file by file rather than as the project directory, which contains `build`
    // and would make this task both consume and produce the same tree.
    @get:InputFile abstract val cmakeLists: RegularFileProperty
    @get:InputDirectory abstract val sources: DirectoryProperty
    @get:InputDirectory abstract val headers: DirectoryProperty
    // The SDK is 105 MB of unchanging binaries; the pinned version identifies it far more
    // cheaply than hashing it on every build.
    @get:Input abstract val filamentVersion: Property<String>
    @get:Internal abstract val filamentSdk: DirectoryProperty
    @get:Internal abstract val projectDirectory: DirectoryProperty
    @get:Input abstract val sysroot: Property<String>
    @get:Input abstract val deploymentTarget: Property<String>
    @get:OutputDirectory abstract val cmakeDirectory: DirectoryProperty

    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val cmakeDir = cmakeDirectory.get().asFile
        cmakeDir.mkdirs()
        exec.exec {
            commandLine(
                "cmake",
                "-G", "Ninja",
                "-S", projectDirectory.get().asFile.absolutePath,
                "-B", cmakeDir.absolutePath,
                "-DCMAKE_SYSTEM_NAME=iOS",
                "-DCMAKE_OSX_SYSROOT=${sysroot.get()}",
                "-DCMAKE_OSX_ARCHITECTURES=arm64",
                "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTarget.get()}",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DFILAMENT_SDK=${filamentSdk.get().asFile.absolutePath}",
            )
        }
        exec.exec { commandLine("cmake", "--build", cmakeDir.absolutePath) }
    }
}

/**
 * Collects everything cinterop and the Kotlin/Native linker need into one directory.
 *
 * A single staging directory per slice means the consuming module points at exactly two
 * paths — `include` and `lib` — instead of reaching into the SDK layout and the cmake
 * build tree separately.
 */
abstract class StageNativeHost : DefaultTask() {

    @get:InputDirectory abstract val headerDirectory: DirectoryProperty
    @get:InputDirectory abstract val cmakeDirectory: DirectoryProperty
    @get:Input abstract val filamentVersion: Property<String>
    @get:Internal abstract val filamentSdk: DirectoryProperty
    @get:Input abstract val xcframeworkSlice: Property<String>
    @get:Input abstract val libraries: ListProperty<String>
    @get:OutputDirectory abstract val stageDirectory: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    @TaskAction
    fun stage() {
        val stage = stageDirectory.get().asFile
        stage.deleteRecursively()

        fs.copy {
            from(headerDirectory)
            into(File(stage, "include"))
        }
        fs.copy {
            from(cmakeDirectory) { include("libAnatomyRenderer.a") }
            into(File(stage, "lib"))
        }

        val sdkLib = File(filamentSdk.get().asFile, "lib")
        val slice = xcframeworkSlice.get()
        libraries.get().forEach { name ->
            val archive = File(sdkLib, "lib$name.xcframework/$slice/lib$name.a")
            check(archive.isFile) { "Filament release has no lib$name.a for $slice" }
            fs.copy {
                from(archive)
                into(File(stage, "lib"))
            }
        }
    }
}

slices.forEach { slice ->
    val buildTask = tasks.register<BuildNativeHost>("buildNative${slice.konanName.replaceFirstChar(Char::titlecase)}") {
        description = "Builds libAnatomyRenderer.a for ${slice.konanName}."
        dependsOn(fetchFilament)
        cmakeLists.set(layout.projectDirectory.file("CMakeLists.txt"))
        sources.set(layout.projectDirectory.dir("src"))
        headers.set(layout.projectDirectory.dir("include"))
        projectDirectory.set(layout.projectDirectory)
        filamentVersion.set(pinnedFilamentVersion)
        filamentSdk.set(fetchFilament.flatMap { it.sdkDirectory })
        sysroot.set(slice.sysroot)
        deploymentTarget.set("18.2")
        cmakeDirectory.set(layout.buildDirectory.dir("cmake/${slice.konanName}"))
    }

    tasks.register<StageNativeHost>("stageNative${slice.konanName.replaceFirstChar(Char::titlecase)}") {
        description = "Stages headers and static libraries for ${slice.konanName}."
        headerDirectory.set(layout.projectDirectory.dir("include"))
        cmakeDirectory.set(buildTask.flatMap { it.cmakeDirectory })
        filamentVersion.set(pinnedFilamentVersion)
        filamentSdk.set(fetchFilament.flatMap { it.sdkDirectory })
        xcframeworkSlice.set(slice.xcframeworkSlice)
        libraries.set(filamentLibraries)
        stageDirectory.set(layout.buildDirectory.dir("stage/${slice.konanName}"))
    }
}

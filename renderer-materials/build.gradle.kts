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

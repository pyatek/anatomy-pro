package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.anatomyDatabase
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfFile

/**
 * Names the bundled pack to draw: `-anatomypro.pack muscular-trunk` on the launch line.
 *
 * A launch argument rather than a build property, because §25.4's method needs the variants
 * interleaved inside one session — relaunching one install is a session, reinstalling is not.
 */
private const val PACK_ARGUMENT = "anatomypro.pack"

/** The free pack of §10, and the one the atlas tree is known to be right on. */
private const val DEFAULT_PACK = "skeletal-trunk"

@Composable
actual fun rememberAnatomyDatabase(): AnatomyDatabase = remember { anatomyDatabase() }

@Composable
actual fun rememberBundledPack(): BundledPack = remember { resolveBundledPack() }

/**
 * Prefers a pipeline pack staged into the app bundle, falling back to the built-in toy.
 *
 * The generated packs live under `pipeline/build`, which is not committed, so a checkout
 * that has never run the pipeline still gets something that draws.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun resolveBundledPack(): BundledPack {
    val id = NSUserDefaults.standardUserDefaults.stringForKey(PACK_ARGUMENT) ?: DEFAULT_PACK
    val directory = "${NSBundle.mainBundle.resourcePath}/packs/$id"
    val mesh = "$directory/mesh.glb"

    if (!NSFileManager.defaultManager.fileExistsAtPath(mesh)) {
        return BundledPack(
            id = PackId("phase0-toy"),
            mesh = MeshSource("file://${Phase0ToyAsset.path}"),
            manifestJson = null,
            label = "phase0-toy ('$id' is not bundled)",
        )
    }
    return BundledPack(
        id = PackId(id),
        mesh = MeshSource("file://$mesh"),
        manifestJson = NSString.stringWithContentsOfFile(
            "$directory/manifest.json",
            encoding = NSUTF8StringEncoding,
            error = null,
        ),
        label = id,
    )
}

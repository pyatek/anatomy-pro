package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.anatomyDatabase
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset
import java.io.File

private const val BUNDLED_MESH = "packs/phase0-pack.glb"
private const val BUNDLED_ID = "packs/phase0-pack.id"
private const val BUNDLED_MANIFEST = "packs/phase0-pack.json"

@Composable
actual fun rememberAnatomyDatabase(): AnatomyDatabase {
    val context = LocalContext.current.applicationContext
    return remember(context) { anatomyDatabase(context) }
}

/**
 * Prefers the pipeline's pack, falling back to the built-in toy.
 *
 * The generated packs live under `pipeline/build`, which is not committed, so a checkout
 * that has never run the pipeline still gets something that draws.
 */
@Composable
actual fun rememberBundledPack(): BundledPack {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        runCatching {
            val id = context.assets.open(BUNDLED_ID).use { it.readBytes().decodeToString().trim() }
            val target = File(context.cacheDir, "$id.glb")
            if (!target.isFile || target.length() == 0L) {
                context.assets.open(BUNDLED_MESH).use { input ->
                    target.outputStream().use(input::copyTo)
                }
            }
            BundledPack(
                id = PackId(id),
                mesh = MeshSource("file://${target.absolutePath}"),
                manifestJson = runCatching {
                    context.assets.open(BUNDLED_MANIFEST).use { it.readBytes().decodeToString() }
                }.getOrNull(),
                label = id,
            )
        }.getOrElse {
            BundledPack(
                id = PackId("phase0-toy"),
                mesh = MeshSource("file://${Phase0ToyAsset.path}"),
                manifestJson = null,
                label = "phase0-toy (no pipeline output bundled)",
            )
        }
    }
}

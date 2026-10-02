package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.PackInstaller
import com.ptk.anatomypro.core.data.notBuiltAppDependencies
import com.ptk.anatomypro.core.data.repository.AtlasRepository
import com.ptk.anatomypro.core.data.repository.RoomAtlasRepository
import com.ptk.anatomypro.core.data.repository.RoomSettingsRepository
import com.ptk.anatomypro.core.data.repository.SettingsRepository
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.renderer.api.MeshSource

/** The content pack shipped inside the app, and the metadata describing it. */
data class BundledPack(
    val id: PackId,
    val mesh: MeshSource,
    val manifestJson: String?,
    val label: String,
)

/** Everything the atlas needs, once the bundled pack has been installed. */
data class AtlasDependencies(
    val pack: BundledPack,
    val repository: AtlasRepository,
)

@Composable
expect fun rememberBundledPack(): BundledPack

@Composable
expect fun rememberAnatomyDatabase(): AnatomyDatabase

/**
 * Installs the bundled pack once, then hands back what the screen needs.
 *
 * Returns null while that is in flight rather than an empty repository, so the screen can
 * tell "still loading" from "nothing installed" — which are different things to show.
 */
@Composable
fun rememberAtlas(): AtlasDependencies? {
    val pack = rememberBundledPack()
    val database = rememberAnatomyDatabase()
    var dependencies by remember { mutableStateOf<AtlasDependencies?>(null) }

    LaunchedEffect(pack, database) {
        pack.manifestJson?.let { manifest ->
            PackInstaller(database).install(manifest, version = 1, meshUri = pack.mesh.uri)
        }
        dependencies = AtlasDependencies(pack, RoomAtlasRepository(database))
    }
    return dependencies
}

/** Settings live in the same database, so they open with it and need no second store. */
@Composable
fun rememberSettingsRepository(): SettingsRepository {
    val database = rememberAnatomyDatabase()
    return remember(database) { RoomSettingsRepository(database) }
}

/**
 * The production set.
 *
 * Six of the eight repositories refuse rather than answer; see notBuiltAppDependencies.
 * Replacing one when Phase 3 builds it is a one-line change here and nothing else.
 *
 * The atlas is installed here, at the entry point, rather than inside the Atlas tab: above
 * ProvideAppLocale, so a language change does not reinstall the pack, and once, so returning
 * to the tab does not either. [AppDependencies.atlas] is null until the install finishes.
 */
@Composable
fun rememberAppDependencies(): AppDependencies {
    val atlas = rememberAtlas()
    val settings = rememberSettingsRepository()
    return remember(atlas, settings) { notBuiltAppDependencies(atlas = atlas?.repository, settings = settings) }
}

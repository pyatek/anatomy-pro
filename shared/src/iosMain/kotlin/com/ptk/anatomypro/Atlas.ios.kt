package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.anatomyDatabase
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset

@Composable
actual fun rememberAnatomyDatabase(): AnatomyDatabase = remember { anatomyDatabase() }

/**
 * iOS has no pipeline pack bundled yet, so the harness draws the toy asset and the atlas
 * tree stays empty. The wiring is identical; only the content is missing.
 */
@Composable
actual fun rememberBundledPack(): BundledPack = remember {
    BundledPack(
        id = PackId("phase0-toy"),
        mesh = MeshSource("file://${Phase0ToyAsset.path}"),
        manifestJson = null,
        label = "phase0-toy",
    )
}

package com.ptk.anatomypro

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.platform.LocalDensity
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import com.ptk.anatomypro.renderer.filament.FilamentAnatomyRenderer
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import platform.QuartzCore.CAMetalLayer
import platform.UIKit.UIScreen
import platform.UIKit.UIView

/**
 * Hosts Filament in a `CAMetalLayer` and pumps it from a coroutine.
 *
 * Frames are driven here rather than inside the renderer for the same reason the contract
 * tests drive them: the renderer stays a passive projection of Kotlin state, with no
 * thread and no loop of its own.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AnatomyCanvas(
    modifier: Modifier,
    onPicked: (StructureId?) -> Unit,
) {
    val scale = UIScreen.mainScreen.scale
    val density = LocalDensity.current.density
    val renderer = remember { FilamentAnatomyRenderer() }
    val currentOnPicked by rememberUpdatedState(onPicked)

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    UIKitView(
        factory = {
            val view = UIView(frame = CGRectZero.readValue())
            val layer = CAMetalLayer()
            layer.contentsScale = scale
            view.layer.addSublayer(layer)
            view
        },
        modifier = modifier.pointerInput(renderer) {
            detectTapGestures { offset ->
                renderer.pickAt(offset.x * scale.toFloat() / density, offset.y * scale.toFloat() / density)
            }
        },
        onResize = { view, rect ->
            val metalLayer = view.layer.sublayers?.firstOrNull() as? CAMetalLayer ?: return@UIKitView
            metalLayer.setFrame(rect)
            rect.useContents {
                val widthPx = (size.width * scale).toInt()
                val heightPx = (size.height * scale).toInt()
                if (widthPx > 0 && heightPx > 0) {
                    renderer.attachLayer(metalLayer, widthPx, heightPx)
                }
            }
        },
    )

    LaunchedEffect(renderer) {
        renderer.loadPack(PackId("phase0-toy"), MeshSource("file://${Phase0ToyAsset.path}"))
        while (true) {
            renderer.renderFrame()
            delay(16)
        }
    }

    LaunchedEffect(renderer) {
        renderer.events.filterIsInstance<RendererEvent.Picked>().collect {
            currentOnPicked(it.structure)
        }
    }
}

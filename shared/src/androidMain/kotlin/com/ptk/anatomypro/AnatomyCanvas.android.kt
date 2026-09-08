package com.ptk.anatomypro

import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import com.ptk.anatomypro.renderer.filament.FilamentAnatomyRenderer
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Hosts Filament in a `SurfaceView` and pumps it from a coroutine.
 *
 * The same shape as the iOS actual: the platform owns the surface, the caller owns the
 * frame loop, and the renderer keeps no thread of its own (spec §4, §4.1).
 */
@Composable
actual fun AnatomyCanvas(
    modifier: Modifier,
    onPicked: (StructureId?) -> Unit,
) {
    val renderer = remember { FilamentAnatomyRenderer() }
    val currentOnPicked by rememberUpdatedState(onPicked)

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        renderer.attachSurface(holder.surface, width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
            }
        },
        modifier = modifier.pointerInput(renderer) {
            detectTapGestures { offset -> renderer.pickAt(offset.x, offset.y) }
        },
    )

    LaunchedEffect(renderer) {
        renderer.loadPack(PackId("phase0-toy"), MeshSource("file://${Phase0ToyAsset.path}"))
    }

    // Filament paces against the vsync timestamp, so frames have to be driven by
    // Choreographer rather than by a timer. Given any other clock reading it refuses
    // every frame after the first, and the surface stays black.
    DisposableEffect(renderer) {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                renderer.renderFrame(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    LaunchedEffect(renderer) {
        renderer.events.filterIsInstance<RendererEvent.Picked>().collect {
            currentOnPicked(it.structure)
        }
    }
}

package com.ptk.anatomypro

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ptk.anatomypro.core.designsystem.HighlightTokens
import com.ptk.anatomypro.core.model.PackId
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.scene.FocusRequest
import com.ptk.anatomypro.feature.atlas.scene.RenderState
import com.ptk.anatomypro.feature.atlas.scene.applyRenderState
import com.ptk.anatomypro.renderer.api.MeshSource
import com.ptk.anatomypro.renderer.api.RendererEvent
import com.ptk.anatomypro.renderer.filament.FilamentAnatomyRenderer
import com.ptk.anatomypro.renderer.filament.Phase0ToyAsset
import kotlinx.coroutines.flow.filterIsInstance
import java.io.File

/** Where the harness gets its geometry, and what to call it on screen. */
private data class HarnessPack(val id: PackId, val source: MeshSource, val label: String)

/** The pipeline's output, staged into the APK by `:androidApp` when it exists. */
private const val BUNDLED_PACK = "packs/phase0-pack.glb"
private const val BUNDLED_PACK_ID = "packs/phase0-pack.id"

/**
 * Hosts Filament in a `SurfaceView` and pumps it from Choreographer.
 *
 * The same shape as the iOS host: the platform owns the surface, the caller owns the
 * frame loop, and the renderer keeps no thread of its own (spec §4, §4.1).
 */
@Composable
actual fun AnatomyCanvas(
    modifier: Modifier,
    highlighted: StructureId?,
    render: RenderState,
    focus: FocusRequest?,
    onPicked: (StructureId?) -> Unit,
    onStats: (CanvasStats) -> Unit,
) {
    val context = LocalContext.current
    val renderer = remember { FilamentAnatomyRenderer() }
    val pack = remember(context) { resolvePack(context) }
    // Filament paces against this. Reading it from the display rather than assuming 60
    // is the difference between measuring the renderer and measuring the assumption.
    val refreshHz = remember(context) {
        context.display?.refreshRate?.takeIf { it > 0f } ?: 60f
    }
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnStats by rememberUpdatedState(onStats)

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        renderer.attachSurface(holder.surface, width, height, refreshHz)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
            }
        },
        modifier = modifier.pointerInput(renderer) {
            detectTapGestures { offset -> renderer.pickAt(offset.x, offset.y) }
        },
    )

    LaunchedEffect(renderer, pack) {
        renderer.loadPack(pack.id, pack.source)
    }

    // Filament paces against the vsync timestamp, so frames have to be driven by
    // Choreographer rather than by a timer. Given any other clock reading it refuses
    // every frame after the first, and the surface stays black.
    DisposableEffect(renderer, pack) {
        val choreographer = Choreographer.getInstance()
        var framesThisSecond = 0
        var windowStartNanos = 0L
        // removeFrameCallback is not enough on its own. Compose disposes effects while
        // Choreographer is running a frame, and a callback already taken off the queue for
        // that frame still runs after removal — against a renderer the other effect has just
        // destroyed. Leaving the atlas for another destination crashed this way.
        var active = true

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (renderer.renderFrame(frameTimeNanos)) framesThisSecond++

                if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
                val elapsed = frameTimeNanos - windowStartNanos
                if (elapsed >= NANOS_PER_SECOND) {
                    val fps = (framesThisSecond * NANOS_PER_SECOND / elapsed).toInt()
                    // One screenshot is a sample, not a measurement. Logging each second
                    // lets a run be reduced to a median instead of an anecdote.
                    android.util.Log.i(
                        "AnatomyPerf",
                        "pack=${pack.label} structures=${renderer.loadedStructureCount} " +
                            "fps=$fps gpuMs=${renderer.gpuFrameMillis}",
                    )
                    currentOnStats(
                        CanvasStats(
                            fps = (framesThisSecond * NANOS_PER_SECOND / elapsed).toInt(),
                            structures = renderer.loadedStructureCount,
                            pack = pack.label,
                            gpuMillis = renderer.gpuFrameMillis,
                            refreshHz = refreshHz.toInt(),
                        )
                    )
                    framesThisSecond = 0
                    windowStartNanos = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }

    LaunchedEffect(renderer) {
        renderer.events.filterIsInstance<RendererEvent.Picked>().collect {
            currentOnPicked(it.structure)
        }
    }

    // The renderer resolves structures to nodes from the loaded pack, so nothing can be
    // hidden, ghosted or framed before it arrives; a state sent early would be dropped.
    var packLoaded by remember(renderer) { mutableStateOf(false) }
    val applied = remember(renderer) { arrayOf(RenderState.None) }

    LaunchedEffect(renderer) {
        renderer.events.filterIsInstance<RendererEvent.PackLoaded>().collect {
            applied[0] = RenderState.None
            packLoaded = true
        }
    }

    LaunchedEffect(renderer, render, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        applyRenderState(renderer, applied[0], render)
        applied[0] = render
    }

    LaunchedEffect(renderer, focus, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        focus?.let { request ->
            val structure = request.structure
            if (structure == null) renderer.frameAll(request.durationMs) else renderer.focusCamera(structure, request.durationMs)
        }
    }

    LaunchedEffect(renderer, highlighted) {
        renderer.highlight(setOfNotNull(highlighted), HighlightTokens.Selected)
    }
}

/**
 * Prefers the pipeline's real pack, falling back to the toy.
 *
 * The generated pack is 22 MB and lives under `pipeline/build`, which is not committed —
 * so a checkout that has never run the pipeline still gets a harness that draws something
 * rather than an empty screen.
 */
private fun resolvePack(context: Context): HarnessPack = runCatching {
    val id = context.assets.open(BUNDLED_PACK_ID).use { it.readBytes().decodeToString().trim() }
    val target = File(context.cacheDir, "$id.glb")
    if (!target.isFile || target.length() == 0L) {
        context.assets.open(BUNDLED_PACK).use { input ->
            target.outputStream().use(input::copyTo)
        }
    }
    HarnessPack(
        id = PackId(id),
        source = MeshSource("file://${target.absolutePath}"),
        label = id,
    )
}.getOrElse {
    HarnessPack(
        id = PackId("phase0-toy"),
        source = MeshSource("file://${Phase0ToyAsset.path}"),
        label = "phase0-toy (no pipeline output bundled)",
    )
}

private const val NANOS_PER_SECOND = 1_000_000_000L

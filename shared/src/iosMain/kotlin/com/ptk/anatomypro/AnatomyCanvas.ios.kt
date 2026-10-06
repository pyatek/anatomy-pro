package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.ptk.anatomypro.core.model.StructureId
import com.ptk.anatomypro.feature.atlas.scene.FocusRequest
import com.ptk.anatomypro.feature.atlas.scene.RenderState
import com.ptk.anatomypro.feature.atlas.scene.applyRenderState
import com.ptk.anatomypro.renderer.api.HighlightStyle
import com.ptk.anatomypro.renderer.api.RendererEvent
import com.ptk.anatomypro.renderer.filament.FilamentAnatomyRenderer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import kotlinx.coroutines.flow.filterIsInstance
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSLog
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAMetalLayer
import platform.darwin.NSObject
import platform.UIKit.UIScreen
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import kotlin.math.roundToInt

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
    highlights: Map<StructureId, HighlightStyle>,
    render: RenderState,
    focus: FocusRequest?,
    onPicked: (StructureId?) -> Unit,
    onStats: (CanvasStats) -> Unit,
) {
    val scale = UIScreen.mainScreen.scale
    val refreshHz = UIScreen.mainScreen.maximumFramesPerSecond.toFloat()
    val renderer = remember { FilamentAnatomyRenderer() }
    val pack = remember { resolveBundledPack() }
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnStats by rememberUpdatedState(onStats)

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    UIKitView(
        factory = {
            MetalHostView(
                scale = scale,
                onSized = { layer, widthPx, heightPx ->
                    renderer.attachLayer(layer, widthPx, heightPx, refreshHz)
                },
                onTapped = { xPx, yPx -> renderer.pickAt(xPx, yPx) },
            )
        },
        modifier = modifier,
    )

    LaunchedEffect(renderer, pack) {
        renderer.loadPack(pack.id, pack.mesh)
    }

    // Filament paces against the vsync timestamp, so frames come from a display link
    // rather than a timer. This is the counterpart of Choreographer on Android, where a
    // plain clock reading made Filament render one frame and then refuse every other.
    //
    // UNVERIFIED ON DEVICE: the Android failure is the evidence for this shape, not a
    // reproduction here. Nothing has run the iOS on-screen path on real hardware.
    DisposableEffect(renderer, pack) {
        var framesThisSecond = 0
        var windowStart = 0.0
        // Pacing, as opposed to throughput: how often the link fired, the longest it went
        // between two firings, and how many of those Filament declined to draw. A frame
        // rate alone cannot tell a dropped vsync from a refused frame.
        var callbacks = 0
        var refused = 0
        var previous = 0.0
        var longestGap = 0.0

        val driver = FrameDriver { seconds, granted ->
            callbacks++
            if (previous != 0.0 && seconds - previous > longestGap) longestGap = seconds - previous
            previous = seconds

            if (renderer.renderFrame((seconds * NANOS_PER_SECOND).toLong())) framesThisSecond++ else refused++

            if (windowStart == 0.0) windowStart = seconds
            val elapsed = seconds - windowStart
            if (elapsed >= 1.0) {
                val fps = (framesThisSecond / elapsed).toInt()
                // One screenshot is a sample, not a measurement. Logging each second
                // lets a run be reduced to a median instead of an anecdote.
                NSLog(
                    "AnatomyPerf pack=${pack.label} structures=${renderer.loadedStructureCount} " +
                        "fps=$fps gpuMs=${renderer.gpuFrameMillis.oneDecimal()} " +
                        "refreshHz=${refreshHz.toInt()} linkHz=${(callbacks / elapsed).roundToInt()} " +
                        "grantedHz=${if (granted > 0.0) (1.0 / granted).roundToInt() else 0} " +
                        "maxGapMs=${(longestGap * 1000.0).oneDecimal()} refused=$refused " +
                        "residentMb=${renderer.residentBytes / BYTES_PER_MB} " +
                        "footprintMb=${renderer.footprintBytes / BYTES_PER_MB}"
                )
                currentOnStats(
                    CanvasStats(
                        fps = fps,
                        structures = renderer.loadedStructureCount,
                        pack = pack.label,
                        gpuMillis = renderer.gpuFrameMillis,
                        refreshHz = refreshHz.toInt(),
                    )
                )
                framesThisSecond = 0
                windowStart = seconds
                callbacks = 0
                refused = 0
                longestGap = 0.0
            }
        }
        val link = CADisplayLink.displayLinkWithTarget(driver, NSSelectorFromString("step:"))
        link.addToRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
        onDispose { link.invalidate() }
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

    // Keyed on the map's contents: an equal map built afresh on recomposition is not a change.
    LaunchedEffect(renderer, highlights, packLoaded) {
        if (!packLoaded) return@LaunchedEffect
        renderer.highlight(highlights)
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val BYTES_PER_MB = 1024L * 1024L

private fun Float.oneDecimal(): Double = (this * 10f).roundToInt() / 10.0

private fun Double.oneDecimal(): Double = (this * 10.0).roundToInt() / 10.0

/**
 * A view whose only content is the `CAMetalLayer` Filament draws into.
 *
 * The layer is sized in `layoutSubviews` because that is the one place UIKit reports the
 * view's real size. `UIKitView`'s `onResize` used to do this and is now a no-op that only
 * logs a warning — so the layer was never attached, no swap chain existed, and every frame
 * was refused while the canvas stayed blank.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class MetalHostView(
    private val scale: Double,
    private val onSized: (layer: CAMetalLayer, widthPx: Int, heightPx: Int) -> Unit,
    private val onTapped: (xPx: Float, yPx: Float) -> Unit,
) : UIView(frame = CGRectZero.readValue()) {

    private val metalLayer = CAMetalLayer().also {
        it.contentsScale = scale
        layer.addSublayer(it)
    }
    private var attachedWidth = 0
    private var attachedHeight = 0

    // Taps are recognised here, in UIKit. An interop view takes its own touches, so a
    // Compose pointerInput on the UIKitView never saw one and nothing could be picked.
    init {
        addGestureRecognizer(UITapGestureRecognizer(target = this, action = NSSelectorFromString("tapped:")))
    }

    @ObjCAction
    fun tapped(sender: UITapGestureRecognizer) {
        sender.locationInView(this).useContents { onTapped((x * scale).toFloat(), (y * scale).toFloat()) }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        metalLayer.setFrame(bounds)
        val (widthPx, heightPx) = bounds.useContents {
            (size.width * scale).toInt() to (size.height * scale).toInt()
        }
        // Layout runs far more often than the size changes, and each attach replaces the
        // swap chain.
        if (widthPx <= 0 || heightPx <= 0) return
        if (widthPx == attachedWidth && heightPx == attachedHeight) return
        attachedWidth = widthPx
        attachedHeight = heightPx
        onSized(metalLayer, widthPx, heightPx)
    }
}

/**
 * Receives display-link callbacks.
 *
 * `CADisplayLink` dispatches through a target and selector, so this has to be a real
 * Objective-C object rather than a Kotlin lambda.
 */
private class FrameDriver(private val onFrame: (timestamp: Double, granted: Double) -> Unit) : NSObject() {

    @kotlinx.cinterop.ObjCAction
    fun step(sender: CADisplayLink) {
        // The gap to the frame's target is the interval the system actually granted, which
        // on a ProMotion panel need not be the panel's maximum.
        onFrame(sender.timestamp, sender.targetTimestamp - sender.timestamp)
    }
}

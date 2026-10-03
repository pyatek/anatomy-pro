package com.ptk.anatomypro.renderer.filament

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** A point or direction in world space. */
internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)
    fun length(): Float = sqrt(x * x + y * y + z * z)
}

private fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

/** An axis-aligned box in world space, described the way Filament describes one. */
internal data class WorldBox(val center: Vec3, val halfExtent: Vec3) {
    val lower: Vec3 get() = center - halfExtent
    val upper: Vec3 get() = center + halfExtent

    fun union(other: WorldBox): WorldBox = fromCorners(
        Vec3(min(lower.x, other.lower.x), min(lower.y, other.lower.y), min(lower.z, other.lower.z)),
        Vec3(max(upper.x, other.upper.x), max(upper.y, other.upper.y), max(upper.z, other.upper.z)),
    )

    companion object {
        fun fromCorners(lower: Vec3, upper: Vec3) = WorldBox((lower + upper) * 0.5f, (upper - lower) * 0.5f)

        /**
         * [local] moved into world space by the column-major 4×4 [m] that
         * `TransformManager.getWorldTransform` returns. The same result as Filament's C++
         * `rigidTransform`: the centre goes through the matrix, the half-extent through |M|.
         */
        fun transformed(local: WorldBox, m: FloatArray): WorldBox {
            require(m.size == 16) { "expected a 4x4 matrix, got ${m.size} values" }
            fun at(row: Int, col: Int) = m[col * 4 + row]
            val c = local.center
            val h = local.halfExtent
            return WorldBox(
                center = Vec3(
                    at(0, 0) * c.x + at(0, 1) * c.y + at(0, 2) * c.z + at(0, 3),
                    at(1, 0) * c.x + at(1, 1) * c.y + at(1, 2) * c.z + at(1, 3),
                    at(2, 0) * c.x + at(2, 1) * c.y + at(2, 2) * c.z + at(2, 3),
                ),
                halfExtent = Vec3(
                    abs(at(0, 0)) * h.x + abs(at(0, 1)) * h.y + abs(at(0, 2)) * h.z,
                    abs(at(1, 0)) * h.x + abs(at(1, 1)) * h.y + abs(at(1, 2)) * h.z,
                    abs(at(2, 0)) * h.x + abs(at(2, 1)) * h.y + abs(at(2, 2)) * h.z,
                ),
            )
        }
    }
}

/** Where the camera is, what it looks at, and its clip planes. */
internal data class CameraShot(val eye: Vec3, val target: Vec3, val near: Double, val far: Double)

internal object CameraFraming {
    const val FOV_DEGREES = 45.0

    /**
     * The shot that frames [box] from in front, looking down -Z.
     *
     * Identical to `frameAsset` on both platforms, so focusing on the whole model and
     * loading it produce the same view.
     */
    fun frame(box: WorldBox): CameraShot {
        val radius = box.halfExtent.length()
        val distance = if (radius <= 0f) 1f else (radius / tan(FOV_DEGREES / 2 * PI / 180) * 1.6).toFloat()
        return CameraShot(
            eye = box.center + Vec3(0f, 0f, distance),
            target = box.center,
            near = distance * 0.01,
            far = distance * 10.0,
        )
    }
}

/**
 * An eased move between two shots, driven by frame timestamps.
 *
 * The clock starts at the first frame that asks, because the caller of `focusCamera` has
 * no frame time. A time of zero is what headless rendering passes — it carries no clock —
 * so it lands at once, which is what lets contract tests assert on a focused camera.
 */
internal class CameraFlight(
    private val from: CameraShot,
    private val to: CameraShot,
    private val durationNanos: Long,
) {
    private var startNanos: Long? = null

    var finished: Boolean = false
        private set

    fun at(nowNanos: Long): CameraShot {
        if (durationNanos <= 0L || nowNanos <= 0L) return land()
        val start = startNanos ?: nowNanos.also { startNanos = it }
        val t = ((nowNanos - start).toDouble() / durationNanos).coerceIn(0.0, 1.0)
        if (t >= 1.0) return land()
        val eased = (t * t * (3 - 2 * t)).toFloat()
        // The planes span both shots during the move, so nothing is clipped half way.
        return CameraShot(
            eye = lerp(from.eye, to.eye, eased),
            target = lerp(from.target, to.target, eased),
            near = minOf(from.near, to.near),
            far = maxOf(from.far, to.far),
        )
    }

    private fun land(): CameraShot {
        finished = true
        return to
    }
}

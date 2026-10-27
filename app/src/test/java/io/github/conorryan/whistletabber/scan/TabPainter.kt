package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.Hole
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws whistle tab charts for tests: dark shapes on white paper, rendered
 * [supersample] times larger and averaged down, so edges are anti-aliased
 * like real screenshots. Coordinates are output pixels.
 */
class TabPainter(val width: Int, val height: Int, private val supersample: Int = 4) {
    private val w = width * supersample
    private val h = height * supersample
    private val ink = FloatArray(w * h)

    private fun paint(x0: Double, y0: Double, x1: Double, y1: Double, darkness: Float, inside: (Double, Double) -> Boolean) {
        val s = supersample
        for (y in (y0 * s).toInt().coerceAtLeast(0)..(y1 * s).toInt().coerceAtMost(h - 1)) {
            for (x in (x0 * s).toInt().coerceAtLeast(0)..(x1 * s).toInt().coerceAtMost(w - 1)) {
                if (inside((x + 0.5) / s, (y + 0.5) / s)) ink[y * w + x] = maxOf(ink[y * w + x], darkness)
            }
        }
    }

    private fun dist(x: Double, y: Double, cx: Double, cy: Double) = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))

    fun disc(cx: Double, cy: Double, r: Double, darkness: Float = 1f) =
        paint(cx - r, cy - r, cx + r, cy + r, darkness) { x, y -> dist(x, y, cx, cy) <= r }

    fun ring(cx: Double, cy: Double, r: Double, thickness: Double, darkness: Float = 1f) =
        paint(cx - r, cy - r, cx + r, cy + r, darkness) { x, y -> dist(x, y, cx, cy).let { it <= r && it >= r - thickness } }

    /** A ring with its left half filled. */
    fun halfDisc(cx: Double, cy: Double, r: Double, thickness: Double, darkness: Float = 1f) {
        ring(cx, cy, r, thickness, darkness)
        paint(cx - r, cy - r, cx, cy + r, darkness) { x, y -> x <= cx && dist(x, y, cx, cy) <= r }
    }

    fun plus(cx: Double, cy: Double, arm: Double, thickness: Double, darkness: Float = 1f) =
        paint(cx - arm, cy - arm, cx + arm, cy + arm, darkness) { x, y ->
            (kotlin.math.abs(x - cx) <= thickness / 2) || (kotlin.math.abs(y - cy) <= thickness / 2)
        }

    fun rect(x0: Double, y0: Double, x1: Double, y1: Double, darkness: Float = 1f) =
        paint(x0, y0, x1, y1, darkness) { _, _ -> true }

    /**
     * One diagram: [notation] in chart form ("XXO OOO+", "XX½ OOO"), holes of
     * radius [r] every [pitch] pixels down from [top], "+" marks side by side below.
     */
    fun column(
        x: Double,
        top: Double,
        notation: String,
        r: Double,
        pitch: Double = r * 2.6,
        ringThickness: Double = maxOf(1.0, r / 6),
        ringDarkness: Float = 1f,
    ) {
        val f = Fingering.parse(notation)
        f.holes.forEachIndexed { i, hole ->
            val cy = top + r + i * pitch
            when (hole) {
                Hole.CLOSED -> disc(x, cy, r)
                Hole.OPEN -> ring(x, cy, r, ringThickness, ringDarkness)
                Hole.HALF -> halfDisc(x, cy, r, ringThickness)
            }
        }
        val plusY = top + 2 * r + 5 * pitch + r * 0.9
        val arm = r * 0.6
        repeat(f.octave) { i ->
            val offset = (i - (f.octave - 1) / 2.0) * arm * 2.6
            plus(x + offset, plusY, arm, maxOf(1.0, r / 5))
        }
    }

    /** Averages the supersampled ink down to output pixels, then box-blurs [blur] pixels each way. */
    fun image(blur: Int = 0): GrayImage {
        val s = supersample
        var out = FloatArray(width * height) { i ->
            val ox = i % width
            val oy = i / width
            var sum = 0f
            for (y in 0 until s) for (x in 0 until s) sum += ink[(oy * s + y) * w + ox * s + x]
            sum / (s * s)
        }
        repeat(blur) { out = boxBlur(out) }
        return GrayImage(width, height, IntArray(out.size) { (255 * (1 - out[it])).roundToInt().coerceIn(0, 255) })
    }

    private fun boxBlur(src: FloatArray): FloatArray = FloatArray(src.size) { i ->
        val x = i % width
        val y = i / width
        var sum = 0f
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) { sum += src[ny * width + nx]; n++ }
        }
        sum / n
    }
}

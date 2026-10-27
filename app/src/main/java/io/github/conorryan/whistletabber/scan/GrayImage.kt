package io.github.conorryan.whistletabber.scan

/**
 * A greyscale picture: [luma] holds one value per pixel, row by row,
 * 0 black to 255 white. Kept free of Android types so the reader is unit-testable.
 */
class GrayImage(val width: Int, val height: Int, val luma: IntArray) {
    init {
        require(width > 0 && height > 0) { "Empty image ${width}x$height" }
        require(luma.size == width * height) { "Expected ${width * height} pixels, got ${luma.size}" }
    }

    operator fun get(x: Int, y: Int): Int = luma[y * width + x]

    /** Black for white: dark-mode screenshots become dark-on-light. */
    fun inverted(): GrayImage = GrayImage(width, height, IntArray(luma.size) { 255 - luma[it] })

    /**
     * Enlarges to [newWidth] × [newHeight] with bicubic interpolation (a = −0.5),
     * one axis at a time. Bicubic keeps thin rings closed where bilinear breaks them.
     */
    fun scaled(newWidth: Int, newHeight: Int): GrayImage {
        val rows = FloatArray(newWidth * height)
        for (y in 0 until height) {
            resample(width, newWidth, { this[it, y].toFloat() }) { x, v -> rows[y * newWidth + x] = v }
        }
        val out = IntArray(newWidth * newHeight)
        for (x in 0 until newWidth) {
            resample(height, newHeight, { rows[it * newWidth + x] }) { y, v ->
                out[y * newWidth + x] = (v + 0.5f).toInt().coerceIn(0, 255)
            }
        }
        return GrayImage(newWidth, newHeight, out)
    }

    private inline fun resample(from: Int, to: Int, sample: (Int) -> Float, write: (Int, Float) -> Unit) {
        val step = from.toFloat() / to
        for (i in 0 until to) {
            val f = (i + 0.5f) * step - 0.5f
            val base = kotlin.math.floor(f).toInt()
            var sum = 0f
            var weights = 0f
            for (k in -1..2) {
                val w = cubic(f - (base + k))
                sum += w * sample((base + k).coerceIn(0, from - 1))
                weights += w
            }
            write(i, sum / weights)
        }
    }

    private fun cubic(t: Float): Float {
        val x = kotlin.math.abs(t)
        return when {
            x <= 1f -> (1.5f * x - 2.5f) * x * x + 1f
            x < 2f -> ((-0.5f * x + 2.5f) * x - 4f) * x + 2f
            else -> 0f
        }
    }

    companion object {
        /** Luma of an ARGB pixel composited over white, so transparent backgrounds read as paper. */
        fun lumaOf(argb: Int): Int {
            val a = argb ushr 24 and 0xFF
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            return (l * a + 255 * (255 - a)) / 255
        }

        fun fromArgb(width: Int, height: Int, argb: IntArray): GrayImage =
            GrayImage(width, height, IntArray(argb.size) { lumaOf(argb[it]) })
    }
}

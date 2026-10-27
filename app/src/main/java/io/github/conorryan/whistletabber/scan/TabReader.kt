package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.Hole
import kotlin.math.abs
import kotlin.math.roundToInt

/** A rectangle in the source image's pixels, edges inclusive. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * One fingering diagram found in an image: six holes, top first, and the
 * number of "+" marks under it.
 */
data class TabColumn(val holes: List<Hole>, val octave: Int, val box: Box)

/** The diagrams in reading order: lines top to bottom, each left to right. */
data class TabReading(val lines: List<List<TabColumn>>) {
    val columns: List<TabColumn> get() = lines.flatten()
}

/**
 * Reads whistle tab images: every vertical run of six evenly spaced holes is
 * one note, filled, open or half-covered, with "+" marks below for the
 * octave. Staff notation, lyrics and anything else in the image are ignored.
 *
 * Two readers run and the one finding more diagrams wins:
 * - hole by hole: round blobs at several ink thresholds (thin grey rings only
 *   survive a loose one; packed discs only separate at a strict one), linked
 *   into columns. Handles half holes and separate "+" marks.
 * - column first, for small or blurry images where a column's holes merge
 *   into one tall thin blob: cut it into six cells and compare their centres.
 * Filled and open are told apart per image, not by a fixed level, so grey,
 * black and anti-aliased charts all work.
 */
object TabReader {

    fun read(source: GrayImage): TabReading {
        // Dark mode: light holes on dark paper read the same once inverted.
        val image = if (median(source.luma) < 128) source.inverted() else source
        val byHoles = readHoles(image)
        val byColumns = readColumns(image)
        val columns = if (byColumns.size > byHoles.size) byColumns else byHoles
        return TabReading(lines(columns))
    }

    // --- Shared ---

    private class Blob(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val area: Int) {
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val cx = (x0 + x1) / 2.0
        val cy = (y0 + y1) / 2.0
        val fill = area.toDouble() / (w * h)

        fun contains(x: Double, y: Double) = x in x0.toDouble()..x1.toDouble() && y in y0.toDouble()..y1.toDouble()
    }

    /** Ink per pixel, 0 for the paper (the median) to 1 for the darkest pixel. */
    private class Ink(val image: GrayImage) {
        val width = image.width
        val height = image.height
        private val paper = median(image.luma)
        private val range = maxOf(1, paper - (image.luma.minOrNull() ?: paper))

        operator fun get(x: Int, y: Int): Double = (paper - image[x, y]).toDouble() / range

        fun mask(level: Double) = BooleanArray(width * height) { (paper - image.luma[it]).toDouble() / range > level }
    }

    private fun median(values: IntArray): Int {
        val histogram = IntArray(256)
        values.forEach { histogram[it.coerceIn(0, 255)]++ }
        var seen = 0
        for (v in 0..255) {
            seen += histogram[v]
            if (seen * 2 >= values.size) return v
        }
        return 255
    }

    /** 8-connected blobs of the true pixels in [mask]. */
    private fun blobs(mask: BooleanArray, width: Int, height: Int): List<Blob> {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val out = mutableListOf<Blob>()
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var x0 = width; var y0 = height; var x1 = -1; var y1 = -1; var area = 0
            while (top > 0) {
                val p = stack[--top]
                val x = p % width
                val y = p / width
                area++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val q = ny * width + nx
                    if (mask[q] && !seen[q]) {
                        seen[q] = true
                        stack[top++] = q
                    }
                }
            }
            out += Blob(x0, y0, x1, y1, area)
        }
        return out
    }

    /** The middle of the widest gap between sorted values: open and filled holes form two clusters. */
    private fun splitPoint(values: List<Double>): Double {
        val v = values.sorted()
        if (v.size < 2 || v.last() - v.first() < 0.3) return 0.5
        return v.zipWithNext().maxBy { (a, b) -> b - a }.let { (a, b) -> (a + b) / 2 }
    }

    /** Groups columns into lines (overlapping vertical spans), top to bottom, each left to right. */
    private fun lines(columns: List<TabColumn>): List<List<TabColumn>> {
        val lines = mutableListOf<Pair<IntRange, MutableList<TabColumn>>>()
        for (c in columns.sortedBy { it.box.top }) {
            val i = lines.indexOfFirst { (span, _) -> c.box.top < span.last && c.box.bottom > span.first }
            if (i >= 0) {
                val (span, cols) = lines[i]
                cols += c
                lines[i] = (span.first..maxOf(span.last, c.box.bottom)) to cols
            } else {
                lines += (c.box.top..c.box.bottom) to mutableListOf(c)
            }
        }
        return lines.map { (_, cols) -> cols.sortedBy { it.box.left + it.box.right } }
    }

    // --- Hole by hole ---

    private const val MIN_WIDTH = 1000
    private val LEVELS = listOf(0.15, 0.25, 0.5, 0.7)

    private class HoleColumn(val holes: List<Blob>) {
        val x = holes.sumOf { it.cx } / holes.size
        val top = holes.first().y0
        val bottom = holes.last().y1
        val size = holes.sumOf { it.w }.toDouble() / holes.size
        var octave = 0
    }

    private fun readHoles(source: GrayImage): List<TabColumn> {
        // Small images are upscaled so thin rings and small "+" marks keep their shape.
        val scale = if (source.width < MIN_WIDTH) MIN_WIDTH.toDouble() / source.width else 1.0
        val image = if (scale > 1) source.scaled(MIN_WIDTH, (source.height * scale).roundToInt()) else source
        val ink = Ink(image)
        val holes = mutableListOf<Blob>()
        val pluses = mutableListOf<Blob>()
        for (level in LEVELS) {
            val mask = ink.mask(level)
            for (b in blobs(mask, image.width, image.height)) {
                if (isPlus(b, mask, image.width)) pluses += b
                else if (isHole(b)) holes += b
            }
        }
        // A "+" that blurs into a ring-like blob at a loose threshold is still a "+".
        val marks = dedupe(pluses)
        val columns = chains(dedupe(holes.filter { h -> marks.none { it.contains(h.cx, h.cy) } }))
            .filter { it.size == 6 }
            .map(::HoleColumn)
        attachPluses(columns, marks)

        val halves = columns.associateWith { col -> col.holes.map { halves(ink, it) } }
        val split = splitPoint(halves.values.flatMap { h -> h.flatMap { listOf(it.first, it.second) } })
        return columns.map { col ->
            TabColumn(
                holes = halves.getValue(col).map { (left, right) -> classify(left, right, split) },
                octave = col.octave.coerceAtMost(2),
                box = Box(
                    (col.holes.minOf { it.x0 } / scale).toInt(),
                    (col.top / scale).toInt(),
                    (col.holes.maxOf { it.x1 } / scale).roundToInt(),
                    (col.bottom / scale).roundToInt(),
                ),
            )
        }
    }

    private fun isRound(b: Blob) = b.w >= 4 && b.h >= 4 && b.w.toDouble() / b.h in 0.75..1.33

    private fun isHole(b: Blob) = isRound(b) && b.fill in 0.2..0.9

    /**
     * A "+": empty corners and an unbroken bar across the middle each way
     * (an "A" in a chord symbol has the first but not the second). Checked
     * first, so a "+" is never a ring.
     */
    private fun isPlus(b: Blob, mask: BooleanArray, width: Int): Boolean {
        if (!isRound(b) || b.fill >= 0.5) return false
        fun dark(x: Int, y: Int) = mask[y * width + x]
        val corners = dark(b.x0, b.y0) || dark(b.x1, b.y0) || dark(b.x0, b.y1) || dark(b.x1, b.y1)
        if (corners) return false
        val cx = b.cx.roundToInt()
        val cy = b.cy.roundToInt()
        val across = (cy - 1..cy + 1).filter { it in b.y0..b.y1 }.maxOf { y -> (b.x0..b.x1).count { dark(it, y) } }
        val down = (cx - 1..cx + 1).filter { it in b.x0..b.x1 }.maxOf { x -> (b.y0..b.y1).count { dark(x, it) } }
        return across >= 0.8 * b.w && down >= 0.8 * b.h
    }

    /**
     * Drops blobs that are several neighbours merged at a loose threshold
     * (they contain two or more hole-sized blobs; the fragments of a thin
     * ring at a strict threshold are too small to count), then keeps one blob
     * per position.
     */
    private fun dedupe(blobs: List<Blob>): List<Blob> {
        val unmerged = blobs.filter { q ->
            blobs.count { it !== q && it.w < 0.75 * q.w && it.w > 0.35 * q.w && q.contains(it.cx, it.cy) } < 2
        }
        val out = mutableListOf<Blob>()
        for (b in unmerged) if (out.none { it.contains(b.cx, b.cy) }) out += b
        return out
    }

    /** Links each hole to the nearest similar one below it, then splits runs where the spacing changes. */
    private fun chains(holes: List<Blob>): List<List<Blob>> {
        val sorted = holes.sortedBy { it.cy }
        val next = HashMap<Int, Int>()
        val hasPrevious = HashSet<Int>()
        for (i in sorted.indices) {
            val a = sorted[i]
            val size = (a.w + a.h) / 2.0
            var best = -1
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                val dy = b.cy - a.cy
                if (dy > 2.5 * size) break
                if (dy < 0.8 * size || abs(b.cx - a.cx) > 0.35 * size || abs(b.w - a.w) > 0.3 * size) continue
                if (best < 0 || dy < sorted[best].cy - a.cy) best = j
            }
            if (best >= 0 && best !in hasPrevious) {
                next[i] = best
                hasPrevious += best
            }
        }
        val runs = mutableListOf<List<Blob>>()
        for (i in sorted.indices) {
            if (i in hasPrevious) continue
            val run = mutableListOf(sorted[i])
            var k = i
            while (true) {
                k = next[k] ?: break
                run += sorted[k]
            }
            runs += evenRuns(run)
        }
        return runs
    }

    /**
     * Splits a chain where the spacing changes. Many charts draw the holes as
     * two groups of three, so one wider gap after the third hole is allowed.
     */
    private fun evenRuns(run: List<Blob>): List<List<Blob>> {
        val out = mutableListOf<List<Blob>>()
        var current = mutableListOf(run.first())
        var pitch = 0.0
        for ((a, b) in run.zipWithNext()) {
            val gap = b.cy - a.cy
            val even = current.size == 1 || abs(gap - pitch) <= 0.3 * pitch
            val groupGap = current.size == 3 && gap > pitch && gap <= 2.2 * pitch
            when {
                current.size == 1 -> { pitch = gap; current += b }
                even || groupGap -> current += b
                else -> {
                    out += current
                    current = mutableListOf(b)
                }
            }
        }
        out += current
        return out
    }

    /** Each "+" counts toward the column it sits just under. */
    private fun attachPluses(columns: List<HoleColumn>, pluses: List<Blob>) {
        for (p in pluses) {
            columns
                .filter { col ->
                    val dy = p.cy - col.bottom
                    dy > 0 && dy < 2 * col.size && abs(p.cx - col.x) < 0.7 * col.size
                }
                .minByOrNull { abs(p.cx - it.x) }
                ?.let { it.octave++ }
        }
    }

    /** Mean ink in the left and right halves of the hole's inner disc. */
    private fun halves(ink: Ink, b: Blob): Pair<Double, Double> {
        val r = minOf(b.w, b.h) / 2.0 * 0.55
        var left = 0.0; var leftCount = 0
        var right = 0.0; var rightCount = 0
        for (y in b.y0..b.y1) for (x in b.x0..b.x1) {
            val dx = x - b.cx
            val dy = y - b.cy
            if (dx * dx + dy * dy > r * r) continue
            if (dx < 0) { left += ink[x, y]; leftCount++ }
            if (dx > 0) { right += ink[x, y]; rightCount++ }
        }
        if (leftCount == 0 || rightCount == 0) return 0.0 to 0.0
        return left / leftCount to right / rightCount
    }

    private fun classify(left: Double, right: Double, split: Double): Hole = when {
        left > split && right > split -> Hole.CLOSED
        left <= split && right <= split -> Hole.OPEN
        else -> Hole.HALF
    }

    // --- Column first ---

    private const val COLUMN_LEVEL = 0.12

    private fun readColumns(image: GrayImage): List<TabColumn> {
        val ink = Ink(image)
        val candidates = blobs(ink.mask(COLUMN_LEVEL), image.width, image.height)
            .filter { it.w in 3..12 && it.h.toDouble() / it.w in 4.0..8.5 }
        if (candidates.isEmpty()) return emptyList()
        // A "+" blurred into the bottom of a column makes it taller than the rest:
        // read the top part as the six holes and count one octave.
        val usual = candidates.map { it.h }.sorted()[candidates.size / 2]
        val cells = candidates.map { b ->
            val plus = b.h > usual + usual / 12.0
            val height = if (plus) usual.toDouble() else b.h.toDouble()
            Triple(b, plus, (0 until 6).map { i -> cellInk(ink, b, b.y0 + height / 6 * (i + 0.5), height / 6) })
        }
        val split = splitPoint(cells.flatMap { it.third })
        return cells.map { (b, plus, values) ->
            TabColumn(
                holes = values.map { if (it > split) Hole.CLOSED else Hole.OPEN },
                octave = if (plus) 1 else 0,
                box = Box(b.x0, b.y0, b.x1, b.y1),
            )
        }
    }

    private fun cellInk(ink: Ink, b: Blob, centreY: Double, cell: Double): Double {
        val ys = (centreY - cell * 0.2).roundToInt()..(centreY + cell * 0.2).roundToInt()
        val xs = (b.cx - b.w * 0.15).roundToInt()..(b.cx + b.w * 0.15).roundToInt()
        var sum = 0.0
        var n = 0
        for (y in ys) for (x in xs) {
            if (x in 0 until ink.width && y in 0 until ink.height) { sum += ink[x, y]; n++ }
        }
        return if (n == 0) 0.0 else sum / n
    }
}

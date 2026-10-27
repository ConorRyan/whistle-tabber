package io.github.conorryan.whistletabber.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabReaderTest {

    private fun notations(reading: TabReading): List<List<String>> =
        reading.lines.map { line -> line.map { c -> notation(c) } }

    private fun notation(c: TabColumn) =
        c.holes.take(3).joinToString("") { it.symbol.toString() } + " " +
            c.holes.drop(3).joinToString("") { it.symbol.toString() } + "+".repeat(c.octave)

    /** Two lines of tab, [r]-pixel holes, columns [gap] apart. */
    private fun chart(
        lines: List<List<String>>,
        r: Double,
        gap: Double = r * 4,
        width: Int = 1100,
        ringDarkness: Float = 1f,
        ringThickness: Double = maxOf(1.0, r / 6),
        pitch: Double = r * 2.6,
        groupGap: Double = 0.0,
        extras: TabPainter.() -> Unit = {},
    ): TabPainter {
        val lineHeight = 2 * r + 5 * pitch + groupGap + 4 * r
        val p = TabPainter(width, (lines.size * lineHeight + 2 * r).toInt() + 20)
        lines.forEachIndexed { row, line ->
            line.forEachIndexed { i, n ->
                p.column(20 + r + i * gap, 10 + row * lineHeight, n, r, pitch, ringThickness, ringDarkness, groupGap)
            }
        }
        p.extras()
        return p
    }

    private val tune = listOf(
        listOf("XXX XXX", "XXX XXO", "XXX XOO", "XXX OOO", "XXO OOO", "XOO OOO", "OXX OOO", "OOO OOO"),
        listOf("OXX XXX+", "XXX XXO+", "XXX XOO+", "XX½ OOO", "XXX X½O", "XXO OOO++", "XOO OOO"),
    )

    @Test
    fun `reads a clean chart line by line, with half holes and octave marks`() {
        val reading = TabReader.read(chart(tune, r = 12.0).image())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `thin grey rings next to bold discs`() {
        val reading = TabReader.read(chart(tune, r = 7.0, ringDarkness = 0.45f, ringThickness = 1.0).image())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `tightly packed columns stay apart`() {
        val reading = TabReader.read(chart(tune, r = 14.0, gap = 14.0 * 2.25, pitch = 14.0 * 2.25).image())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `holes drawn as two groups of three, nearly touching`() {
        // Like the app's own diagrams in a phone screenshot: 1-2 px between holes.
        val reading = TabReader.read(chart(tune, r = 8.0, pitch = 17.5, groupGap = 7.0).image())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `dark mode, light holes on dark paper`() {
        val reading = TabReader.read(chart(tune, r = 12.0).image().inverted())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `a dark header bar above the tab is ignored`() {
        val reading = TabReader.read(chart(tune, r = 12.0, extras = { rect(0.0, 0.0, 1100.0, 6.0) }).image())
        assertEquals(tune, notations(reading))
    }

    @Test
    fun `a small image is enlarged and boxes are in its own pixels`() {
        val small = listOf(listOf("XXX XXX", "XXX OOO", "XOO OOO+"))
        val reading = TabReader.read(chart(small, r = 4.0, width = 300).image())
        assertEquals(small, notations(reading))
        val first = reading.columns.first().box
        // Drawn at x 20..28, y 10..(10 + 8 + 5 * 10.4).
        assertTrue("$first", first.left in 18..21 && first.right in 27..30)
        assertTrue("$first", first.top in 8..11 && first.bottom in 69..72)
    }

    @Test
    fun `staff lines, text and stray dots are ignored`() {
        val reading = TabReader.read(
            chart(listOf(listOf("XXX OOO", "XXO OOO")), r = 10.0) {
                for (i in 0 until 5) rect(400.0, 30.0 + i * 12, 1000.0, 31.5 + i * 12) // a staff
                disc(500.0, 42.0, 6.0) // a note head on it
                rect(600.0, 150.0, 640.0, 162.0) // a word
                disc(700.0, 150.0, 8.0) // a lone dot
                disc(760.0, 150.0, 8.0); disc(760.0, 176.0, 8.0) // a short column
            }.image(),
        )
        assertEquals(listOf(listOf("XXX OOO", "XXO OOO")), notations(reading))
    }

    @Test
    fun `tiny blurred diagrams are read column first, a blurred plus counts as an octave`() {
        val tiny = listOf(
            listOf("XXX XXX", "XXX OOO", "XOO OOO", "XXX XXO", "OXX OOO", "XXO OOO", "OXX XXX+", "XXX XOO"),
        )
        val p = chart(tiny, r = 2.2, gap = 14.0, width = 160, pitch = 4.6, ringThickness = 1.0)
        val reading = TabReader.read(p.image(blur = 1))
        assertEquals(tiny, notations(reading))
    }

    @Test
    fun `a blank page has no tab`() {
        val reading = TabReader.read(GrayImage(200, 100, IntArray(200 * 100) { 255 }))
        assertEquals(emptyList<List<TabColumn>>(), reading.lines)
    }
}

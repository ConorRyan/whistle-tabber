package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringOptionsTest {

    @Test
    fun `each note's options are distinct, in its octave, hard only in the third`() {
        for (offset in FingeringOptions.offsets) {
            val options = FingeringOptions.at(offset).map { it.fingering }
            assertTrue("offset $offset", options.isNotEmpty())
            assertEquals("offset $offset", options.size, options.map { it.holes }.toSet().size)
            options.forEach {
                assertEquals("offset $offset", offset / 12, it.octave)
                assertEquals("offset $offset", offset >= 24, it.hard)
            }
        }
    }

    @Test
    fun `the whole range is covered, bottom D to third-octave C`() {
        assertEquals((0..FingeringOptions.HIGHEST_OFFSET).toList(), FingeringOptions.offsets)
    }

    @Test
    fun `notes the user asked about have alternatives`() {
        assertEquals("OXX XXX+", FingeringOptions.at(12)[1].fingering.notation()) // vented D
        assertEquals(9, FingeringOptions.at(10).size) // C natural
        assertEquals(9, FingeringOptions.at(22).size)
        assertEquals(listOf("X½O OOO", "XOX XOO"), FingeringOptions.at(8).map { it.fingering.notation() })
        assertTrue(FingeringOptions.at(8)[1].fife)
    }

    @Test
    fun `choices encode as shapes and read back`() {
        val c = FingeringChoices.DEFAULT.with(12, 1).with(10, 1).with(31, 2)
        assertEquals("10=½OOOOO;12=OXXXXX+;31=OXXXXO++", c.encode())
        assertEquals(c, FingeringChoices.decode(c.encode()))
        assertEquals(FingeringChoices.DEFAULT, FingeringChoices.decode(""))
        assertEquals(FingeringChoices.DEFAULT, FingeringChoices.decode(null))
    }

    @Test
    fun `out-of-range picks are ignored or fall back to the default`() {
        assertEquals(FingeringChoices.DEFAULT, FingeringChoices.DEFAULT.with(12, 7))
        assertEquals(FingeringChoices.DEFAULT, FingeringChoices.DEFAULT.with(40, 0))
        val stray = FingeringChoices(mapOf(12 to Fingering.parse("OOO OOO+")))
        assertEquals(0, stray.index(12))
        assertEquals("XXX XXX+", stray.fingeringAt(12)?.notation())
    }

    @Test
    fun `picking the default clears the pick`() {
        assertEquals(FingeringChoices.DEFAULT, FingeringChoices.DEFAULT.with(12, 1).with(12, 0))
    }
}

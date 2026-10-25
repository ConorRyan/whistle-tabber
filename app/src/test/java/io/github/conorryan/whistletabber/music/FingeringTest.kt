package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringTest {

    @Test
    fun `parses chart notation top hole first`() {
        val f = Fingering.parse("XXX X½O")
        assertEquals(
            listOf(Hole.CLOSED, Hole.CLOSED, Hole.CLOSED, Hole.CLOSED, Hole.HALF, Hole.OPEN),
            f.holes,
        )
        assertEquals(0, f.octave)
    }

    @Test
    fun `trailing plus marks set the octave`() {
        assertEquals(1, Fingering.parse("XXX XXX+").octave)
        assertEquals(2, Fingering.parse("OXX XXX++").octave)
    }

    @Test
    fun `notation round-trips`() {
        listOf("XXX XXX", "XXX XX½", "OXX OOO+", "XOX OXX++", "½OO OOO").forEach {
            assertEquals(it, Fingering.parse(it).notation())
        }
    }

    @Test
    fun `whitespace is optional`() {
        assertEquals(Fingering.parse("XXX OOO"), Fingering.parse("XXXOOO"))
    }

    @Test
    fun `inOctave keeps holes and can mark hard`() {
        val f = Fingering.parse("XOO OOO").inOctave(2, hard = true)
        assertEquals("XOO OOO++", f.notation())
        assertTrue(f.hard)
    }

    @Test
    fun `rejects the wrong number of holes or unknown symbols`() {
        assertThrows(IllegalArgumentException::class.java) { Fingering.parse("XXX XX") }
        assertThrows(IllegalArgumentException::class.java) { Fingering.parse("XXX XXXX") }
        assertThrows(IllegalArgumentException::class.java) { Fingering.parse("XXX XX?") }
        assertThrows(IllegalArgumentException::class.java) { Fingering.parse("XXX XXX+++") }
    }

    @Test
    fun `every C natural choice is a distinct first-octave fingering`() {
        val all = CNatural.entries.map { it.fingering }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.all { it.octave == 0 && !it.hard })
        assertEquals("OXX OOO", CNatural.DEFAULT.fingering.notation())
    }
}

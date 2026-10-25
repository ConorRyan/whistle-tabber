package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyChangeTest {

    @Test
    fun `steps take the shorter way round`() {
        assertEquals(5, KeyChange.steps("D", "G"))
        assertEquals(-5, KeyChange.steps("G", "D"))
        assertEquals(-2, KeyChange.steps("D", "C"))
        assertEquals(0, KeyChange.steps("Dmaj", "D"))
        assertEquals(-6, KeyChange.steps("D", "Ab")) // a tritone goes down
        assertEquals(5, KeyChange.steps("Em", "A"))
        assertEquals(-1, KeyChange.steps("Bb", "A"))
        assertEquals(1, KeyChange.steps("F#m", "G"))
    }

    @Test
    fun `steps of unreadable keys are null`() {
        assertNull(KeyChange.steps(null, "G"))
        assertNull(KeyChange.steps("none", "G"))
        assertNull(KeyChange.steps("D", "H"))
    }

    @Test
    fun `every offered tonic is reachable from every tonic`() {
        for (from in KeyChange.TONICS) for (to in KeyChange.TONICS) {
            val steps = KeyChange.steps(from, to)!!
            assert(steps in -6..5) { "$from→$to: $steps" }
        }
    }

    @Test
    fun `key names keep the mode`() {
        assertEquals("G", KeyChange.keyName("D", "G"))
        assertEquals("Am", KeyChange.keyName("Em", "A"))
        assertEquals("Am", KeyChange.keyName("E minor", "A"))
        assertEquals("Gdor", KeyChange.keyName("Ador", "G"))
        assertEquals("G", KeyChange.keyName(null, "G"))
    }

    @Test
    fun `copy title names the key`() {
        assertEquals("Banish Misfortune (G)", KeyChange.copyTitle("Banish Misfortune", "G"))
    }
}

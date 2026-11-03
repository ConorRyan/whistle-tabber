package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ABC from anywhere — a bad import, a stray paste — never throws the tune
 * screen, which reads every tune it shows ([TapTune.read]): hostile text
 * reads as a draft, as losses, or not at all, and never as a crash.
 */
class TapTuneRobustnessTest {

    private fun abc(body: String, headers: String = "X:1\nT:Hostile\nM:4/4\nL:1/8\nK:D\n") = headers + body

    /** The read of [body]: a draft whose lengths are sane, or null. */
    private fun read(body: String, headers: String = "X:1\nT:Hostile\nM:4/4\nL:1/8\nK:D\n"): TapRead? {
        val read = TapTune.read(abc(body, headers))
        read?.let {
            val lengths = it.draft.timing!!.lengths
            assertTrue("$body -> $lengths", lengths.all { l -> l in 1..TICKS_PER_WHOLE * 64 })
        }
        return read
    }

    @Test
    fun `a denominator too big for an Int is its slashes`() {
        val read = read("A/99999999999 B |\n")!!
        assertEquals(listOf(7, 9), read.draft.notes)
        assertEquals(emptySet<Loss>(), read.losses)
    }

    @Test
    fun `a tuplet of zero or a huge count is skipped`() {
        val zero = read("(0:2:3A B c |\n")!!
        assertEquals(listOf(7, 9, 11), zero.draft.notes) // c in D is C♯
        assertEquals(emptySet<Loss>(), zero.losses)
        read("(99999999999A B c |\n")
    }

    @Test
    fun `an L or Q too big for an Int reads as absent`() {
        assertNull(read("A|\n", headers = "X:1\nT:Hostile\nM:4/4\nL:1/99999999999\nK:D\n"))
        assertNotNull(read("A|\n", headers = "X:1\nT:Hostile\nM:4/4\nL:1/8\nQ:1/2=99999999999\nK:D\n"))
    }

    @Test
    fun `an absurd written length is capped, at a rounding loss`() {
        val read = read("A2000000000 z |\n")!!
        assertTrue(Loss.ROUNDED in read.losses)
        assertEquals(TICKS_PER_WHOLE * 64, read.draft.timing!!.lengths[0])
    }

    @Test
    fun `a huge multi-bar rest is capped, not overflowed`() {
        val read = read("Z2000000000 A|\n")!!
        assertTrue(Loss.ROUNDED in read.losses)
        assertEquals(TICKS_PER_WHOLE * 64, read.draft.timing!!.lengths[0])
    }
}

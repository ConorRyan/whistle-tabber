package io.github.conorryan.whistletabber.ui.tap

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePlayerTest {

    @Test
    fun `the end of a note fades to silence`() {
        val pcm = ShortArray(100) { 1000 }
        val faded = NotePlayer.faded(pcm, sampleRate = 400) // a 20-sample fade
        assertEquals(1000, faded[79].toInt())
        assertEquals(500, faded[89].toInt())
        assertEquals(0, faded[99].toInt())
        assertEquals(100, faded.size)
    }
}

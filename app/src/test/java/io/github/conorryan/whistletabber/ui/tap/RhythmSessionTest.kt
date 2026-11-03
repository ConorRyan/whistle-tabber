package io.github.conorryan.whistletabber.ui.tap

import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.Rhythm
import io.github.conorryan.whistletabber.music.RhythmSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmSessionTest {

    private val reel = RhythmSetup(bpm = 120, meter = Meter.FOUR_FOUR)

    @Test
    fun `each press plays the next note, the last release finishes`() {
        val s = RhythmSession(reel, 2)
        assertTrue(s.empty)
        assertEquals(0, s.press(0))
        s.release(400)
        assertEquals(1, s.next)
        assertEquals(1, s.press(500))
        assertFalse(s.finished)
        s.release(900)
        assertTrue(s.finished)
        assertNull(s.press(1000))
        assertEquals(listOf(Rhythm.Event(0, 48), Rhythm.Event(1, 48)), s.rhythm().events)
    }

    @Test
    fun `a press while one is held ends it there`() {
        val s = RhythmSession(reel, 3)
        s.press(0)
        assertEquals(1, s.press(500)) // the next finger landed first
        s.release(600)
        assertEquals(2, s.next)
        assertEquals(listOf(48, 24), s.rhythm().events.take(2).map { it.ticks }) // the first ended at 500
    }

    @Test
    fun `a release with nothing held, or a held press dropped, changes nothing`() {
        val s = RhythmSession(reel, 2)
        s.release(100)
        assertTrue(s.empty)
        s.press(0)
        s.cancelHeld()
        s.release(400)
        assertTrue(s.empty)
        assertEquals(listOf(Rhythm.Event(0, 48), Rhythm.Event(1, 48)), s.rhythm().events) // untimed: a beat each
    }
}

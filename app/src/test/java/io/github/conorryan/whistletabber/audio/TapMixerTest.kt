package io.github.conorryan.whistletabber.audio

import io.github.conorryan.whistletabber.music.WhistleSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TapMixerTest {

    /** 1000 frames a second, clicks 10 frames long, no metronome yet. */
    private fun silent() = TapMixer(1000, ShortArray(10) { 1000 }, ShortArray(10) { 500 })

    /** [silent], with the metronome started at frame 0: a beat every 100 frames, four to a bar. */
    private fun mixer() = silent().apply { startClicks(100, 4) }

    private fun TapMixer.next(frames: Int) = ShortArray(frames).also { render(it) }

    private fun peak(pcm: ShortArray): Int = pcm.maxOf { abs(it.toInt()) }

    @Test
    fun `the end of a note fades to silence`() {
        val pcm = ShortArray(100) { 1000 }
        val faded = TapMixer.faded(pcm, sampleRate = 400) // a 20-sample fade
        assertEquals(1000, faded[79].toInt())
        assertEquals(500, faded[89].toInt())
        assertEquals(0, faded[99].toInt())
        assertEquals(100, faded.size)
    }

    @Test
    fun `clicks fall on the beats, beat 1 accented`() {
        val m = mixer()
        val bar = m.next(400)
        assertEquals(1000, bar[0].toInt())
        assertEquals(1000, bar[9].toInt())
        assertEquals(0, bar[10].toInt())
        assertEquals(500, bar[100].toInt())
        assertEquals(500, bar[300].toInt())
        assertEquals(1000, m.next(1)[0].toInt()) // the next bar
        assertEquals(401, m.frame)
    }

    @Test
    fun `without the metronome only notes sound, and it starts and stops at a chunk`() {
        val m = silent()
        assertTrue(m.next(250).all { it.toInt() == 0 })
        assertEquals(-1L, m.clickStartFrame)
        m.noteOn(ShortArray(5) { 700 }, 1f)
        assertEquals(listOf(700, 700, 700, 700, 700, 0), m.next(6).map { it.toInt() })

        m.startClicks(100, 2)
        val bars = m.next(200)
        assertEquals(256L, m.clickStartFrame) // where the chunk began, not frame 0
        assertEquals(1000, bars[0].toInt()) // the first click is beat 1
        assertEquals(500, bars[100].toInt())
        assertEquals(1000, m.next(1)[0].toInt())

        m.stopClicks()
        assertTrue(m.next(300).all { it.toInt() == 0 })
        assertEquals(-1L, m.clickStartFrame)
    }

    @Test
    fun `a note starts with the next chunk and fades out without a jump`() {
        val m = mixer()
        m.next(20)
        m.noteOn(ShortArray(1000) { 2000 }, 0.5f)
        assertTrue(m.next(30).all { it.toInt() == 1000 })
        m.noteOff()
        // RELEASE_SECONDS at 1000 frames a second is 5 frames.
        assertEquals(listOf(1000, 800, 600, 400, 200, 0, 0), m.next(7).map { it.toInt() })
    }

    @Test
    fun `a new note fades the old one out as it starts, a note ends with its sample`() {
        val m = mixer()
        m.next(20)
        m.noteOn(ShortArray(1000) { 2000 }, 1f)
        m.next(5)
        m.noteOn(ShortArray(3) { 300 }, 1f)
        // The old note: 2000 fading over 5 frames; the new one: three frames of 300.
        assertEquals(listOf(2300, 1900, 1500, 800, 400, 0), m.next(6).map { it.toInt() })
    }

    @Test
    fun `fast taps never jump`() {
        val m = mixer()
        m.next(20)
        val out = mutableListOf<Int>()
        repeat(6) {
            m.noteOn(ShortArray(1000) { 3000 }, 1f)
            out += m.next(3).map { it.toInt() }
        }
        out.zipWithNext().forEach { (a, b) -> assertTrue("$a → $b", abs(b - a) <= 3000) }
    }

    @Test
    fun `the pad note starts with a tick mixed in`() {
        val note = ShortArray(10) { 1000 }
        val onset = ShortArray(3) { 20_000 }
        val mixed = TapMixer.withOnset(note, 0.5f, onset, 2f)
        assertEquals(listOf(32_767, 32_767, 32_767, 500, 500), mixed.take(5).map { it.toInt() })
        assertEquals(10, mixed.size)
    }

    @Test
    fun `a press shorter than a chunk still sounds`() {
        val m = mixer()
        m.next(20)
        m.noteOn(ShortArray(1000) { 1000 }, 1f)
        m.noteOff()
        assertEquals(listOf(1000, 800, 600, 400, 200, 0), m.next(6).map { it.toInt() })
    }

    @Test
    fun `the mix is clamped`() {
        val m = TapMixer(1000, ShortArray(10) { 30_000 }, ShortArray(10) { 30_000 }).apply { startClicks(100, 4) }
        m.noteOn(ShortArray(10) { 30_000 }, 1f)
        assertEquals(Short.MAX_VALUE, m.next(1)[0])
    }

    @Test
    fun `the click is louder than the pad's note and dies away, the tick is shorter`() {
        val rate = WhistleSynth.SAMPLE_RATE
        val click = TapMixer.click(rate, TapMixer.ACCENT_HZ)
        val note = WhistleSynth.renderPcm(74, seconds = 0.5)
        assertTrue(peak(click) > 0.9 * Short.MAX_VALUE)
        assertTrue(peak(click) > 2 * peak(note) * TapMixer.NOTE_VOLUME)
        assertTrue(peak(click.copyOfRange(click.size - 50, click.size)) < 0.05 * Short.MAX_VALUE)
        val tick = TapMixer.click(rate, TapMixer.TICK_HZ, TapMixer.TICK_SECONDS)
        assertTrue(tick.size < click.size / 2)
        assertTrue(peak(tick.copyOfRange(tick.size - 20, tick.size)) < 0.05 * Short.MAX_VALUE)
    }
}

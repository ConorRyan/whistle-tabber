package io.github.conorryan.whistletabber.music

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WhistleSynthTest {

    @Test
    fun `note names map to MIDI like abcjs's pitch table`() {
        assertEquals(62, WhistleSynth.noteNameToMidi("D4"))
        assertEquals(74, WhistleSynth.noteNameToMidi("D5"))
        assertEquals(69, WhistleSynth.noteNameToMidi("A4"))
        assertEquals(70, WhistleSynth.noteNameToMidi("Bb4"))
        assertEquals(73, WhistleSynth.noteNameToMidi("Db5"))
        assertEquals(60, WhistleSynth.noteNameToMidi("C4"))
        assertEquals(21, WhistleSynth.noteNameToMidi("A0"))
        assertEquals(108, WhistleSynth.noteNameToMidi("C8"))
    }

    @Test
    fun `malformed note names are rejected`() {
        listOf("", "H4", "D#5", "d5", "D", "Dbb5", "D55", "D5.mp3", "../D5").forEach {
            assertNull(it, WhistleSynth.noteNameToMidi(it))
        }
    }

    @Test
    fun `frequency is equal-tempered around A440`() {
        assertEquals(440.0, WhistleSynth.frequency(69), 1e-9)
        assertEquals(587.33, WhistleSynth.frequency(74), 0.01) // D5, a D whistle's bottom note
        assertEquals(880.0, WhistleSynth.frequency(81), 1e-9)
    }

    @Test
    fun `wav header describes 16-bit mono PCM`() {
        val pcm = ShortArray(100) { it.toShort() }
        val wav = WhistleSynth.wav(pcm, sampleRate = 22_050)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 200, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(22_050, b.getInt(24))
        assertEquals(44_100, b.getInt(28)) // byte rate
        assertEquals(2, b.getShort(32).toInt())
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(200, b.getInt(40))
        assertEquals(244, wav.size)
        assertEquals(99, b.getShort(44 + 99 * 2).toInt())
    }

    @Test
    fun `rendered note lasts SECONDS at SAMPLE_RATE`() {
        val pcm = WhistleSynth.renderPcm(74)
        assertEquals((WhistleSynth.SAMPLE_RATE * WhistleSynth.SECONDS).toInt(), pcm.size)
    }

    @Test
    fun `rendered pitch matches the note`() {
        for (midi in listOf(62, 74, 81, 86, 93)) {
            val pcm = WhistleSynth.renderPcm(midi)
            // Measure over a steady stretch after the attack and before vibrato.
            val measured = zeroCrossingFrequency(pcm, from = 2_000, to = 7_000)
            val expected = WhistleSynth.frequency(midi)
            assertTrue("midi $midi: expected $expected Hz, measured $measured", abs(measured - expected) / expected < 0.02)
        }
    }

    @Test
    fun `attack starts quiet and the sustain is loud but never clips`() {
        val pcm = WhistleSynth.renderPcm(74)
        val firstMs = pcm.take(WhistleSynth.SAMPLE_RATE / 1000).maxOf { abs(it.toInt()) }
        val sustain = pcm.copyOfRange(WhistleSynth.SAMPLE_RATE, 2 * WhistleSynth.SAMPLE_RATE).maxOf { abs(it.toInt()) }
        assertTrue("onset peak $firstMs", firstMs < Short.MAX_VALUE / 4)
        assertTrue("sustain peak $sustain", sustain > Short.MAX_VALUE / 3)
        assertTrue("sustain peak $sustain", sustain < Short.MAX_VALUE)
    }

    @Test
    fun `output is deterministic`() {
        assertArrayEquals(WhistleSynth.renderPcm(79), WhistleSynth.renderPcm(79))
        assertArrayEquals(WhistleSynth.wavForNote("G5"), WhistleSynth.wavForNote("G5"))
    }

    @Test
    fun `wavForNote returns null for bad names`() {
        assertNull(WhistleSynth.wavForNote("X9"))
    }

    private fun zeroCrossingFrequency(pcm: ShortArray, from: Int, to: Int): Double {
        var crossings = 0
        for (i in from + 1 until to) {
            if (pcm[i - 1] < 0 && pcm[i] >= 0) crossings++
        }
        return crossings * WhistleSynth.SAMPLE_RATE.toDouble() / (to - from)
    }
}

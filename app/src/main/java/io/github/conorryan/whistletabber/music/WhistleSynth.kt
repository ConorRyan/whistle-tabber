package io.github.conorryan.whistletabber.music

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesises the per-note samples abcjs plays back, so the app ships no
 * soundfont. A tin whistle is close to a pure tone: a sine with a little 2nd
 * and 3rd harmonic, a short breathy onset, and vibrato that fades in. abcjs
 * cuts each sample at the note's end and fades it out, so samples only need
 * an attack and a long sustain.
 *
 * Output is deterministic (fixed noise seed) so builds and tests are stable.
 */
object WhistleSynth {
    const val SAMPLE_RATE = 22_050
    const val SECONDS = 4.0

    private val NOTE_NAME = Regex("^([A-G])(b?)(-?\\d)$")
    private val PITCH_CLASS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** abcjs note names ("D5", "Bb4", "Db6"; flats only) to MIDI; null when malformed. */
    fun noteNameToMidi(name: String): Int? {
        val match = NOTE_NAME.matchEntire(name) ?: return null
        val (letter, flat, octave) = match.destructured
        val midi = (octave.toInt() + 1) * 12 + PITCH_CLASS.getValue(letter[0]) - if (flat.isEmpty()) 0 else 1
        return midi.takeIf { it in 0..127 }
    }

    fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun renderPcm(midi: Int, sampleRate: Int = SAMPLE_RATE, seconds: Double = SECONDS): ShortArray {
        val f = frequency(midi)
        val count = (sampleRate * seconds).toInt()
        val noise = Random(midi)
        val out = ShortArray(count)
        var phase = 0.0
        var lowPassed = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val attack = min(1.0, t / 0.025)
            val vibratoDepth = 0.004 * min(1.0, maxOf(0.0, t - 0.35) / 0.6)
            val vibrato = 1.0 + vibratoDepth * sin(2 * PI * 5.5 * t)
            phase += 2 * PI * f * vibrato / sampleRate
            val tone = sin(phase) + 0.12 * sin(2 * phase) + 0.05 * sin(3 * phase)
            // Breath "chiff": low-passed noise that decays over the first 60 ms,
            // plus a faint constant breath under the tone.
            lowPassed += 0.25 * (noise.nextDouble(-1.0, 1.0) - lowPassed)
            val breath = lowPassed * (0.35 * exp(-t / 0.06) + 0.02)
            val sample = 0.6 * attack * tone + breath
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 16-bit mono PCM wrapped in a RIFF/WAVE header. */
    fun wav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataBytes = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }
        val data = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        return ByteArrayOutputStream(44 + dataBytes).apply {
            write(header.array())
            write(data.array())
        }.toByteArray()
    }

    fun wavForNote(name: String): ByteArray? = noteNameToMidi(name)?.let { wav(renderPcm(it)) }
}

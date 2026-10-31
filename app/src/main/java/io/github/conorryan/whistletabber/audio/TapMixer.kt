package io.github.conorryan.whistletabber.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * The tap editor's sound, as samples: notes (the palette's taps, the
 * rhythm pad's held notes) and, between [startClicks] and [stopClicks], a
 * metronome click on every beat (beat 1 of each bar [accent]ed). Pure, so
 * [TapAudio] only moves its output to the speaker.
 *
 * Every command may be called from any thread; it is queued and applied at
 * the start of the next [render], so a press costs nothing. A note off
 * fades the voice out over [RELEASE_SECONDS]; a new note fades the old one
 * out the same way while it starts, so fast taps don't pop.
 */
class TapMixer(
    val rate: Int,
    private val accent: ShortArray,
    private val plain: ShortArray,
) {

    private sealed interface Command {
        class On(val pcm: ShortArray, val volume: Float) : Command
        data object Off : Command
        class Clicks(val beatFrames: Long, val beatsPerBar: Int) : Command
        data object NoClicks : Command
    }

    private val commands = ConcurrentLinkedQueue<Command>()
    private val releaseFrames = maxOf(1, (rate * RELEASE_SECONDS).toInt())

    /** Frames rendered so far. */
    var frame: Long = 0
        private set

    /** The frame of the metronome's first click (its count-in), or -1 while it is off or not yet started. */
    @Volatile
    var clickStartFrame: Long = -1
        private set
    private var beatFrames = 0L
    private var beatsPerBar = 1

    private class Voice(val pcm: ShortArray, val volume: Float) {
        var pos = 0
        /** Frames left in the fade-out, or -1 while held. */
        var releaseLeft = -1
    }

    /** The held voice last, earlier ones fading out. */
    private val voices = ArrayList<Voice>(4)

    private fun releaseAll() {
        for (v in voices) if (v.releaseLeft < 0) v.releaseLeft = releaseFrames
    }

    /** Starts [pcm] at [volume]; any voice playing fades out. */
    fun noteOn(pcm: ShortArray, volume: Float) {
        commands += Command.On(pcm, volume)
    }

    /** Fades the voice out. */
    fun noteOff() {
        commands += Command.Off
    }

    /** Starts the metronome with the next chunk: a click every [beatFrames], [beatsPerBar] to a bar. */
    fun startClicks(beatFrames: Long, beatsPerBar: Int) {
        commands += Command.Clicks(maxOf(1L, beatFrames), maxOf(1, beatsPerBar))
    }

    fun stopClicks() {
        commands += Command.NoClicks
    }

    /** Fills [out] with the next frames. */
    fun render(out: ShortArray) {
        while (true) {
            when (val c = commands.poll() ?: break) {
                is Command.On -> {
                    releaseAll()
                    voices += Voice(c.pcm, c.volume)
                }
                Command.Off -> releaseAll()
                is Command.Clicks -> {
                    beatFrames = c.beatFrames
                    beatsPerBar = c.beatsPerBar
                    clickStartFrame = frame
                }
                Command.NoClicks -> clickStartFrame = -1
            }
        }
        val clickStart = clickStartFrame
        for (i in out.indices) {
            var sample = 0f
            if (clickStart >= 0) {
                val f = frame + i - clickStart
                val inBeat = f % beatFrames
                val click = if ((f / beatFrames) % beatsPerBar == 0L) accent else plain
                if (inBeat < click.size) sample = click[inBeat.toInt()].toFloat()
            }
            for (v in voices) {
                if (v.pos >= v.pcm.size || v.releaseLeft == 0) continue
                val gain = if (v.releaseLeft < 0) 1f else v.releaseLeft.toFloat() / releaseFrames
                sample += v.pcm[v.pos] * v.volume * gain
                v.pos++
                if (v.releaseLeft > 0) v.releaseLeft--
            }
            out[i] = sample.coerceIn(-Short.MAX_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
        }
        frame += out.size
        voices.removeAll { it.pos >= it.pcm.size || it.releaseLeft == 0 }
    }

    companion object {
        const val RELEASE_SECONDS = 0.005
        const val ACCENT_HZ = 1760.0
        const val PLAIN_HZ = 1175.0
        const val CLICK_SECONDS = 0.05
        const val CLICK_PEAK = 0.95

        /** The pad's tick: higher and shorter than a beat, so it isn't mistaken for one. */
        const val TICK_HZ = 2640.0
        const val TICK_SECONDS = 0.02
        const val TICK_VOLUME = 0.7f

        private const val FADE_SECONDS = 0.05

        /** [pcm] with its last [FADE_SECONDS] faded to silence, so a note that runs out doesn't click. */
        fun faded(pcm: ShortArray, sampleRate: Int): ShortArray {
            val fade = minOf(pcm.size, (sampleRate * FADE_SECONDS).toInt())
            val start = pcm.size - fade
            return ShortArray(pcm.size) { i ->
                if (i < start) pcm[i] else (pcm[i] * (pcm.size - 1 - i).toFloat() / fade).toInt().toShort()
            }
        }

        /** The pad's whistle note, quiet enough that the beat stays easy to hear over it. */
        const val NOTE_VOLUME = 0.4f

        /** A palette tap's note: short, like the tap that plays it. */
        const val TAP_SECONDS = 0.4

        /** The tongued start of the pad's note: a short tick, which is what makes a tap feel immediate. */
        const val ONSET_SECONDS = 0.012
        const val ONSET_VOLUME = 0.5f

        /** [note] at [noteGain] with [onset] at [onsetGain] mixed into its start, clamped. */
        fun withOnset(note: ShortArray, noteGain: Float, onset: ShortArray, onsetGain: Float): ShortArray =
            ShortArray(note.size) { i ->
                val mixed = note[i] * noteGain + (if (i < onset.size) onset[i] * onsetGain else 0f)
                mixed.coerceIn(-Short.MAX_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            }

        /**
         * A woodblock-like blip at [CLICK_PEAK]: a sine with odd harmonics (it
         * cuts through a pure whistle tone) decaying to near silence over
         * [seconds].
         */
        fun click(rate: Int, hz: Double, seconds: Double = CLICK_SECONDS): ShortArray {
            val decay = 5 / seconds
            val raw = DoubleArray((rate * seconds).toInt()) { i ->
                val t = i.toDouble() / rate
                val tone = sin(2 * PI * hz * t) + 0.4 * sin(2 * PI * 3 * hz * t) + 0.2 * sin(2 * PI * 5 * hz * t)
                tone * exp(-t * decay)
            }
            val scale = CLICK_PEAK * Short.MAX_VALUE / raw.maxOf { abs(it) }
            return ShortArray(raw.size) { (raw[it] * scale).toInt().toShort() }
        }
    }
}

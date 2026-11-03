package io.github.conorryan.whistletabber.music

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A time signature the rhythm tapper knows, as written in `M:`. The beat is a
 * quarter in x/4 meters and `C`, a half in x/2 and `C|`, and a dotted quarter
 * in compound ones (a jig at 120 is 120 dotted quarters a minute), which is
 * what the BPM counts.
 */
enum class Meter(val abc: String, val beatsPerBar: Int, val eighthsPerBeat: Int) {
    TWO_FOUR("2/4", 2, 2),
    THREE_FOUR("3/4", 3, 2),
    FOUR_FOUR("4/4", 4, 2),
    COMMON("C", 4, 2),
    TWO_TWO("2/2", 2, 4),
    CUT("C|", 2, 4),
    THREE_TWO("3/2", 3, 4),
    THREE_EIGHT("3/8", 1, 3),
    SIX_EIGHT("6/8", 2, 3),
    NINE_EIGHT("9/8", 3, 3),
    TWELVE_EIGHT("12/8", 4, 3),
    ;

    val compound: Boolean get() = eighthsPerBeat == 3

    /** A beat and a bar in [TICKS_PER_WHOLE] ticks. */
    val ticksPerBeat: Int get() = eighthsPerBeat * TICKS_PER_EIGHTH
    val ticksPerBar: Int get() = beatsPerBar * ticksPerBeat

    /** What a `Q:` tempo counts: the beat. */
    val tempoNote: String get() = when (eighthsPerBeat) {
        3 -> "3/8"
        4 -> "1/2"
        else -> "1/4"
    }

    companion object {
        /** The meter written [text] (`M:` value), if it is one of these. */
        fun of(text: String?): Meter? = entries.firstOrNull { it.abc == text?.trim() }
    }
}

/**
 * Note lengths are counted in ticks, [TICKS_PER_WHOLE] to a whole note, so
 * that every length down to a 64th and every triplet of them is a whole
 * number: an eighth is 24 ticks, an eighth-note triplet 16.
 */
const val TICKS_PER_WHOLE = 192
const val TICKS_PER_EIGHTH = TICKS_PER_WHOLE / 8

/** The shortest note the fit writes. */
enum class RhythmGrid(val perEighth: Int, val unitLength: String) {
    EIGHTH(1, "1/8"),
    SIXTEENTH(2, "1/16"),
    ;

    val ticks: Int get() = TICKS_PER_EIGHTH / perEighth
}

/** What the rhythm is tapped against. */
data class RhythmSetup(
    val bpm: Int = 100,
    val meter: Meter = Meter.FOUR_FOUR,
    val grid: RhythmGrid = RhythmGrid.EIGHTH,
) {
    val unitsPerBeat: Int get() = meter.eighthsPerBeat * grid.perEighth
    val unitsPerBar: Int get() = meter.beatsPerBar * unitsPerBeat
    val beatMs: Double get() = 60_000.0 / bpm
    val unitMs: Double get() = beatMs / unitsPerBeat
    val ticksPerBeat: Int get() = meter.ticksPerBeat
    val ticksPerBar: Int get() = meter.ticksPerBar

    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 200
    }
}

/** One held note, in ms from beat 1 of the first bar after the count-in (negative: during the count-in). */
data class Press(val downMs: Long, val upMs: Long)

/**
 * A fitted rhythm: [events] in order from the first note, each [ticks] long
 * ([TICKS_PER_WHOLE] to a whole note); [note] indexes the tune's sounding
 * notes, null is a rest. [firstBarTicks] is the length of the first bar (less
 * than a bar for a pickup), so bar lines fall on the metronome's downbeats.
 */
data class Rhythm(val events: List<Event>, val firstBarTicks: Int) {
    data class Event(val note: Int?, val ticks: Int)
}

/**
 * Best-guess note lengths from presses tapped along to a metronome. Pure, so
 * the thresholds below can be tuned against tests.
 */
object RhythmFit {

    /** A gap shorter than this many beats between a release and the next press is legato, not a rest. */
    const val LEGATO_BEATS = 1.0

    /** The most the latency step will shift every press, either way. */
    const val MAX_LATENCY_MS = 150.0

    /**
     * The rhythm of [noteCount] notes, the first `presses.size` of them tapped
     * as [presses]; notes not reached get one beat each. After a note in
     * [breaks] (the tune jumps on, as to a second ending) the note is as long
     * as it was held, with no rest after it.
     */
    fun fit(presses: List<Press>, noteCount: Int, setup: RhythmSetup, breaks: Set<Int> = emptySet()): Rhythm {
        val beat = setup.unitsPerBeat
        val tick = setup.grid.ticks
        val untimed = (noteCount - presses.size).coerceAtLeast(0)
        val tapped = presses.take(noteCount)
        if (tapped.isEmpty()) {
            return Rhythm(List(untimed) { Rhythm.Event(it, beat * tick) }, setup.ticksPerBar)
        }

        val unitMs = setup.unitMs
        val latency = latencyUnits(tapped.map { it.downMs / unitMs }, setup)
        val onsets = mutableListOf<Int>()
        for (p in tapped) {
            val q = (p.downMs / unitMs - latency).roundToInt()
            onsets += if (onsets.isEmpty()) q else maxOf(q, onsets.last() + 1)
        }
        val releases = tapped.map { (it.upMs / unitMs - latency).roundToInt() }

        val events = mutableListOf<Rhythm.Event>()
        for (i in onsets.indices) {
            val start = onsets[i]
            val next = onsets.getOrNull(i + 1)
            if (next == null || i in breaks) {
                events += Rhythm.Event(i, (maxOf(releases[i], start + 1) - start).let { if (next == null) it else minOf(it, next - start) } * tick)
                if (next == null) break
                continue
            }
            val end = releases[i].coerceIn(start + 1, next)
            if (next - end < LEGATO_BEATS * beat) {
                events += Rhythm.Event(i, (next - start) * tick)
            } else {
                events += Rhythm.Event(i, (end - start) * tick)
                events += Rhythm.Event(null, (next - end) * tick)
            }
        }
        for (k in 0 until untimed) events += Rhythm.Event(tapped.size + k, beat * tick)

        val intoBar = Math.floorMod(onsets.first(), setup.unitsPerBar)
        return Rhythm(events, (setup.unitsPerBar - intoBar) * tick)
    }

    /**
     * How late the presses are on average, in grid units: the circular mean
     * of each onset's offset from the nearest eighth (so a constant delay
     * near half an eighth doesn't flip sign), capped at [MAX_LATENCY_MS].
     */
    private fun latencyUnits(onsets: List<Double>, setup: RhythmSetup): Double {
        val period = setup.grid.perEighth.toDouble()
        var s = 0.0
        var c = 0.0
        for (x in onsets) {
            val angle = 2 * PI * (x / period)
            s += sin(angle)
            c += cos(angle)
        }
        val mean = atan2(s, c) / (2 * PI) * period
        val cap = MAX_LATENCY_MS / setup.unitMs
        return mean.coerceIn(-cap, cap)
    }
}

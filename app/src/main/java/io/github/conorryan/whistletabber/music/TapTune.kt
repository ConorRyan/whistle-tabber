package io.github.conorryan.whistletabber.music

/**
 * A tune written by tapping fingerings: [notes] are semitones above
 * [whistle]'s bottom note ([FingeringEngine] offsets), null for a rest.
 * Storing fingerings, not pitches, lets the whistle change without
 * changing what is played.
 *
 * @property extraHeaders header lines [TapTune] doesn't write itself (such as
 *   `Z:` or `R:`), kept so a tune edited as text loses nothing
 * @property timing the tapped rhythm, one length per item of [notes]; null
 *   while every note is an equal quarter
 */
data class TapDraft(
    val title: String,
    val whistle: WhistleKey,
    val notes: List<Int?>,
    val extraHeaders: List<String> = emptyList(),
    val timing: TapTiming? = null,
) {
    /**
     * The notes with a tapped [rhythm] (from [RhythmFit], whose events index
     * the sounding notes): the draft's own rests give way to the rests the
     * fit found.
     */
    fun withRhythm(rhythm: Rhythm, setup: RhythmSetup): TapDraft {
        val sounding = notes.filterNotNull()
        val events = rhythm.events.filter { e -> e.note == null || e.note in sounding.indices }
        return copy(
            notes = events.map { e -> e.note?.let(sounding::get) },
            timing = TapTiming(setup, events.map { it.units }, rhythm.firstBarUnits),
        ).normalized()
    }

    /**
     * Inserts [note] (null: a rest) at [index]. In a timed draft it is as long
     * as the item before it, or a beat at the start. Returns the draft and the
     * index just after the new item.
     */
    fun inserted(index: Int, note: Int?): Pair<TapDraft, Int> {
        val at = index.coerceIn(0, notes.size)
        val t = timing ?: return copy(notes = notes.toMutableList().apply { add(at, note) }) to at + 1
        val length = t.lengths.getOrNull(at - 1) ?: t.setup.unitsPerBeat
        val draft = copy(
            notes = notes.toMutableList().apply { add(at, note) },
            timing = t.copy(lengths = t.lengths.toMutableList().apply { add(at, length) }),
        )
        val (merged, index) = draft.mergedRests()
        return merged.fixFirstBar() to index[at] + 1
    }

    /** Removes the item at [index], with its length. Returns the draft and where the cursor goes. */
    fun removed(index: Int): Pair<TapDraft, Int> {
        if (index !in notes.indices) return this to index.coerceIn(0, notes.size)
        val t = timing ?: return copy(notes = notes.toMutableList().apply { removeAt(index) }) to index
        val draft = copy(
            notes = notes.toMutableList().apply { removeAt(index) },
            timing = t.copy(lengths = t.lengths.toMutableList().apply { removeAt(index) }),
        )
        if (draft.notes.isEmpty()) return draft.copy(timing = null) to 0
        val (merged, map) = draft.mergedRests()
        return merged.fixFirstBar() to if (index == 0) 0 else map[index - 1] + 1
    }

    /**
     * The one way a timed draft is written down, so it reads back the same:
     * adjacent rests merged (ABC can't tie rests), the first bar no longer
     * than the tune, no timing without notes.
     */
    fun normalized(): TapDraft {
        if (timing == null) return this
        if (notes.isEmpty()) return copy(timing = null)
        return mergedRests().first.fixFirstBar()
    }

    /** Adjacent rests merged into one, and where each old item went. */
    private fun mergedRests(): Pair<TapDraft, IntArray> {
        val t = timing ?: return this to IntArray(notes.size) { it }
        val outNotes = mutableListOf<Int?>()
        val outLengths = mutableListOf<Int>()
        val map = IntArray(notes.size)
        for (i in notes.indices) {
            if (notes[i] == null && outNotes.isNotEmpty() && outNotes.last() == null) {
                outLengths[outLengths.size - 1] += t.lengths[i]
            } else {
                outNotes += notes[i]
                outLengths += t.lengths[i]
            }
            map[i] = outNotes.size - 1
        }
        return copy(notes = outNotes, timing = t.copy(lengths = outLengths)) to map
    }

    private fun fixFirstBar(): TapDraft {
        val t = timing ?: return this
        val first = minOf(t.firstBarUnits, t.lengths.sum()).coerceIn(1, t.setup.unitsPerBar)
        return if (first == t.firstBarUnits) this else copy(timing = t.copy(firstBarUnits = first))
    }
}

/**
 * A [TapDraft]'s rhythm: [lengths] in grid units of [setup], one per item of
 * the draft's notes (rests too), and [firstBarUnits], the first bar's length
 * (less than a bar for a pickup).
 */
data class TapTiming(val setup: RhythmSetup, val lengths: List<Int>, val firstBarUnits: Int)

/**
 * The ABC form of a [TapDraft], and back. Every form has the whistle's
 * major key and its `%whistle_tab_key` directive, four bars to a line.
 *
 * - Untimed: every note a quarter, four to a 4/4 bar (`L:1/4`).
 * - Timed ([TapDraft.timing]): the meter, a unit of the grid (`L:1/8` or
 *   `L:1/16`) and a `Q:` tempo. Notes that cross a bar line, or whose length
 *   isn't one ABC note, are tied pieces; a space at each beat groups the
 *   beams.
 *
 * [parse] accepts only text in these forms, so any other tune stays with
 * the text editor.
 */
object TapTune {

    const val PER_BAR = 4
    const val BARS_PER_LINE = 4

    private const val METER = "4/4"
    private const val UNIT = "1/4"
    private val OWN_FIELDS = setOf('X', 'M', 'L', 'K')

    /** Lengths, in units, that one ABC note can have here, longest first. */
    private val WRITABLE = listOf(16, 12, 8, 6, 4, 3, 2, 1)

    fun toAbc(draft: TapDraft): String {
        draft.timing?.let { return timed(draft, it) }
        val key = draft.whistle.letter
        val bars = draft.notes.ifEmpty { listOf(null) }.chunked(PER_BAR).map { bar ->
            AbcNotes.spellBar(bar.map { offset -> offset?.let { draft.whistle.writtenTonic + it } }, key).joinToString(" ")
        }
        return withHeader(draft, listOf("M:$METER", "L:$UNIT"), bars)
    }

    /** One written note or rest: [midi] null for a rest, [tie] to the next piece. */
    private data class Piece(val midi: Int?, val units: Int, val tie: Boolean, val atBeat: Boolean)

    private fun timed(draft: TapDraft, timing: TapTiming): String {
        val setup = timing.setup
        val key = draft.whistle.letter
        val bar = setup.unitsPerBar
        val beat = setup.unitsPerBeat

        // Positions count from the start of the first (maybe partial) bar's full length.
        var pos = bar - timing.firstBarUnits
        val bars = mutableListOf<MutableList<Piece>>(mutableListOf())
        for ((i, offset) in draft.notes.withIndex()) {
            val midi = offset?.let { draft.whistle.writtenTonic + it }
            var left = timing.lengths.getOrElse(i) { beat }
            while (left > 0) {
                val inBar = Math.floorMod(pos, bar)
                if (inBar == 0 && bars.last().isNotEmpty()) bars += mutableListOf<Piece>()
                val length = WRITABLE.first { it <= minOf(left, bar - inBar) }
                left -= length
                bars.last() += Piece(midi, length, tie = midi != null && left > 0, atBeat = inBar % beat == 0)
                pos += length
            }
        }

        val written = bars.filter { it.isNotEmpty() }.map { pieces ->
            val names = AbcNotes.spellBar(pieces.map { it.midi }, key)
            pieces.indices.joinToString("") { i ->
                val p = pieces[i]
                val gap = if (i > 0 && p.atBeat) " " else ""
                gap + names[i] + (if (p.units == 1) "" else p.units.toString()) + (if (p.tie) "-" else "")
            }
        }
        return withHeader(
            draft,
            listOf("M:" + setup.meter.abc, "L:" + setup.grid.unitLength, "Q:${tempoNote(setup.meter)}=${setup.bpm}"),
            written.ifEmpty { listOf("z") },
        )
    }

    /** What the `Q:` tempo counts: the beat, a dotted quarter in compound meters. */
    private fun tempoNote(meter: Meter): String = if (meter.compound) "3/8" else "1/4"

    /**
     * A whole tune from [draft]'s title, extra headers and whistle, the
     * [fields] (such as `M:` and `L:`) before its `K:`, and the bars of its
     * body, [BARS_PER_LINE] to a line.
     */
    private fun withHeader(draft: TapDraft, fields: List<String>, bars: List<String>): String {
        val body = bars.chunked(BARS_PER_LINE).joinToString(" |\n") { it.joinToString(" | ") }
        val header = buildList {
            add("X:1")
            add("T:" + draft.title.lines().joinToString(" ").trim())
            addAll(draft.extraHeaders)
            addAll(fields)
            add("K:" + draft.whistle.letter)
        }
        return AbcBook.withWhistle(header.joinToString("\n") + "\n$body |]\n", draft.whistle)
    }

    /** The draft [abc] was written from, or null if it isn't a tune in one of [toAbc]'s forms. */
    fun parse(abc: String): TapDraft? {
        val headers = AbcBook.headers(abc)
        val setup = when (headers.unitLength) {
            UNIT -> null.also { if (headers.meter != METER) return null }
            else -> {
                val grid = RhythmGrid.entries.firstOrNull { it.unitLength == headers.unitLength } ?: return null
                val meter = Meter.entries.firstOrNull { it.abc == headers.meter } ?: return null
                RhythmSetup(meter = meter, grid = grid)
            }
        }
        val key = headers.key?.takeIf { it in AbcNotes.KEYS } ?: return null
        val whistle = headers.whistle ?: AbcBook.suggestWhistle(key) ?: return null

        val lines = abc.removePrefix("﻿").lines().map { it.trimEnd('\r') }.filterNot(::isWhistleLine)
        val keyLine = lines.indexOfFirst { it.startsWith("K:") }
        val own = if (setup == null) OWN_FIELDS else OWN_FIELDS + 'Q'
        var titleSeen = false
        var tempo: String? = null
        val extra = lines.subList(0, keyLine).filter { line ->
            val field = FIELD.find(line)?.groupValues?.get(1)?.get(0)
            if (field == 'Q' && tempo == null) tempo = line.substring(2).trim()
            when {
                line.isBlank() -> false
                field == 'T' && !titleSeen -> { titleSeen = true; false }
                field in own -> false
                else -> true
            }
        }

        val body = lines.drop(keyLine + 1).joinToString(" ").trim()
        if (!body.endsWith("|]") || body.dropLast(2).contains("|]")) return null
        val bars = body.dropLast(2).split("|").map { it.trim() }
        val draft = if (setup == null) {
            val notes = untimedNotes(bars, key, whistle) ?: return null
            TapDraft(headers.title.orEmpty(), whistle, notes, extra)
        } else {
            val bpm = tempo?.removePrefix(tempoNote(setup.meter) + "=")?.takeIf { it != tempo }?.toIntOrNull()
                ?.takeIf { it > 0 } ?: return null
            val timed = timedNotes(bars, key, whistle, setup.copy(bpm = bpm)) ?: return null
            TapDraft(headers.title.orEmpty(), whistle, timed.first, extra, timed.second)
        }
        return draft
    }

    private fun untimedNotes(bars: List<String>, key: String, whistle: WhistleKey): List<Int?>? {
        val notes = mutableListOf<Int?>()
        for (bar in bars) {
            val midis = AbcNotes.parseBar(bar.split(" ").filter { it.isNotEmpty() }, key) ?: return null
            for (midi in midis) notes += midi?.let { offsetOf(it, whistle) ?: return null }
        }
        return notes
    }

    /**
     * The notes and timing of a timed body: tied pieces of one pitch join up,
     * also across bar lines, and the bars must line up (only the first and
     * last may be short).
     */
    private fun timedNotes(bars: List<String>, key: String, whistle: WhistleKey, setup: RhythmSetup): Pair<List<Int?>, TapTiming>? {
        val notes = mutableListOf<Int?>()
        val lengths = mutableListOf<Int>()
        var tied = false
        var firstBar = 0
        for ((b, bar) in bars.withIndex()) {
            val text = bar.filterNot { it.isWhitespace() }
            val pitches = mutableListOf<String>()
            val units = mutableListOf<Int>()
            val ties = mutableListOf<Boolean>()
            var at = 0
            while (at < text.length) {
                val m = PIECE.matchAt(text, at) ?: return null
                val (pitch, length, tie) = m.destructured
                if (pitch == "z" && tie.isNotEmpty()) return null
                pitches += pitch
                units += if (length.isEmpty()) 1 else length.toIntOrNull()?.takeIf { it > 0 } ?: return null
                ties += tie.isNotEmpty()
                at = m.range.last + 1
            }
            val sum = units.sum()
            val full = setup.unitsPerBar
            when {
                sum == 0 || sum > full -> return null
                sum < full && b != 0 && b != bars.lastIndex -> return null
            }
            if (b == 0) firstBar = sum
            val midis = AbcNotes.parseBar(pitches, key) ?: return null
            for (i in midis.indices) {
                val offset = midis[i]?.let { offsetOf(it, whistle) ?: return null }
                when {
                    tied -> {
                        if (offset == null || offset != notes.last()) return null
                        lengths[lengths.size - 1] += units[i]
                    }
                    offset == null && notes.isNotEmpty() && notes.last() == null -> lengths[lengths.size - 1] += units[i]
                    else -> {
                        notes += offset
                        lengths += units[i]
                    }
                }
                tied = ties[i]
            }
        }
        if (tied) return null
        return notes to TapTiming(setup, lengths, firstBar)
    }

    /** [midi] as an offset above [whistle]'s bottom note, if it has a fingering. */
    private fun offsetOf(midi: Int, whistle: WhistleKey): Int? =
        (midi - whistle.writtenTonic).takeIf { FingeringEngine.fingeringAt(it, CNatural.DEFAULT) != null }

    private val FIELD = Regex("""^([A-Za-z]):""")

    /** A note or rest with its length in units and an optional tie. */
    private val PIECE = Regex("""([_^=]?[A-Ga-g][,']*|z)(\d*)(-?)""")

    /** [toAbc] writes the directive itself. */
    private fun isWhistleLine(line: String): Boolean = line.startsWith("%whistle_tab_key")
}

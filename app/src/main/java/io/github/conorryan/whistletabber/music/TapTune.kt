package io.github.conorryan.whistletabber.music

/**
 * What a note carries as written, besides its pitch and length: [prefix]
 * (decorations such as `~` or `!roll!`, grace notes `{g}`, chord symbols
 * `"G"`, a slur's `(`) and [suffix] (a slur's `)`, a tie to another pitch).
 */
data class NoteMarks(val prefix: String = "", val suffix: String = "")

/**
 * Something in a tune's body between notes, kept as written: before the item
 * at [gap] (`notes.size`: after the last one).
 */
data class Anchor(val gap: Int, val kind: Kind, val text: String) {
    enum class Kind {
        /** A bar line that isn't worked out from the lengths: `|:`, `:|`, `||`, `[2`, or any bar line in a free tune. */
        BAR,

        /** The end of a music line; [text] is `\` for a continued one. */
        BREAK,

        /** A whole line in the body: `P:B`, `w:` lyrics, a `%` comment. */
        LINE,

        /** Anything else in the music: a decoration with no note after it, `[P:A]`, a comment at the end of a line. */
        TEXT,
    }

    /** The note strip shows it, and the cursor can stop on either side of it. */
    val visible: Boolean get() = kind == Kind.BAR
}

/** What reading a tune into a [TapDraft] couldn't keep. */
enum class Loss {
    /** Chords: the top note is kept. */
    CHORDS,

    /** A second voice or a voice overlay: only the first is kept. */
    VOICES,

    /** A key change in the tune: the pitches are kept, written in the first key. */
    KEY_CHANGE,

    /** A meter change in the tune: the bar lines are kept as written under the first meter. */
    METER_CHANGE,

    /** Notes out of the whistle's range, moved by octaves. */
    FOLDED,

    /** Lengths (odd tuplets) rounded to the nearest tick. */
    ROUNDED,

    /** Parts that couldn't be read: an unknown key change, notes with no fingering. */
    UNREADABLE,
}

/** A tune read for the tap editor, and what was lost on the way ([TapTune.read]). */
data class TapRead(val draft: TapDraft, val losses: Set<Loss>)

/**
 * A tune written by tapping fingerings: [notes] are semitones above
 * [whistle]'s bottom note ([FingeringEngine] offsets), null for a rest.
 * Storing fingerings, not pitches, lets the whistle change without
 * changing what is played.
 *
 * @property extraHeaders header lines [TapTune] doesn't write itself (such as
 *   `Z:` or `R:`), kept so a tune edited as text loses nothing
 * @property timing the rhythm, one length per item of [notes]; null while
 *   every note is an equal quarter
 * @property key the tune's `K:` as written, when it isn't [whistle]'s major
 *   key (`Em`, `Ador`); it moves with the whistle
 * @property marks each item's [NoteMarks] (empty: none at all)
 * @property anchors what sits between the items, in order
 */
data class TapDraft(
    val title: String,
    val whistle: WhistleKey,
    val notes: List<Int?>,
    val extraHeaders: List<String> = emptyList(),
    val timing: TapTiming? = null,
    val key: String? = null,
    val marks: List<NoteMarks?> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
) {
    /** The `K:` value written. */
    val keyText: String get() = key ?: whistle.letter

    // ------------------------------------------------------------- the cursor

    /*
     * The cursor sits in a slot: between two of the items and visible
     * anchors, in order. Without anchors a slot is a note index.
     */

    private fun visibleAt(gap: Int): Int = anchors.count { it.gap == gap && it.visible }

    /** The slot before everything at [gap], plus [k] of its visible anchors. */
    fun slotOf(gap: Int, k: Int = 0): Int = gap + anchors.count { it.gap < gap && it.visible } + k

    /** The slot just after item [index]. */
    fun slotAfter(index: Int): Int = slotOf(index + 1)

    /** The last slot. */
    val lastSlot: Int get() = slotOf(notes.size, visibleAt(notes.size))

    /** Where the cursor goes on opening: after the last note, before what closes the tune. */
    val endSlot: Int get() = if (notes.isEmpty()) lastSlot else slotAfter(notes.lastIndex)

    /** The gap [slot] is in, and how many of that gap's visible anchors come before it. */
    fun gapOf(slot: Int): Pair<Int, Int> {
        var start = 0
        for (gap in 0..notes.size) {
            val visible = visibleAt(gap)
            if (slot <= start + visible) return gap to (slot - start).coerceAtLeast(0)
            start += visible + 1
        }
        return notes.size to visibleAt(notes.size)
    }

    /** The anchors at [gap] up to and including its [k]th visible one. */
    private fun anchorsBefore(gap: Int, k: Int): Int {
        if (k == 0) return 0
        val at = anchors.filter { it.gap == gap }
        var seen = 0
        for ((i, a) in at.withIndex()) {
            if (a.visible) seen++
            if (seen == k) return i + 1
        }
        return at.size
    }

    // ------------------------------------------------------------- editing

    /**
     * The notes with a tapped [rhythm] (from [RhythmFit], whose events index
     * the sounding notes): the draft's own rests give way to the rests the
     * fit found, and what sat between the notes stays with the note after it.
     * A free tune's plain bar lines go: the new rhythm places them.
     */
    fun withRhythm(rhythm: Rhythm, setup: RhythmSetup): TapDraft {
        val soundingIndex = notes.indices.filter { notes[it] != null }
        val events = rhythm.events.filter { e -> e.note == null || e.note in soundingIndex.indices }
        val old = timing
        val dropPlainBars = old?.free == true
        // Each old gap goes before the event of the first sounding note at or after it.
        val eventOfSounding = IntArray(soundingIndex.size) { -1 }
        events.forEachIndexed { k, e -> if (e.note != null) eventOfSounding[e.note] = k }
        fun newGap(gap: Int): Int {
            val s = soundingIndex.indexOfFirst { it >= gap }
            return if (s < 0 || eventOfSounding[s] < 0) events.size else eventOfSounding[s]
        }
        val moved = anchors.filterNot { dropPlainBars && it.kind == Anchor.Kind.BAR && it.text == "|" }
            .map { it.copy(gap = newGap(it.gap)) }
        val newMarks = events.map { e -> e.note?.let { marks.getOrNull(soundingIndex[it]) } }
        val unit = if (old != null && !old.free) old.unitLength else setup.grid.unitLength
        return copy(
            notes = events.map { e -> e.note?.let { notes[soundingIndex[it]] } },
            timing = TapTiming(setup, events.map { it.ticks }, rhythm.firstBarTicks, unit),
            marks = newMarks.takeIf { m -> m.any { it != null } }.orEmpty(),
            anchors = moved,
        ).normalized()
    }

    /**
     * Inserts [note] (null: a rest) at [slot]. In a timed draft it is as long
     * as the item before it, or a beat at the start. Returns the draft and the
     * slot just after the new item.
     */
    fun inserted(slot: Int, note: Int?): Pair<TapDraft, Int> {
        val (gap, k) = gapOf(slot.coerceIn(0, lastSlot))
        val split = anchorsBefore(gap, k)
        var seen = 0
        val newAnchors = anchors.map { a ->
            when {
                a.gap < gap -> a
                a.gap > gap -> a.copy(gap = a.gap + 1)
                else -> (if (seen++ < split) a else a.copy(gap = gap + 1))
            }
        }
        val newNotes = notes.toMutableList().apply { add(gap, note) }
        val newMarks = if (marks.isEmpty()) marks else marks.toMutableList().apply { add(gap, null) }
        val t = timing
        val draft = if (t == null) {
            copy(notes = newNotes, marks = newMarks, anchors = newAnchors)
        } else {
            val length = t.lengths.getOrNull(gap - 1) ?: t.setup.ticksPerBeat
            copy(notes = newNotes, marks = newMarks, anchors = newAnchors, timing = t.copy(lengths = t.lengths.toMutableList().apply { add(gap, length) }))
        }
        val (merged, map) = draft.mergedRests()
        val fixed = merged.fixFirstBar()
        return fixed to fixed.slotAfter(map[gap])
    }

    /**
     * Removes the item before [slot], with its length; anchors between it and
     * the cursor stay. Returns the draft and where the cursor goes.
     */
    fun removedBefore(slot: Int): Pair<TapDraft, Int> {
        val (gap, k) = gapOf(slot.coerceIn(0, lastSlot))
        if (gap == 0) return this to slot.coerceIn(0, lastSlot)
        val index = gap - 1
        val kept = visibleAt(index) + k
        val newAnchors = anchors.map { a -> if (a.gap > index) a.copy(gap = a.gap - 1) else a }
        val newNotes = notes.toMutableList().apply { removeAt(index) }
        val newMarks = if (marks.isEmpty()) marks else marks.toMutableList().apply { removeAt(index) }.takeIf { m -> m.any { it != null } }.orEmpty()
        val t = timing
        val draft = when {
            t == null -> copy(notes = newNotes, marks = newMarks, anchors = newAnchors)
            newNotes.isEmpty() -> copy(notes = newNotes, marks = newMarks, anchors = newAnchors, timing = null)
            else -> copy(notes = newNotes, marks = newMarks, anchors = newAnchors, timing = t.copy(lengths = t.lengths.toMutableList().apply { removeAt(index) }))
        }
        val (merged, map) = draft.mergedRests()
        val fixed = merged.fixFirstBar()
        val newGap = if (index == 0) 0 else map[index - 1] + 1
        return fixed to fixed.slotOf(newGap, kept)
    }

    /** Removes the item at [index], with its length. Returns the draft and where the cursor goes. */
    fun removed(index: Int): Pair<TapDraft, Int> =
        if (index !in notes.indices) this to slotOf(index.coerceIn(0, notes.size)) else removedBefore(slotAfter(index))

    /** Keeps the fingerings; the key moves with the whistle (`Em` on a D whistle is `Am` on a G). */
    fun withWhistle(whistle: WhistleKey): TapDraft {
        val moved = key?.let { AbcNotes.transposeKey(it, whistle.writtenTonic - this.whistle.writtenTonic) ?: it }
        return copy(whistle = whistle, key = moved?.takeIf { it != whistle.letter })
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

    /** Adjacent rests merged into one (unless something sits between them), and where each old item went. */
    private fun mergedRests(): Pair<TapDraft, IntArray> {
        val t = timing ?: return this to IntArray(notes.size) { it }
        val outNotes = mutableListOf<Int?>()
        val outLengths = mutableListOf<Int>()
        val outMarks = mutableListOf<NoteMarks?>()
        val map = IntArray(notes.size)
        for (i in notes.indices) {
            val mark = marks.getOrNull(i)
            val merge = notes[i] == null && outNotes.isNotEmpty() && outNotes.last() == null &&
                anchors.none { it.gap == i } && mark?.prefix.isNullOrEmpty() && outMarks.last()?.suffix.isNullOrEmpty()
            if (merge) {
                outLengths[outLengths.size - 1] += t.lengths[i]
                if (mark != null) outMarks[outMarks.size - 1] = NoteMarks(outMarks.last()?.prefix.orEmpty(), mark.suffix)
            } else {
                outNotes += notes[i]
                outLengths += t.lengths[i]
                outMarks += mark
            }
            map[i] = outNotes.size - 1
        }
        if (outNotes.size == notes.size) return this to map
        val newAnchors = anchors.map { a -> a.copy(gap = if (a.gap >= notes.size) outNotes.size else map[a.gap]) }
        return copy(
            notes = outNotes,
            timing = t.copy(lengths = outLengths),
            marks = outMarks.takeIf { m -> m.any { it != null } }.orEmpty(),
            anchors = newAnchors,
        ) to map
    }

    private fun fixFirstBar(): TapDraft {
        val t = timing ?: return this
        if (t.free) return this
        val first = minOf(t.firstBarTicks, t.lengths.sum()).coerceIn(1, t.setup.ticksPerBar)
        return if (first == t.firstBarTicks) this else copy(timing = t.copy(firstBarTicks = first))
    }
}

/**
 * A [TapDraft]'s rhythm: [lengths] in ticks ([TICKS_PER_WHOLE] to a whole
 * note), one per item of the draft's notes (rests too), and [firstBarTicks],
 * the first bar's length (less than a bar for a pickup).
 *
 * @property setup the meter, and what the tapper starts from (tempo, grid)
 * @property unitLength the `L:` written
 * @property tempo the `Q:` value written, or none
 * @property freeMeter for a free tune, the `M:` written (`none`, `7/8`, or a
 *   meter its bar lines don't follow): bar lines are kept as anchors, not
 *   worked out
 */
data class TapTiming(
    val setup: RhythmSetup,
    val lengths: List<Int>,
    val firstBarTicks: Int,
    val unitLength: String = setup.grid.unitLength,
    val tempo: String? = "${setup.meter.tempoNote}=${setup.bpm}",
    val freeMeter: String? = null,
) {
    val free: Boolean get() = freeMeter != null
}

/**
 * The ABC form of a [TapDraft], and back ([AbcBody] does the body).
 *
 * - Untimed: every note a quarter, four to a 4/4 bar (`L:1/4`).
 * - Timed ([TapDraft.timing]): the meter, the unit length and a `Q:` tempo.
 *   Notes that cross a bar line, or whose length isn't one ABC note, are
 *   tied pieces; a space at each beat groups the beams; dotted pairs that
 *   need fractions are written `A>B`, triplets `(3ABC`.
 *
 * Any tune with a key and a whistle reads ([read]); [parse] only gives a draft
 * when nothing is lost, so saving it writes the same music back.
 */
object TapTune {

    const val PER_BAR = 4
    const val BARS_PER_LINE = 4

    fun toAbc(draft: TapDraft): String = AbcBody.write(draft)

    /**
     * The draft [abc] reads as, and what it couldn't keep; null if it has no
     * key or whistle the editor can use. Reading text from anywhere, it never
     * throws: a surprise is null, the same as unreadable.
     */
    fun read(abc: String): TapRead? = runCatching { AbcBody.read(abc) }.getOrNull()

    /** The draft [abc] reads as without losing anything, or null. */
    fun parse(abc: String): TapDraft? = read(abc)?.takeIf { it.losses.isEmpty() }?.draft

    /** For each item, whether a worked-out bar line comes before it. */
    fun barStarts(draft: TapDraft): BooleanArray = AbcBody.barStarts(draft)

    /**
     * The sounding notes the tune jumps on after (the last before a later
     * ending, `[2`), as indexes into the sounding notes: tapping goes on from
     * there with no legato or rest.
     */
    fun breaks(draft: TapDraft): Set<Int> {
        val sounding = draft.notes.indices.filter { draft.notes[it] != null }
        return draft.anchors
            .filter { it.kind == Anchor.Kind.BAR && (AbcBody.endingOf(it.text) ?: 1) != 1 }
            .mapNotNull { a -> sounding.indexOfLast { it < a.gap }.takeIf { it >= 0 } }
            .toSet()
    }

    /**
     * A whole tune from [draft]'s title, extra headers and key, [timing]'s
     * `M:`, `L:` and `Q:` before its `K:`, and the [body].
     */
    internal fun withHeader(draft: TapDraft, timing: TapTiming, body: String): String {
        val header = buildList {
            add("X:1")
            add("T:" + draft.title.lines().joinToString(" ").trim())
            addAll(draft.extraHeaders)
            add("M:" + (timing.freeMeter ?: timing.setup.meter.abc))
            add("L:" + timing.unitLength)
            timing.tempo?.let { add("Q:$it") }
            add("K:" + draft.keyText)
        }
        return AbcBook.withWhistle(header.joinToString("\n") + "\n$body\n", draft.whistle)
    }
}

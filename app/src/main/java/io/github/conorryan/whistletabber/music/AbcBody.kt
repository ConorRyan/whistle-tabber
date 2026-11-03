package io.github.conorryan.whistletabber.music

/**
 * The body of a tune as [TapDraft] holds it, read from ABC and written back.
 * Notes and rests become items with lengths in ticks ([TICKS_PER_WHOLE] to a
 * whole note); everything else in the music is kept as written, either on a
 * note ([NoteMarks]: decorations, grace notes, chord symbols, slurs) or
 * between notes ([Anchor]: bar lines that aren't plain, line breaks, body
 * lines such as `P:` or `w:`).
 *
 * Plain bar lines are worked out from the lengths and the meter, so that a
 * new rhythm moves them. A tune whose bar lines don't follow its meter (or
 * that has none, `M:none`) keeps every bar line as written instead
 * ([TapTiming.freeMeter]).
 */
internal object AbcBody {

    // ---------------------------------------------------------------- reading

    fun read(abc: String): TapRead? {
        val headers = AbcBook.headers(abc)
        val keyText = headers.key ?: return null
        if (AbcNotes.signature(keyText) == null) return null
        val whistle = headers.whistle ?: AbcBook.suggestWhistle(keyText) ?: return null

        val lines = abc.removePrefix("\uFEFF").lines().map { it.trimEnd('\r') }.filterNot(::isWhistleLine)
        val keyLine = lines.indexOfFirst { it.startsWith("K:") }
        if (keyLine < 0) return null

        val meterText = headers.meter
        val meter = Meter.of(meterText)
        val unitText = headers.unitLength ?: defaultUnit(meterText)
        val unit = ticksOf(unitText) ?: return null

        var tempo: String? = null
        var titleSeen = false
        val header = mutableListOf<String>()
        for (line in lines.subList(0, keyLine)) {
            val field = FIELD.find(line)?.groupValues?.get(1)?.get(0)
            when {
                line.isBlank() -> {}
                field == 'T' && !titleSeen -> titleSeen = true
                field == 'Q' && tempo == null -> tempo = stripComment(line.substring(2))
                field != null && field in OWN_FIELDS -> {}
                else -> header += line
            }
        }

        val body = lines.drop(keyLine + 1).let { rest ->
            val end = rest.indexOfFirst { it.isBlank() }
            if (end < 0) rest else rest.subList(0, end)
        }
        val reader = Reader(keyText, unit, meterText, meter, whistle)
        for (line in body) reader.line(line)
        val losses = reader.losses
        val raw = reader.finish()

        val setupMeter = meter ?: Meter.FOUR_FOUR
        val bar = setupMeter.ticksPerBar
        val firstBar = if (meter != null && Loss.METER_CHANGE !in losses) consistentFirstBar(raw, bar) else null
        val free = firstBar == null

        val built = build(raw, free)
        val extras = if (Loss.VOICES in losses) header.filterNot { FIELD.find(it)?.groupValues?.get(1) == "V" } else header

        val grid = when (unitText) {
            RhythmGrid.EIGHTH.unitLength -> RhythmGrid.EIGHTH
            RhythmGrid.SIXTEENTH.unitLength -> RhythmGrid.SIXTEENTH
            else -> if (built.lengths.all { it % RhythmGrid.EIGHTH.ticks == 0 }) RhythmGrid.EIGHTH else RhythmGrid.SIXTEENTH
        }
        val bpm = (tempo?.let { bpmOf(it, setupMeter, unit) } ?: RhythmSetup().bpm).coerceIn(RhythmSetup.MIN_BPM, RhythmSetup.MAX_BPM)
        val setup = RhythmSetup(bpm = bpm, meter = setupMeter, grid = grid)
        val timing = TapTiming(
            setup = setup,
            lengths = built.lengths,
            firstBarTicks = firstBar ?: bar,
            unitLength = unitText,
            tempo = tempo,
            freeMeter = if (free) meterText ?: "none" else null,
        )
        val untimed = !free && meter == Meter.FOUR_FOUR && unitText == UNTIMED_UNIT && tempo == null &&
            built.lengths.all { it == UNTIMED_TICKS } && (timing.firstBarTicks == bar || timing.firstBarTicks == built.lengths.sum())
        val read = TapDraft(
            title = headers.title.orEmpty(),
            whistle = whistle,
            notes = built.notes,
            extraHeaders = extras,
            timing = if (untimed) null else timing,
            key = keyText.takeIf { it != whistle.letter },
            marks = built.marks.takeIf { m -> m.any { it != null } }.orEmpty(),
            anchors = built.anchors,
        )
        return TapRead(withoutAutoLines(read), losses)
    }

    /** [draft] without its line breaks, if they are just where the writer would put them anyway. */
    private fun withoutAutoLines(draft: TapDraft): TapDraft {
        val breaks = draft.anchors.filter { it.kind == Anchor.Kind.BREAK }
        if (breaks.isEmpty() || !autoLines(draft.copy(anchors = draft.anchors - breaks.toSet()))) return draft
        if (breaks.any { it.text.isNotEmpty() }) return draft
        val bare = draft.copy(anchors = draft.anchors.filter { it.kind != Anchor.Kind.BREAK })
        // A break the writer puts inside the last note lands after it, where reading drops it.
        val auto = lay(bare).autoGaps.filter { it < draft.notes.size }
        return if (auto == breaks.map { it.gap }) bare else draft
    }

    /** The writer puts in line breaks itself: four bars to a line. */
    private fun autoLines(draft: TapDraft): Boolean = draft.anchors.none {
        it.kind == Anchor.Kind.BREAK || it.kind == Anchor.Kind.LINE || (it.kind == Anchor.Kind.TEXT && it.text.startsWith("%"))
    }

    /** One piece of the body as read, before ties and rests are joined up. */
    private sealed interface Raw {
        class Note(val offset: Int?, var ticks: Int, val prefix: String) : Raw {
            var suffix = ""
            var tie = false
        }
        data class Bar(val text: String) : Raw {
            val plain: Boolean get() = text == "|"
        }
        data class Other(val kind: Anchor.Kind, val text: String) : Raw
    }

    private class Reader(
        private var key: String,
        private var unit: Int,
        private val meterText: String?,
        private val meter: Meter?,
        private val whistle: WhistleKey,
    ) {
        val losses = mutableSetOf<Loss>()
        private val raw = mutableListOf<Raw>()
        private val prefix = StringBuilder()
        private var last: Raw.Note? = null
        private var keyAltered = AbcNotes.altered(key)
        private val barAltered = mutableMapOf<Pair<Int, Int>, Int>()

        // Tuplets: the next [tupletLeft] notes are q/p as long.
        private var tupletLeft = 0
        private var tupletP = 1
        private var tupletQ = 1

        // Broken rhythm: the next note's length times num/den.
        private var brokenNum = 1
        private var brokenDen = 1

        private var firstVoice: String? = null
        private var skipping = false
        private val voiceLines = mutableListOf<Raw>()

        fun finish(): List<Raw> {
            flushPrefix()
            val out = if (Loss.VOICES in losses) raw.filterNot { it in voiceLines } else raw
            return out
        }

        fun line(text: String) {
            val field = FIELD.find(text)
            if (field != null) {
                field(field.groupValues[1][0], stripComment(field.groupValues[2]), text)
                return
            }
            if (skipping) {
                // Inline voice changes can still bring the first voice back.
                Regex("""\[V:\s*([^\]\s]*)[^\]]*]""").findAll(text).lastOrNull()?.let { voice(it.groupValues[1]) }
                return
            }
            if (text.startsWith("%")) {
                flushPrefix()
                raw += Raw.Other(Anchor.Kind.LINE, text)
                return
            }
            if (text.isBlank()) return
            var continued = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (skipping) {
                    val m = INLINE_FIELD.matchAt(text, i)
                    if (m != null && m.groupValues[1] == "V") voice(m.groupValues[2].trim().substringBefore(' '))
                    i = if (m != null) m.range.last + 1 else i + 1
                    continue
                }
                when {
                    c.isWhitespace() -> i++
                    c == '%' -> {
                        flushPrefix()
                        raw += Raw.Other(Anchor.Kind.TEXT, text.substring(i).trimEnd())
                        i = text.length
                    }
                    c == '\\' && text.substring(i + 1).isBlank() -> {
                        continued = true
                        i = text.length
                    }
                    c == '"' -> i = quoted(text, i, '"')
                    (c == '!' || c == '+') && text.indexOf(c, i + 1) > i -> i = quoted(text, i, c)
                    c == '{' -> {
                        val end = text.indexOf('}', i).let { if (it < 0) text.length - 1 else it }
                        prefix.append(text, i, end + 1)
                        i = end + 1
                    }
                    isBarStart(text, i) -> i = bar(text, i)
                    c == '[' && text.getOrNull(i + 1)?.isDigit() == true -> i = bar(text, i)
                    c == '[' && INLINE_FIELD.matchAt(text, i) != null -> {
                        val m = INLINE_FIELD.matchAt(text, i)!!
                        inlineField(m.groupValues[1][0], m.groupValues[2].trim(), m.value)
                        i = m.range.last + 1
                    }
                    c == '[' -> i = chord(text, i)
                    c == '(' && text.getOrNull(i + 1)?.isDigit() == true -> {
                        val m = TUPLET.matchAt(text, i)!!
                        tupletP = m.groupValues[1].toInt()
                        tupletQ = m.groupValues[2].toIntOrNull() ?: defaultTupletQ(tupletP)
                        tupletLeft = m.groupValues[3].toIntOrNull() ?: tupletP
                        i = m.range.last + 1
                    }
                    c == ')' -> {
                        val note = last
                        if (note != null && prefix.isEmpty()) note.suffix += ")" else prefix.append(c)
                        i++
                    }
                    c == '-' -> {
                        val note = last
                        if (note != null && prefix.isEmpty()) note.tie = true else prefix.append(c)
                        i++
                    }
                    c == '>' || c == '<' -> {
                        var n = 0
                        while (text.getOrNull(i + n) == c) n++
                        broken(c, n)
                        i += n
                    }
                    c == '&' -> {
                        // A voice overlay: only the main line is kept.
                        losses += Loss.VOICES
                        while (i < text.length && !isBarStart(text, i)) i++
                    }
                    c in REST_LETTERS -> i = rest(text, i)
                    c in NOTE_START -> i = note(text, i)
                    c in DECORATIONS -> {
                        prefix.append(c)
                        i++
                    }
                    else -> {
                        prefix.append(c)
                        i++
                    }
                }
            }
            if (!skipping) {
                flushPrefix()
                raw += Raw.Other(Anchor.Kind.BREAK, if (continued) "\\" else "")
            }
        }

        private fun field(letter: Char, value: String, line: String) {
            when (letter) {
                'V' -> {
                    voice(value.substringBefore(' '))
                    if (!skipping) {
                        val anchor = Raw.Other(Anchor.Kind.LINE, line)
                        voiceLines += anchor
                        flushPrefix()
                        raw += anchor
                    }
                }
                'K' -> if (!skipping) keyChange(value, line)
                'M' -> if (!skipping) {
                    if (value == meterText) other(Anchor.Kind.LINE, line) else losses += Loss.METER_CHANGE
                }
                'L' -> if (!skipping) ticksOf(value)?.let { unit = it }
                'X' -> {}
                else -> if (!skipping) other(Anchor.Kind.LINE, line)
            }
        }

        private fun inlineField(letter: Char, value: String, text: String) {
            when (letter) {
                'V' -> voice(value.substringBefore(' '))
                'K' -> keyChange(value, null)
                'M' -> if (value != meterText) losses += Loss.METER_CHANGE
                'L' -> ticksOf(value)?.let { unit = it }
                else -> prefix.append(text)
            }
        }

        private fun voice(id: String) {
            if (firstVoice == null) firstVoice = id
            if (id != firstVoice) losses += Loss.VOICES
            skipping = id != firstVoice
        }

        private fun keyChange(value: String, line: String?) {
            if (value == key) {
                if (line != null) other(Anchor.Kind.LINE, line)
                return
            }
            if (AbcNotes.signature(value) == null) {
                losses += Loss.UNREADABLE
                return
            }
            losses += Loss.KEY_CHANGE
            key = value
            keyAltered = AbcNotes.altered(key)
        }

        private fun other(kind: Anchor.Kind, text: String) {
            flushPrefix()
            raw += Raw.Other(kind, text)
        }

        private fun flushPrefix() {
            if (prefix.isEmpty()) return
            raw += Raw.Other(Anchor.Kind.TEXT, prefix.toString())
            prefix.clear()
        }

        /** A quoted run ("…", !…!, +…+) goes on the next note as written. */
        private fun quoted(text: String, at: Int, quote: Char): Int {
            val end = text.indexOf(quote, at + 1).let { if (it < 0) text.length - 1 else it }
            prefix.append(text, at, end + 1)
            return end + 1
        }

        private fun bar(text: String, at: Int): Int {
            var j = at
            if (text[j] == '[' || text[j] == '.') j++
            while (j < text.length && (text[j] == '|' || text[j] == ':')) j++
            if (j < text.length && text[j] == ']' && text[j - 1] == '|') j++
            var k = j
            if (k < text.length && text[k] == '[' && text.getOrNull(k + 1)?.isDigit() == true) k++
            if (k < text.length && text[k].isDigit()) {
                while (k < text.length && (text[k].isDigit() || text[k] == ',' || text[k] == '-')) k++
                j = k
            }
            flushPrefix()
            raw += Raw.Bar(text.substring(at, j))
            last = null
            barAltered.clear()
            return j
        }

        private fun broken(c: Char, n: Int) {
            val note = last ?: return
            val den = 1 shl n
            val long = 2 * den - 1
            val (first, next) = if (c == '>') long to 1 else 1 to long
            note.ticks = scaled(note.ticks, first, den)
            brokenNum = next
            brokenDen = den
        }

        private fun scaled(ticks: Int, num: Int, den: Int): Int {
            if (ticks * num % den != 0) losses += Loss.ROUNDED
            return maxOf(1, Math.round(ticks.toDouble() * num / den).toInt())
        }

        /** A length after a note or rest, in ticks of the current unit, and where it ends. */
        private fun length(text: String, at: Int): Pair<Int, Int> {
            val m = LENGTH.matchAt(text, at)!!
            val (digits, slashes, below) = m.destructured
            val num = digits.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val den = when {
                slashes.isEmpty() -> 1
                below.isNotEmpty() && slashes.length == 1 -> below.toInt().coerceAtLeast(1)
                else -> 1 shl slashes.length
            }
            return scaled(unit, num, den) to m.range.last + 1
        }

        /** Applies a pending tuplet and broken rhythm to a new note's length. */
        private fun timed(ticks: Int): Int {
            var t = ticks
            if (tupletLeft > 0) {
                t = scaled(t, tupletQ, tupletP)
                tupletLeft--
            }
            if (brokenDen != 1) {
                t = scaled(t, brokenNum, brokenDen)
                brokenNum = 1
                brokenDen = 1
            }
            return t
        }

        private fun rest(text: String, at: Int): Int {
            val c = text[at]
            if (c == 'Z') {
                val m = Regex("""\d*""").matchAt(text, at + 1)!!
                val bars = m.value.toIntOrNull() ?: 1
                add(null, bars * (meter ?: Meter.FOUR_FOUR).ticksPerBar)
                return m.range.last + 1
            }
            val (ticks, end) = length(text, at + 1)
            add(null, timed(ticks))
            return end
        }

        private fun note(text: String, at: Int): Int {
            val m = NOTE.matchAt(text, at) ?: run {
                prefix.append(text[at])
                return at + 1
            }
            val midi = pitch(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val (ticks, end) = length(text, m.range.last + 1)
            add(offsetOf(midi), timed(ticks))
            return end
        }

        /** A chord keeps its top note (the melody), as long as the chord. */
        private fun chord(text: String, at: Int): Int {
            val close = text.indexOf(']', at)
            if (close < 0) {
                prefix.append(text[at])
                return at + 1
            }
            val inner = text.substring(at + 1, close)
            val notes = NOTE.findAll(inner).toList()
            if (notes.isEmpty()) {
                prefix.append(text, at, close + 1)
                return close + 1
            }
            if (notes.size > 1) losses += Loss.CHORDS
            val pitches = notes.map { pitch(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            val innerLength = length(inner, notes.first().range.last + 1).first
            val (outer, end) = length(text, close + 1)
            add(offsetOf(pitches.max()), timed(scaled(innerLength, outer, unit)))
            return end
        }

        private fun pitch(accidental: String, name: String, marks: String): Int {
            val letter = LETTERS.indexOf(name[0].uppercaseChar())
            val octave = if (name[0].isUpperCase()) -marks.count { it == ',' } + marks.count { it == '\'' } else 1 + marks.count { it == '\'' } - marks.count { it == ',' }
            val shift = when (accidental) {
                "^^" -> 2
                "^" -> 1
                "__" -> -2
                "_" -> -1
                "=" -> 0
                else -> barAltered[letter to octave] ?: keyAltered[letter]
            }
            if (accidental.isNotEmpty()) barAltered[letter to octave] = shift
            return (octave + 5) * 12 + NATURAL_PC[letter] + shift
        }

        /** [midi] as a fingering offset on the whistle, moved by octaves if it is out of range. */
        private fun offsetOf(midi: Int): Int? {
            val offset = midi - whistle.writtenTonic
            if (playable(offset)) return offset
            val folded = (1..4).flatMap { listOf(offset + 12 * it, offset - 12 * it) }.firstOrNull(::playable)
            losses += if (folded != null) Loss.FOLDED else Loss.UNREADABLE
            return folded
        }

        private fun add(offset: Int?, ticks: Int) {
            val note = Raw.Note(offset, ticks, prefix.toString())
            prefix.clear()
            raw += note
            last = note
        }
    }

    /**
     * Whether the bar lines in [raw] fall where [bar]-tick bars would put
     * them; if so, the first bar's length (a pickup is short), else null.
     */
    private fun consistentFirstBar(raw: List<Raw>, bar: Int): Int? {
        val firstNote = raw.indexOfFirst { it is Raw.Note }
        if (firstNote < 0) return bar
        var firstBar = 0
        for (r in raw.drop(firstNote)) {
            if (r is Raw.Bar) break
            if (r is Raw.Note) firstBar += r.ticks
        }
        if (firstBar > bar) return null
        var pos = bar - firstBar
        var endingStart: Int? = null
        var started = false
        var sawBar = false
        for (r in raw) {
            when (r) {
                is Raw.Bar -> {
                    if (r.plain && started && Math.floorMod(pos, bar) != 0) return null
                    if (started) sawBar = true
                    when (endingOf(r.text)) {
                        null -> {}
                        1 -> endingStart = pos
                        else -> endingStart?.let { pos = it }
                    }
                }
                is Raw.Note -> {
                    val inBar = Math.floorMod(pos, bar)
                    if (started && inBar == 0 && !sawBar) return null
                    if (inBar + r.ticks > bar) return null
                    pos += r.ticks
                    started = true
                    sawBar = false
                }
                is Raw.Other -> {}
            }
        }
        return firstBar
    }

    private class Built(val notes: List<Int?>, val lengths: List<Int>, val marks: List<NoteMarks?>, val anchors: List<Anchor>)

    /** Items from [raw]: tied pieces of one pitch and rests in a row joined, unless something sits between them. */
    private fun build(raw: List<Raw>, free: Boolean): Built {
        val notes = mutableListOf<Int?>()
        val lengths = mutableListOf<Int>()
        val prefixes = mutableListOf<String>()
        val suffixes = mutableListOf<String>()
        val ties = mutableListOf<Boolean>()
        val anchors = mutableListOf<Anchor>()
        for (r in raw) {
            when (r) {
                is Raw.Bar -> if (free || !r.plain) anchors += Anchor(notes.size, Anchor.Kind.BAR, r.text)
                is Raw.Other -> anchors += Anchor(notes.size, r.kind, r.text)
                is Raw.Note -> {
                    val n = notes.size
                    // A line break doesn't part a tie or two rests; it moves after them.
                    val between = anchors.any { it.gap == n && it.kind != Anchor.Kind.BREAK }
                    if (n > 0 && !between && r.prefix.isEmpty() && suffixes[n - 1].isEmpty()) {
                        val joinTie = ties[n - 1] && r.offset != null && notes[n - 1] == r.offset
                        val joinRest = r.offset == null && notes[n - 1] == null && !ties[n - 1]
                        if (joinTie || joinRest) {
                            lengths[n - 1] += r.ticks
                            suffixes[n - 1] = r.suffix
                            ties[n - 1] = r.tie
                            continue
                        }
                    }
                    if (n > 0 && ties[n - 1]) {
                        suffixes[n - 1] += "-"
                        ties[n - 1] = false
                    }
                    notes += r.offset
                    lengths += r.ticks
                    prefixes += r.prefix
                    suffixes += r.suffix
                    ties += r.tie
                }
            }
        }
        if (ties.lastOrNull() == true) suffixes[suffixes.lastIndex] += "-"
        val n = notes.size
        // Line breaks after the last note, and the closing |], are what the writer adds itself.
        while (anchors.lastOrNull()?.let { it.gap == n && it.kind == Anchor.Kind.BREAK } == true) anchors.removeAt(anchors.lastIndex)
        val closing = anchors.indexOfFirst { it.gap == n && it.kind == Anchor.Kind.BAR }
        if (closing >= 0 && anchors[closing].text == "|]") anchors.removeAt(closing)
        while (anchors.lastOrNull()?.let { it.gap == n && it.kind == Anchor.Kind.BREAK } == true) anchors.removeAt(anchors.lastIndex)
        val marks = prefixes.indices.map { i -> NoteMarks(prefixes[i], suffixes[i]).takeUnless { it.prefix.isEmpty() && it.suffix.isEmpty() } }
        return Built(notes, lengths, marks, anchors)
    }

    // ---------------------------------------------------------------- writing

    /** One thing the writer puts down, before notes are spelled. */
    private sealed interface Out {
        data class Bar(val text: String) : Out
        data class Other(val kind: Anchor.Kind, val text: String) : Out
        data object Triplet : Out
        class Piece(
            val midi: Int?,
            val written: Int,
            val tie: Boolean,
            val space: Boolean,
            val prefix: String,
            val suffix: String,
            val broken: String = "",
        ) : Out {
            var name = ""
        }
    }

    fun write(draft: TapDraft): String {
        val timing = draft.timing ?: untimed(draft.notes.size)
        val out = lay(draft).out
        spell(out, draft.keyText)
        return TapTune.withHeader(draft, timing, render(out, ticksOf(timing.unitLength) ?: UNTIMED_TICKS))
    }

    /** What the writer puts down, and the gaps where it broke lines by itself. */
    private class Laid(val out: List<Out>, val autoGaps: List<Int>)

    private fun lay(draft: TapDraft): Laid {
        val timing = draft.timing ?: untimed(draft.notes.size)
        val unit = ticksOf(timing.unitLength) ?: UNTIMED_TICKS
        val writable = writable(unit)
        val free = timing.free
        val meter = timing.setup.meter
        val bar = meter.ticksPerBar
        val beat = meter.ticksPerBeat
        val notes = draft.notes
        val n = notes.size
        val autoLines = autoLines(draft)
        val autoGaps = mutableListOf<Int>()

        val out = mutableListOf<Out>()
        var pos = if (free) 0 else bar - timing.firstBarTicks
        var endingStart: Int? = null
        var started = false
        var barsOnLine = 0
        var lineDue = false

        fun inBar(): Int = if (free) pos else Math.floorMod(pos, bar)
        fun countBar() {
            // Only bar lines that end a bar count: not a [2 in the middle of one.
            if (!autoLines || (started && inBar() != 0)) return
            barsOnLine++
            if (barsOnLine == TapTune.BARS_PER_LINE) {
                barsOnLine = 0
                lineDue = true
            }
        }
        fun plainBar() {
            out += Out.Bar("|")
            countBar()
        }
        fun anchor(a: Anchor) {
            if (a.kind == Anchor.Kind.BAR) {
                out += Out.Bar(a.text)
                if (free || inBar() == 0) countBar()
                when (endingOf(a.text)) {
                    null -> {}
                    1 -> endingStart = pos
                    else -> endingStart?.let { pos = it }
                }
                if (free) pos = 0
            } else {
                out += Out.Other(a.kind, a.text)
            }
        }
        fun beforeNote(gap: Int) {
            if (lineDue) {
                out += Out.Other(Anchor.Kind.BREAK, "")
                autoGaps += gap
                lineDue = false
            }
        }
        fun midiOf(i: Int): Int? = notes[i]?.let { draft.whistle.writtenTonic + it }
        fun prefixOf(i: Int): String = draft.marks.getOrNull(i)?.prefix.orEmpty()
        fun suffixOf(i: Int): String = draft.marks.getOrNull(i)?.suffix.orEmpty()
        fun gapEmpty(g: Int): Boolean = draft.anchors.none { it.gap == g }
        fun length(i: Int): Int = timing.lengths.getOrElse(i) { meter.ticksPerBeat }
        fun fits(ticks: Int): Boolean = free || inBar() + ticks <= bar

        val byGap = draft.anchors.groupBy { it.gap }
        var i = 0
        while (i <= n) {
            val gapAnchors = byGap[i].orEmpty()
            if (i == n) {
                if (n == 0) out += Out.Other(Anchor.Kind.TEXT, "z")
                if (gapAnchors.none { it.kind == Anchor.Kind.BAR }) out += Out.Bar("|]")
                gapAnchors.forEach { out += if (it.kind == Anchor.Kind.BAR) Out.Bar(it.text) else Out.Other(it.kind, it.text) }
                break
            }
            if (!free && started && inBar() == 0 && gapAnchors.firstOrNull()?.kind != Anchor.Kind.BAR) plainBar()
            gapAnchors.forEach(::anchor)
            beforeNote(i)

            val len = length(i)
            // Three equal notes in the time of two: a triplet.
            if (i + 2 < n && len % 3 != 0 && len * 3 % 2 == 0 && len * 3 / 2 in writable &&
                length(i + 1) == len && length(i + 2) == len && gapEmpty(i + 1) && gapEmpty(i + 2) && fits(3 * len)
            ) {
                out += Out.Triplet
                for (k in 0 until 3) {
                    out += Out.Piece(midiOf(i + k), len * 3 / 2, tie = false, space = k == 0 && inBar() % beat == 0, prefix = prefixOf(i + k), suffix = suffixOf(i + k))
                    pos += len
                }
                started = true
                i += 3
                continue
            }
            // A dotted pair that needs fractions written plainly: A>B or A<B.
            if (i + 1 < n && gapEmpty(i + 1)) {
                val next = length(i + 1)
                val sign = when {
                    len == 3 * next -> ">"
                    next == 3 * len -> "<"
                    else -> null
                }
                val short = minOf(len, next)
                if (sign != null && (3 * short) % unit != 0 && 2 * short in writable && fits(len + next) && inBar() % (len + next) == 0) {
                    out += Out.Piece(midiOf(i), 2 * short, tie = false, space = inBar() % beat == 0, prefix = prefixOf(i), suffix = suffixOf(i), broken = sign)
                    out += Out.Piece(midiOf(i + 1), 2 * short, tie = false, space = false, prefix = prefixOf(i + 1), suffix = suffixOf(i + 1))
                    pos += len + next
                    started = true
                    i += 2
                    continue
                }
            }

            var left = len
            var first = true
            while (left > 0) {
                if (!first && !free && inBar() == 0) {
                    plainBar()
                    beforeNote(i + 1)
                }
                val room = if (free) left else minOf(left, bar - inBar())
                val piece = if (room % 3 != 0) room else writable.firstOrNull { it <= room } ?: room
                left -= piece
                out += Out.Piece(
                    midi = midiOf(i),
                    written = piece,
                    tie = notes[i] != null && left > 0,
                    space = inBar() % beat == 0,
                    prefix = if (first) prefixOf(i) else "",
                    suffix = if (left == 0) suffixOf(i) else "",
                )
                pos += piece
                first = false
            }
            started = true
            i++
        }

        return Laid(out, autoGaps)
    }

    /** Spells each bar's notes, bar lines of any kind ending a bar. */
    private fun spell(out: List<Out>, key: String) {
        val bar = mutableListOf<Out.Piece>()
        fun flush() {
            val names = AbcNotes.spellBar(bar.map { it.midi }, key)
            bar.forEachIndexed { k, p -> p.name = names[k] }
            bar.clear()
        }
        for (o in out) {
            if (o is Out.Bar) flush()
            if (o is Out.Piece) bar += o
        }
        flush()
    }

    private fun render(out: List<Out>, unit: Int): String {
        val sb = StringBuilder()
        var lineStart = true
        var prev: Out? = null
        fun space() {
            if (!lineStart) sb.append(' ')
        }
        for (o in out) {
            when (o) {
                is Out.Bar -> {
                    space()
                    sb.append(o.text)
                    lineStart = false
                }
                is Out.Other -> when (o.kind) {
                    Anchor.Kind.BREAK -> {
                        sb.append(o.text).append('\n')
                        lineStart = true
                    }
                    Anchor.Kind.LINE -> {
                        if (!lineStart) sb.append('\n')
                        sb.append(o.text).append('\n')
                        lineStart = true
                    }
                    else -> {
                        space()
                        sb.append(o.text)
                        lineStart = false
                    }
                }
                Out.Triplet -> {
                    space()
                    sb.append("(3")
                    lineStart = false
                }
                is Out.Piece -> {
                    val spaced = when (prev) {
                        is Out.Piece -> o.space && prev.broken.isEmpty()
                        Out.Triplet -> false
                        else -> true
                    }
                    if (spaced) space()
                    sb.append(o.prefix).append(o.name).append(lengthText(o.written, unit))
                    if (o.tie) sb.append('-')
                    sb.append(o.suffix).append(o.broken)
                    lineStart = false
                }
            }
            prev = o
        }
        return sb.toString().trimEnd('\n')
    }

    // ---------------------------------------------------------------- shared

    /**
     * For each item, whether a worked-out bar line comes before it (the first
     * item never has one). A free tune ([TapTiming.free]) has none; its bar
     * lines are anchors.
     */
    fun barStarts(draft: TapDraft): BooleanArray {
        val timing = draft.timing ?: untimed(draft.notes.size)
        val starts = BooleanArray(draft.notes.size)
        if (timing.free) return starts
        val bar = timing.setup.meter.ticksPerBar
        var pos = bar - timing.firstBarTicks
        var endingStart: Int? = null
        val byGap = draft.anchors.groupBy { it.gap }
        for (i in draft.notes.indices) {
            for (a in byGap[i].orEmpty()) {
                if (a.kind != Anchor.Kind.BAR) continue
                when (endingOf(a.text)) {
                    null -> {}
                    1 -> endingStart = pos
                    else -> endingStart?.let { pos = it }
                }
            }
            starts[i] = i > 0 && Math.floorMod(pos, bar) == 0
            pos += timing.lengths.getOrElse(i) { timing.setup.ticksPerBeat }
        }
        return starts
    }

    /** The timing of an untimed draft: equal quarters in 4/4. */
    fun untimed(count: Int): TapTiming {
        val setup = RhythmSetup(meter = Meter.FOUR_FOUR)
        return TapTiming(setup, List(count) { UNTIMED_TICKS }, setup.ticksPerBar, UNTIMED_UNIT, tempo = null)
    }

    /** The ending a bar line starts (`[1`, `:|2`): its first number, or null. */
    fun endingOf(text: String): Int? = text.firstOrNull { it.isDigit() }?.digitToInt()

    /** [text] (`1/8`) in ticks, if it is a whole number of them. */
    fun ticksOf(text: String): Int? {
        val m = Regex("""^\s*(\d+)\s*/\s*(\d+)\s*$""").find(text) ?: return null
        val (num, den) = m.destructured.toList().map { it.toInt() }
        if (den == 0 || TICKS_PER_WHOLE * num % den != 0) return null
        return TICKS_PER_WHOLE * num / den
    }

    /** [ticks] as an ABC length after a note in [unit]-tick units: "", "2", "/", "3/2". */
    fun lengthText(ticks: Int, unit: Int): String {
        val g = gcd(ticks, unit)
        val num = ticks / g
        val den = unit / g
        return when {
            den == 1 && num == 1 -> ""
            den == 1 -> "$num"
            num == 1 && den == 2 -> "/"
            num == 1 -> "/$den"
            else -> "$num/$den"
        }
    }

    /** Lengths one ABC note can have here, longest first: plain and dotted values up to 16 units. */
    private fun writable(unit: Int): List<Int> {
        val plain = (0..7).map { 3 shl it }
        val dotted = plain.drop(1).map { it * 3 / 2 }
        return (plain + dotted).filter { it <= 16 * unit }.sortedDescending()
    }

    /** The `Q:` tempo [q] as beats a minute, if it says. */
    private fun bpmOf(q: String, meter: Meter, unit: Int): Int? {
        Regex("""(\d+)\s*/\s*(\d+)\s*=\s*(\d+)""").find(q)?.let { m ->
            val (num, den, bpm) = m.destructured.toList().map { it.toInt() }
            if (den == 0) return null
            return Math.round(bpm.toDouble() * TICKS_PER_WHOLE * num / den / meter.ticksPerBeat).toInt()
        }
        val plain = q.trim().toIntOrNull() ?: return null
        return Math.round(plain.toDouble() * unit / meter.ticksPerBeat).toInt()
    }

    /** The unit length ABC assumes without `L:`: a sixteenth below 3/4, else an eighth. */
    private fun defaultUnit(meter: String?): String {
        val m = Meter.of(meter) ?: return "1/8"
        return if (m.ticksPerBar * 4 < TICKS_PER_WHOLE * 3) "1/16" else "1/8"
    }

    private fun isBarStart(text: String, i: Int): Boolean {
        val c = text[i]
        val next = text.getOrNull(i + 1)
        return c == '|' || (c == ':' && (next == ':' || next == '|')) || ((c == '[' || c == '.') && next == '|')
    }

    private fun defaultTupletQ(p: Int): Int = when (p) {
        2, 4, 8 -> 3
        3, 6 -> 2
        else -> 2
    }

    private fun playable(offset: Int): Boolean = FingeringEngine.fingeringAt(offset) != null

    private fun stripComment(value: String): String {
        val i = value.indexOf('%')
        return (if (i >= 0) value.substring(0, i) else value).trim()
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun isWhistleLine(line: String): Boolean = line.startsWith("%whistle_tab_key")

    const val UNTIMED_UNIT = "1/4"
    const val UNTIMED_TICKS = TICKS_PER_WHOLE / 4

    private const val LETTERS = "CDEFGAB"
    private val NATURAL_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val OWN_FIELDS = setOf('X', 'M', 'L', 'K', 'Q')
    private val FIELD = Regex("""^([A-Za-z]):(.*)$""")
    private val INLINE_FIELD = Regex("""\[([A-Za-z]):([^\]]*)]""")
    private val TUPLET = Regex("""\((\d+)(?::(\d*))?(?::(\d*))?""")
    private val NOTE = Regex("""(\^\^|\^|__|_|=)?([A-Ga-g])([,']*)""")
    private val LENGTH = Regex("""(\d*)(/*)(\d*)""")
    private const val NOTE_START = "^_=ABCDEFGabcdefg"
    private const val REST_LETTERS = "zxZ"
    private const val DECORATIONS = ".~HLMOPSTuvJR"
}

package io.github.conorryan.whistletabber.music

/**
 * A tune written by tapping fingerings: [notes] are semitones above
 * [whistle]'s bottom note ([FingeringEngine] offsets), null for a rest.
 * Storing fingerings, not pitches, lets the whistle change without
 * changing what is played.
 *
 * @property extraHeaders header lines [TapTune] doesn't write itself (such as
 *   `Z:` or `R:`), kept so a tune edited as text loses nothing
 */
data class TapDraft(
    val title: String,
    val whistle: WhistleKey,
    val notes: List<Int?>,
    val extraHeaders: List<String> = emptyList(),
)

/**
 * The ABC form of a [TapDraft], and back. Notes have no rhythm yet: every
 * note is a quarter, four to a 4/4 bar, four bars to a line, in the whistle's
 * major key with its `%whistle_tab_key` directive. [parse] accepts only text
 * in that form, so any other tune stays with the text editor.
 */
object TapTune {

    const val PER_BAR = 4
    const val BARS_PER_LINE = 4

    private const val METER = "4/4"
    private const val UNIT = "1/4"
    private val OWN_FIELDS = setOf('X', 'M', 'L', 'K')

    fun toAbc(draft: TapDraft): String {
        val key = draft.whistle.letter
        val bars = draft.notes.ifEmpty { listOf(null) }.chunked(PER_BAR).map { bar ->
            AbcNotes.spellBar(bar.map { offset -> offset?.let { draft.whistle.writtenTonic + it } }, key).joinToString(" ")
        }
        val body = bars.chunked(BARS_PER_LINE).joinToString(" |\n") { it.joinToString(" | ") }
        val header = buildList {
            add("X:1")
            add("T:" + draft.title.lines().joinToString(" ").trim())
            addAll(draft.extraHeaders)
            add("M:$METER")
            add("L:$UNIT")
            add("K:$key")
        }
        return AbcBook.withWhistle(header.joinToString("\n") + "\n$body |]\n", draft.whistle)
    }

    /** The draft [abc] was written from, or null if it isn't a tune in [toAbc]'s form. */
    fun parse(abc: String): TapDraft? {
        val headers = AbcBook.headers(abc)
        if (headers.meter != METER || headers.unitLength != UNIT) return null
        val key = headers.key?.takeIf { it in AbcNotes.KEYS } ?: return null
        val whistle = headers.whistle ?: AbcBook.suggestWhistle(key) ?: return null

        val lines = abc.removePrefix("﻿").lines().map { it.trimEnd('\r') }.filterNot(::isWhistleLine)
        val keyLine = lines.indexOfFirst { it.startsWith("K:") }
        var titleSeen = false
        val extra = lines.subList(0, keyLine).filter { line ->
            val field = FIELD.find(line)?.groupValues?.get(1)?.get(0)
            when {
                line.isBlank() -> false
                field == 'T' && !titleSeen -> { titleSeen = true; false }
                field in OWN_FIELDS -> false
                else -> true
            }
        }

        val tokens = lines.drop(keyLine + 1).flatMap { it.trim().split(Regex("""\s+""")) }.filter { it.isNotEmpty() }
        if (tokens.lastOrNull() != "|]" || tokens.dropLast(1).contains("|]")) return null
        val bars = tokens.dropLast(1).joinToString(" ").split("|").map { bar -> bar.trim().split(" ").filter { it.isNotEmpty() } }
        val notes = mutableListOf<Int?>()
        for (bar in bars) {
            val midis = AbcNotes.parseBar(bar, key) ?: return null
            for (midi in midis) {
                if (midi == null) {
                    notes += null
                    continue
                }
                val offset = midi - whistle.writtenTonic
                if (FingeringEngine.fingeringAt(offset, CNatural.DEFAULT) == null) return null
                notes += offset
            }
        }
        return TapDraft(headers.title.orEmpty(), whistle, notes, extra)
    }

    private val FIELD = Regex("""^([A-Za-z]):""")

    /** [toAbc] writes the directive itself. */
    private fun isWhistleLine(line: String): Boolean = line.startsWith("%whistle_tab_key")
}

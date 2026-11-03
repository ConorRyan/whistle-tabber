package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcNotes
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.FingeringOptions
import io.github.conorryan.whistletabber.music.sameShape
import io.github.conorryan.whistletabber.music.WhistleKey

/**
 * Turns a [TabReading] into ABC: one note per diagram, all the same length
 * (tab has no rhythm), one ABC line per line of tab, in the whistle's major
 * key. A diagram that matches no known fingering becomes a rest marked "?".
 * Bar lines every [GROUP] notes and at each line's end are only there so
 * the page can wrap (it breaks lines at bars); they aren't the tune's bars.
 */
object TabTune {

    /**
     * Semitones above the whistle's bottom note, or null for an unknown
     * fingering. A shape some notes share (`OXX XXX+` is a vented D and a C
     * natural) is read as the user's own fingering first, then as a default,
     * then as the lowest note offering it: a chart may use other fingerings
     * than the user's.
     */
    fun offsetOf(column: TabColumn, choices: FingeringChoices): Int? {
        val read = Fingering(column.holes, column.octave.coerceIn(0, 2))
        val offsets = 0..FingeringEngine.HIGHEST_OFFSET
        return offsets.firstOrNull { FingeringEngine.fingeringAt(it, choices)?.sameShape(read) == true }
            ?: offsets.firstOrNull { FingeringEngine.fingeringAt(it)?.sameShape(read) == true }
            ?: offsets.firstOrNull { o -> FingeringOptions.at(o).any { it.fingering.sameShape(read) } }
    }

    fun toAbc(reading: TabReading, whistle: WhistleKey, title: String, choices: FingeringChoices): String {
        val key = whistle.letter
        val body = reading.lines.joinToString(" |\n") { line ->
            line.chunked(GROUP).joinToString(" | ") { group ->
                val midis = group.map { column -> offsetOf(column, choices)?.let { whistle.writtenTonic + it } }
                AbcNotes.spellBar(midis, key, rest = UNKNOWN).joinToString(" ")
            }
        }
        val abc = "X:1\nT:$title\nM:none\nL:1/4\nK:$key\n$body |]\n"
        return AbcBook.withWhistle(abc, whistle)
    }

    /** Several screenshots of one tab, in order, as one reading: the lines follow each other. */
    fun combine(readings: List<TabReading>): TabReading = TabReading(readings.flatMap { it.lines })

    const val UNKNOWN = "\"^?\"z"

    const val GROUP = 4
}

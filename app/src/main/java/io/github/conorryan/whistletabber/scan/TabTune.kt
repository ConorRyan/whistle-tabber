package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcNotes
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.WhistleKey

/**
 * Turns a [TabReading] into ABC: one note per diagram, all the same length
 * (tab has no rhythm), one ABC line per line of tab, in the whistle's major
 * key. A diagram that matches no known fingering becomes a rest marked "?".
 * Bar lines every [GROUP] notes and at each line's end are only there so
 * the page can wrap (it breaks lines at bars); they aren't the tune's bars.
 */
object TabTune {

    /** Fingerings charts use besides the ones [FingeringEngine] draws: vented second-octave D. */
    private val ALTERNATES: Map<Fingering, Int> = mapOf(
        Fingering.parse("OXX XXX+") to 12,
    )

    /** Semitones above the whistle's bottom note, or null for an unknown fingering. */
    fun offsetOf(column: TabColumn, cNatural: CNatural): Int? {
        val read = Fingering(column.holes, column.octave.coerceIn(0, 2))
        (0..FingeringEngine.HIGHEST_OFFSET).firstOrNull { offset ->
            FingeringEngine.fingeringAt(offset, cNatural)?.let { it.holes == read.holes && it.octave == read.octave } == true
        }?.let { return it }
        ALTERNATES[read]?.let { return it }
        // A chart may use another C natural fingering than the user's.
        if (read.octave < 2 && CNatural.entries.any { it.fingering.holes == read.holes }) return 10 + 12 * read.octave
        return null
    }

    fun toAbc(reading: TabReading, whistle: WhistleKey, title: String, cNatural: CNatural): String {
        val key = whistle.letter
        val body = reading.lines.joinToString(" |\n") { line ->
            line.chunked(GROUP).joinToString(" | ") { group ->
                val midis = group.map { column -> offsetOf(column, cNatural)?.let { whistle.writtenTonic + it } }
                AbcNotes.spellBar(midis, key, rest = UNKNOWN).joinToString(" ")
            }
        }
        val abc = "X:1\nT:$title\nM:none\nL:1/4\nK:$key\n$body |]\n"
        return AbcBook.withWhistle(abc, whistle)
    }

    const val UNKNOWN = "\"^?\"z"

    const val GROUP = 4
}

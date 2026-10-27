package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.AbcBook
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
                group.joinToString(" ") { column ->
                    offsetOf(column, cNatural)?.let { spell(whistle.writtenTonic + it, key) } ?: UNKNOWN
                }
            }
        }
        val abc = "X:1\nT:$title\nM:none\nL:1/4\nK:$key\n$body |]\n"
        return AbcBook.withWhistle(abc, whistle)
    }

    const val UNKNOWN = "\"^?\"z"

    const val GROUP = 4

    private val LETTERS = "CDEFGAB"
    private val NATURAL_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private const val SHARP_ORDER = "FCGDAEB"
    private const val FLAT_ORDER = "BEADGCF"

    /** Sharps (positive) or flats (negative) in the major key [key]. */
    private fun signature(key: String): Int = when (key) {
        "C" -> 0; "G" -> 1; "D" -> 2; "A" -> 3; "E" -> 4; "B" -> 5
        "F" -> -1; "Bb" -> -2; "Eb" -> -3; "Ab" -> -4
        else -> 0
    }

    /** MIDI [midi] as an ABC note in major key [key]: scale notes plain, others with an accidental. */
    fun spell(midi: Int, key: String): String {
        val sig = signature(key)
        val altered = IntArray(7)
        if (sig > 0) SHARP_ORDER.take(sig).forEach { altered[LETTERS.indexOf(it)] = 1 }
        if (sig < 0) FLAT_ORDER.take(-sig).forEach { altered[LETTERS.indexOf(it)] = -1 }
        val pc = Math.floorMod(midi, 12)

        val inScale = (0 until 7).firstOrNull { Math.floorMod(NATURAL_PC[it] + altered[it], 12) == pc }
        val (letter, shift, accidental) = when {
            inScale != null -> Triple(inScale, altered[inScale], "")
            NATURAL_PC.contains(pc) -> Triple(NATURAL_PC.indexOf(pc), 0, "=")
            sig < 0 -> Triple(NATURAL_PC.indexOf(pc + 1), -1, "_")
            else -> Triple(NATURAL_PC.indexOf(pc - 1), 1, "^")
        }
        val octave = Math.floorDiv(midi - shift, 12) - 5
        val name = LETTERS[letter]
        return accidental + when {
            octave <= 0 -> name + ",".repeat(-octave)
            else -> name.lowercaseChar() + "'".repeat(octave - 1)
        }
    }
}

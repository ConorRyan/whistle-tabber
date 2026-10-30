package io.github.conorryan.whistletabber.music

/**
 * Single ABC notes in a major key: MIDI pitch to ABC ([spell], [spellBar])
 * and back ([parseBar]). Only plain notes are handled: an optional `^`, `_`
 * or `=`, a letter, and `,` or `'` octave marks; no lengths.
 *
 * An accidental lasts to the end of its bar. abcjs's playback applies it to
 * the same letter in the same octave (as the ABC standard says), its
 * transposer to the same letter in every octave; [spellBar] writes an
 * explicit accidental wherever the two could disagree, and [parseBar] reads
 * the standard way.
 */
object AbcNotes {

    /** A note as written: letter index into "CDEFGAB", its alteration, and an octave (0 = `C`..`B`, 1 = `c`..`b`). */
    private data class Spelling(val letter: Int, val shift: Int, val accidental: String, val octave: Int) {
        val text: String
            get() = accidental + LETTERS[letter].let { name ->
                if (octave <= 0) name + ",".repeat(-octave) else name.lowercaseChar() + "'".repeat(octave - 1)
            }
    }

    private const val LETTERS = "CDEFGAB"
    private val NATURAL_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private const val SHARP_ORDER = "FCGDAEB"
    private const val FLAT_ORDER = "BEADGCF"
    private val SIGNATURES = mapOf(
        "C" to 0, "G" to 1, "D" to 2, "A" to 3, "E" to 4, "B" to 5,
        "F" to -1, "Bb" to -2, "Eb" to -3, "Ab" to -4,
    )
    private val NOTE = Regex("""^([_^=]?)([A-Ga-g])([,']*)$""")

    /** Major keys these functions know, as `K:` values. */
    val KEYS: Set<String> get() = SIGNATURES.keys

    /** Each letter's alteration in major key [key]: +1 sharp, −1 flat. Unknown keys read as C. */
    private fun altered(key: String): IntArray {
        val sig = SIGNATURES[key] ?: 0
        val altered = IntArray(7)
        if (sig > 0) SHARP_ORDER.take(sig).forEach { altered[LETTERS.indexOf(it)] = 1 }
        if (sig < 0) FLAT_ORDER.take(-sig).forEach { altered[LETTERS.indexOf(it)] = -1 }
        return altered
    }

    private fun spelling(midi: Int, key: String): Spelling {
        val sig = SIGNATURES[key] ?: 0
        val altered = altered(key)
        val pc = Math.floorMod(midi, 12)
        val inScale = (0 until 7).firstOrNull { Math.floorMod(NATURAL_PC[it] + altered[it], 12) == pc }
        val (letter, shift, accidental) = when {
            inScale != null -> Triple(inScale, altered[inScale], "")
            NATURAL_PC.contains(pc) -> Triple(NATURAL_PC.indexOf(pc), 0, "=")
            sig < 0 -> Triple(NATURAL_PC.indexOf(pc + 1), -1, "_")
            else -> Triple(NATURAL_PC.indexOf(pc - 1), 1, "^")
        }
        return Spelling(letter, shift, accidental, Math.floorDiv(midi - shift, 12) - 5)
    }

    /** MIDI [midi] as an ABC note in major key [key], on its own: scale notes plain, others with an accidental. */
    fun spell(midi: Int, key: String): String = spelling(midi, key).text

    /**
     * One bar's notes ([rest] for null). A note whose letter already carried an
     * accidental earlier in the bar gets an explicit one too, so `=c c` (C
     * natural then C sharp in D) is written `=c ^c`.
     */
    fun spellBar(midis: List<Int?>, key: String, rest: String = "z"): List<String> {
        val marked = mutableSetOf<Int>()
        return midis.map { midi ->
            if (midi == null) return@map rest
            val s = spelling(midi, key)
            when {
                s.accidental.isNotEmpty() -> { marked += s.letter; s.text }
                s.letter in marked -> s.copy(accidental = ACCIDENTAL.getValue(s.shift)).text
                else -> s.text
            }
        }
    }

    /**
     * Reads one bar of notes and `z` rests (null) in major key [key], with the
     * bar's accidentals carried forward; null if a token is anything else.
     */
    fun parseBar(tokens: List<String>, key: String): List<Int?>? {
        val keyAltered = altered(key)
        val barAltered = mutableMapOf<Pair<Int, Int>, Int>()
        return tokens.map { token ->
            if (token == "z") return@map null
            val match = NOTE.find(token) ?: return null
            val (accidental, name, marks) = match.destructured
            val letter = LETTERS.indexOf(name[0].uppercaseChar())
            val octave = if (name[0].isUpperCase()) {
                if ('\'' in marks) return null
                -marks.length
            } else {
                if (',' in marks) return null
                1 + marks.length
            }
            val shift = when (accidental) {
                "^" -> 1
                "_" -> -1
                "=" -> 0
                else -> barAltered[letter to octave] ?: keyAltered[letter]
            }
            if (accidental.isNotEmpty()) barAltered[letter to octave] = shift
            (octave + 5) * 12 + NATURAL_PC[letter] + shift
        }
    }

    /**
     * [midi]'s name in major key [key] in convention [names] (AUTO reads as
     * letters): "F♯", "B♭" or "Fa♯", "Si♭".
     */
    fun name(midi: Int, key: String, names: NoteNames): String {
        val s = spelling(midi, key)
        val letter = LETTERS[s.letter]
        val base = if (names == NoteNames.SOLFEGE) SOLFEGE.getValue(letter) else letter.toString()
        return base + when (s.shift) { 1 -> "♯"; -1 -> "♭"; else -> "" }
    }

    private val ACCIDENTAL = mapOf(1 to "^", -1 to "_", 0 to "=")
}

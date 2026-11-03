package io.github.conorryan.whistletabber.music

/**
 * Single ABC notes in a key (any tonic and mode, as `K:` writes it: `D`,
 * `Em`, `Ador`): MIDI pitch to ABC ([spell], [spellBar]) and back
 * ([parseBar]). Only plain notes are handled: an optional `^`, `_` or `=`, a
 * letter, and `,` or `'` octave marks; no lengths.
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

    /** Major keys by name, as `K:` values. */
    val KEYS: Set<String> get() = SIGNATURES.keys

    /** Each mode's signature in fifths from its tonic's major key. */
    private val MODE_FIFTHS = mapOf("maj" to 0, "lyd" to 1, "mix" to -1, "dor" to -2, "min" to -3, "phr" to -4, "loc" to -5)

    /**
     * The signature of [key], a `K:` value: sharps (positive) or flats
     * (negative). Null for a key these functions can't read, such as one
     * with explicit accidentals (`K:D ^g`) or `K:none`.
     */
    fun signature(key: String): Int? {
        SIGNATURES[key]?.let { return it }
        if (Regex("""(^|\s)[_^=][A-Ga-g]""").containsMatchIn(key)) return null
        val (tonic, mode) = AbcBook.parseKey(key) ?: return null
        val fifths = SHARP_ORDER.indexOf(tonic[0]) - 1 + when (tonic.getOrNull(1)) {
            '#' -> 7
            'b' -> -7
            else -> 0
        } + MODE_FIFTHS.getValue(mode)
        return fifths.takeIf { it in -7..7 }
    }

    /** The tonic of [key] as a pitch class (0 = C), or null. */
    fun tonicClass(key: String): Int? {
        val (tonic, _) = AbcBook.parseKey(key) ?: return null
        val shift = when (tonic.getOrNull(1)) { '#' -> 1; 'b' -> -1; else -> 0 }
        return Math.floorMod(NATURAL_PC[LETTERS.indexOf(tonic[0])] + shift, 12)
    }

    /**
     * [key] moved by [steps] semitones, its mode and anything after the tonic
     * kept: `Ador` up 5 is `Ddor`. The new tonic is spelled with the fewest
     * accidentals in its signature. Null if [key] can't be read.
     */
    fun transposeKey(key: String, steps: Int): String? {
        val trimmed = key.trim()
        val pc = tonicClass(trimmed) ?: return null
        val tonicLength = if (trimmed.getOrNull(1) == '#' || trimmed.getOrNull(1) == 'b') 2 else 1
        val rest = trimmed.substring(tonicLength)
        val target = Math.floorMod(pc + steps, 12)
        return LETTERS.flatMap { l -> listOf("", "#", "b").map { l + it } }
            .filter { t -> Math.floorMod(NATURAL_PC[LETTERS.indexOf(t[0])] + when (t.getOrNull(1)) { '#' -> 1; 'b' -> -1; else -> 0 }, 12) == target }
            .mapNotNull { t -> signature(t + rest)?.let { sig -> t + rest to sig } }
            .minByOrNull { (_, sig) -> Math.abs(sig) * 2 + if (sig < 0) 1 else 0 }
            ?.first
    }

    /** Each letter's alteration in [key]: +1 sharp, −1 flat. Unknown keys read as C. */
    internal fun altered(key: String): IntArray {
        val sig = signature(key) ?: 0
        val altered = IntArray(7)
        if (sig > 0) SHARP_ORDER.take(sig).forEach { altered[LETTERS.indexOf(it)] = 1 }
        if (sig < 0) FLAT_ORDER.take(-sig).forEach { altered[LETTERS.indexOf(it)] = -1 }
        return altered
    }

    private fun spelling(midi: Int, key: String): Spelling {
        val sig = signature(key) ?: 0
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

    /** MIDI [midi] as an ABC note in [key], on its own: scale notes plain, others with an accidental. */
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
     * Reads one bar of notes and `z` rests (null) in [key], with the
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
     * [midi]'s name in [key] in convention [names] (AUTO reads as
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

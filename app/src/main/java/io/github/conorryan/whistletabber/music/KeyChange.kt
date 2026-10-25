package io.github.conorryan.whistletabber.music

/**
 * Choosing a key to rewrite a tune into (the rewriting itself is abcjs's
 * strTranspose, through the page). Pure, so the UI's choices are testable.
 */
object KeyChange {

    /** Tonics offered as targets, spelled as abcjs writes them after strTranspose. */
    val TONICS = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private val SEMITONES = mapOf(
        "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3, "E" to 4, "Fb" to 4,
        "E#" to 5, "F" to 5, "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8, "A" to 9,
        "A#" to 10, "Bb" to 10, "B" to 11, "Cb" to 11, "B#" to 0,
    )

    /**
     * Semitones from the tonic of [fromKey] (a `K:` value) to [toTonic], the
     * shorter way round: −6..+5, so a tune stays near a whistle's range.
     * Null when either key can't be read.
     */
    fun steps(fromKey: String?, toTonic: String): Int? {
        val from = AbcBook.parseKey(fromKey ?: return null)?.first?.let(SEMITONES::get) ?: return null
        val to = SEMITONES[toTonic] ?: return null
        return Math.floorMod(to - from + 6, 12) - 6
    }

    /** "Am", "G", "Ddor": [tonic] with the mode of [fromKey], as shown in titles and pickers. */
    fun keyName(fromKey: String?, tonic: String): String {
        val mode = fromKey?.let(AbcBook::parseKey)?.second ?: "maj"
        return tonic + when (mode) {
            "maj" -> ""
            "min" -> "m"
            else -> mode
        }
    }

    /** The title for a copy rewritten into [keyName]: "Banish Misfortune (G)". */
    fun copyTitle(title: String, keyName: String): String = "$title ($keyName)"
}

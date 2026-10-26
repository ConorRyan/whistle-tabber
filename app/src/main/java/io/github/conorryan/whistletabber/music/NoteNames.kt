package io.github.conorryan.whistletabber.music

/**
 * How note names are shown: letters (C D E) or fixed-do solfège (Do Ré Mi).
 * A setting of its own, independent of the app's language.
 */
enum class NoteNames {
    LETTERS,
    SOLFEGE,
    ;

    companion object {
        val DEFAULT = LETTERS
    }
}

private val SOLFEGE = mapOf(
    'C' to "Do", 'D' to "Ré", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si",
)

/** The whistle's key in [names]: "E♭" or "Mi♭". Accidentals are kept as written. */
fun WhistleKey.noteName(names: NoteNames): String = when (names) {
    NoteNames.LETTERS -> letter
    NoteNames.SOLFEGE -> SOLFEGE.getValue(letter.first()) + letter.drop(1)
}

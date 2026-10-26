package io.github.conorryan.whistletabber.music

/**
 * How whistles are named. Each is a whole convention, the same in every app
 * language: letters ("High G", "Eb", "Low D") or fixed-do solfège
 * ("Sol aigu", "Mi♭", "Ré grave").
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

/** The whistle's key alone: "Eb" or "Mi♭". */
fun WhistleKey.noteName(names: NoteNames): String = when (names) {
    NoteNames.LETTERS -> letter
    NoteNames.SOLFEGE -> SOLFEGE.getValue(letter.first()) + letter.drop(1).replace("b", "♭").replace("#", "♯")
}

/** The whistle's full name: "Low D" or "Ré grave". */
fun WhistleKey.label(names: NoteNames): String {
    val name = noteName(names)
    return when (names) {
        NoteNames.LETTERS -> when (register) {
            WhistleKey.Register.HIGH -> "High $name"
            WhistleKey.Register.NORMAL -> name
            WhistleKey.Register.LOW -> "Low $name"
        }
        NoteNames.SOLFEGE -> when (register) {
            WhistleKey.Register.HIGH -> "$name aigu"
            WhistleKey.Register.NORMAL -> name
            WhistleKey.Register.LOW -> "$name grave"
        }
    }
}

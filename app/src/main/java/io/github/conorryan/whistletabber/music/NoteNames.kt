package io.github.conorryan.whistletabber.music

/**
 * How whistles are named. LETTERS and SOLFEGE are each a whole convention,
 * the same in every app language: "High G", "Eb", "Low D" or "Sol aigu",
 * "Mi♭", "Ré grave". AUTO picks one by the app's language; the UI resolves it
 * ([resolve]) before naming anything.
 */
enum class NoteNames {
    AUTO,
    LETTERS,
    SOLFEGE,
    ;

    /** AUTO becomes SOLFEGE where the app's language names notes in solfège, else LETTERS. */
    fun resolve(languageUsesSolfege: Boolean): NoteNames = when (this) {
        AUTO -> if (languageUsesSolfege) SOLFEGE else LETTERS
        else -> this
    }

    companion object {
        val DEFAULT = AUTO
    }
}

private val SOLFEGE = mapOf(
    'C' to "Do", 'D' to "Ré", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si",
)

/** The whistle's key alone: "Eb" or "Mi♭". An unresolved AUTO reads as letters. */
fun WhistleKey.noteName(names: NoteNames): String = when (names) {
    NoteNames.AUTO, NoteNames.LETTERS -> letter
    NoteNames.SOLFEGE -> SOLFEGE.getValue(letter.first()) + letter.drop(1).replace("b", "♭").replace("#", "♯")
}

/** The whistle's full name: "Low D" or "Ré grave". */
fun WhistleKey.label(names: NoteNames): String {
    val name = noteName(names)
    return when (names) {
        NoteNames.AUTO, NoteNames.LETTERS -> when (register) {
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

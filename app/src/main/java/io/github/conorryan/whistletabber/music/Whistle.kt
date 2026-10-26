package io.github.conorryan.whistletabber.music

/**
 * Whistles by key. Whistle music is written an octave below where a soprano
 * whistle sounds: the D whistle's bottom note is written D (MIDI 62) and
 * sounds D5 (74). Low whistles read the same notation and sound as written.
 *
 * "A" is the common A whistle (bottom note A4, 440 Hz).
 *
 * @property letter the whistle's key as a letter name ("G", "Eb"); see [label]
 * @property register high, normal or low: "High G", "Low D"
 * @property writtenTonic MIDI pitch of the bottom note (all holes closed) as written
 * @property soundingShift semitones from written to sounding pitch
 */
enum class WhistleKey(val letter: String, val register: Register, val writtenTonic: Int, val soundingShift: Int) {
    HIGH_G("G", Register.HIGH, 67, 12),
    F("F", Register.NORMAL, 65, 12),
    E_FLAT("Eb", Register.NORMAL, 63, 12),
    D("D", Register.NORMAL, 62, 12),
    C("C", Register.NORMAL, 60, 12),
    B_FLAT("Bb", Register.NORMAL, 58, 12),
    A("A", Register.NORMAL, 57, 12),
    LOW_G("G", Register.LOW, 67, 0),
    LOW_F("F", Register.LOW, 65, 0),
    LOW_D("D", Register.LOW, 62, 0),
    LOW_C("C", Register.LOW, 60, 0),
    ;

    enum class Register { HIGH, NORMAL, LOW }

    companion object {
        val DEFAULT = D
    }
}

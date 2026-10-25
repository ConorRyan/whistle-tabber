package io.github.conorryan.whistletabber.music

/**
 * Whistles by key. Whistle music is written an octave below where a soprano
 * whistle sounds: the D whistle's bottom note is written D (MIDI 62) and
 * sounds D5 (74). Low whistles read the same notation and sound as written.
 *
 * "A" is the common A whistle (bottom note A4, 440 Hz).
 *
 * @property writtenTonic MIDI pitch of the bottom note (all holes closed) as written
 * @property soundingShift semitones from written to sounding pitch
 */
enum class WhistleKey(val label: String, val writtenTonic: Int, val soundingShift: Int) {
    HIGH_G("High G", 67, 12),
    F("F", 65, 12),
    E_FLAT("E♭", 63, 12),
    D("D", 62, 12),
    C("C", 60, 12),
    B_FLAT("B♭", 58, 12),
    A("A", 57, 12),
    LOW_G("Low G", 67, 0),
    LOW_F("Low F", 65, 0),
    LOW_D("Low D", 62, 0),
    LOW_C("Low C", 60, 0),
    ;

    companion object {
        val DEFAULT = D
    }
}

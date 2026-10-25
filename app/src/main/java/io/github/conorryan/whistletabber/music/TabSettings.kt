package io.github.conorryan.whistletabber.music

/**
 * What "change whistle" means:
 * - [SAME_PITCHES]: keep the tune's notes; recompute fingerings for the chosen
 *   whistle (may bring half-holes or out-of-range notes).
 * - [SAME_FINGERINGS]: keep the fingerings written for the tune's whistle and
 *   play them on the chosen one; the music sounds in the chosen whistle's key.
 */
enum class TransposeMode { SAME_PITCHES, SAME_FINGERINGS }

data class TabSettings(
    val whistle: WhistleKey = WhistleKey.DEFAULT,
    val cNatural: CNatural = CNatural.DEFAULT,
    val foldOutOfRange: Boolean = true,
    val mode: TransposeMode = TransposeMode.SAME_PITCHES,
) {
    /** The whistle whose fingerings are shown for a tune written for [tuneWhistle]. */
    fun fingeringWhistle(tuneWhistle: WhistleKey): WhistleKey = when (mode) {
        TransposeMode.SAME_PITCHES -> whistle
        TransposeMode.SAME_FINGERINGS -> tuneWhistle
    }

    /** Semitones from written to played pitch for a tune written for [tuneWhistle]. */
    fun midiTranspose(tuneWhistle: WhistleKey): Int = when (mode) {
        TransposeMode.SAME_PITCHES -> whistle.soundingShift
        TransposeMode.SAME_FINGERINGS ->
            whistle.soundingShift + whistle.writtenTonic - tuneWhistle.writtenTonic
    }
}

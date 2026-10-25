package io.github.conorryan.whistletabber.music

import io.github.conorryan.whistletabber.web.NoteEvent

sealed interface NoteFingering {
    /** @property folded octaves the note was moved to fit the range (−1, 0, +1, …) */
    data class Playable(val fingering: Fingering, val folded: Int = 0) : NoteFingering

    data class OutOfRange(val tooHigh: Boolean) : NoteFingering
}

/**
 * Maps written pitches to fingerings. Every whistle key uses the same shapes,
 * so the table is indexed by semitones above the whistle's bottom note.
 */
object FingeringEngine {

    private val FIRST_OCTAVE: List<Fingering?> = listOf(
        "XXX XXX", // 0  D
        "XXX XX½", // 1  D♯/E♭
        "XXX XXO", // 2  E
        "XXX X½O", // 3  F
        "XXX XOO", // 4  F♯
        "XXX OOO", // 5  G
        "XX½ OOO", // 6  G♯/A♭
        "XXO OOO", // 7  A
        "X½O OOO", // 8  A♯/B♭
        "XOO OOO", // 9  B
        null, //      10 C, from the user's CNatural preference
        "OOO OOO", // 11 C♯
    ).map { it?.let(Fingering::parse) }

    /**
     * Third octave, from the Woodwind Fingering Guide's tin whistle chart
     * (https://www.wfg.woodwind.org/tinwhistle/tw_bas_3.html). Fife-only
     * entries are left out, so D♯ (offset 25) is out of range. All are hard.
     */
    private val THIRD_OCTAVE: Map<Int, Fingering> = mapOf(
        24 to "OXX XXX", // D
        26 to "XXX XXO", // E   "Difficult. Do not overblow."
        27 to "XXX X½X", // F   "Tricky."
        28 to "XXX XOX", // F♯
        29 to "XXX OOX", // G   "Good on most whistles."
        30 to "XX½ OOX", // G♯  "Occasionally possible in passing."
        31 to "XXO OOX", // A
        32 to "OXO XOO", // A♯  "Typically flat."
        33 to "XOX OXO", // B   "Typically sharp."
        34 to "XOX OXX", // C   "Requires a lot of air."
    ).mapValues { (_, n) -> Fingering.parse(n, hard = true).inOctave(2) }

    const val HIGHEST_OFFSET = 34

    /** Fingering for [offset] semitones above the bottom note, or null if not playable. */
    fun fingeringAt(offset: Int, cNatural: CNatural): Fingering? = when (offset) {
        in 0..11 -> if (offset == 10) cNatural.fingering else FIRST_OCTAVE[offset]
        in 12..23 -> fingeringAt(offset - 12, cNatural)?.inOctave(1)
        else -> THIRD_OCTAVE[offset]
    }

    fun fingeringFor(midi: Int, whistle: WhistleKey, cNatural: CNatural, fold: Boolean): NoteFingering {
        val offset = midi - whistle.writtenTonic
        fingeringAt(offset, cNatural)?.let { return NoteFingering.Playable(it) }
        val tooHigh = offset > 0
        if (!fold) return NoteFingering.OutOfRange(tooHigh)
        // Fold by whole octaves toward the playable range; offset 25 (no
        // whistle D♯ in the third octave) folds down one octave too.
        var folded = 0
        var o = offset
        while (fingeringAt(o, cNatural) == null) {
            if (tooHigh) { o -= 12; folded-- } else { o += 12; folded++ }
        }
        return NoteFingering.Playable(fingeringAt(o, cNatural)!!, folded)
    }

    /** One entry per note; null where no diagram belongs (tie continuations, unknown pitch). */
    fun forNotes(notes: List<NoteEvent>, settings: TabSettings, tuneWhistle: WhistleKey): List<NoteFingering?> {
        val whistle = settings.fingeringWhistle(tuneWhistle)
        return notes.map { note ->
            val midi = note.midi
            if (note.tieContinuation || midi == null) null
            else fingeringFor(midi, whistle, settings.cNatural, settings.foldOutOfRange)
        }
    }
}

package io.github.conorryan.whistletabber.music

import io.github.conorryan.whistletabber.web.NoteEvent

sealed interface NoteFingering {
    /** @property folded octaves the note was moved to fit the range (−1, 0, +1, …) */
    data class Playable(val fingering: Fingering, val folded: Int = 0) : NoteFingering

    data class OutOfRange(val tooHigh: Boolean) : NoteFingering
}

/**
 * Maps written pitches to fingerings. Every whistle key uses the same shapes,
 * so fingerings are indexed by semitones above the whistle's bottom note; the
 * shapes and the user's choice among them come from [FingeringOptions] and
 * [FingeringChoices].
 */
object FingeringEngine {

    const val HIGHEST_OFFSET = FingeringOptions.HIGHEST_OFFSET

    /** Fingering for [offset] semitones above the bottom note, or null if not playable. */
    fun fingeringAt(offset: Int, choices: FingeringChoices = FingeringChoices.DEFAULT): Fingering? =
        choices.fingeringAt(offset)

    fun fingeringFor(midi: Int, whistle: WhistleKey, choices: FingeringChoices, fold: Boolean): NoteFingering {
        val offset = midi - whistle.writtenTonic
        fingeringAt(offset, choices)?.let { return NoteFingering.Playable(it) }
        val tooHigh = offset > 0
        if (!fold) return NoteFingering.OutOfRange(tooHigh)
        // Fold by whole octaves toward the playable range.
        var folded = 0
        var o = offset
        while (fingeringAt(o, choices) == null) {
            if (tooHigh) { o -= 12; folded-- } else { o += 12; folded++ }
        }
        return NoteFingering.Playable(fingeringAt(o, choices)!!, folded)
    }

    /** One entry per note; null where no diagram belongs (tie continuations, unknown pitch). */
    fun forNotes(notes: List<NoteEvent>, settings: TabSettings, tuneWhistle: WhistleKey): List<NoteFingering?> {
        val whistle = settings.fingeringWhistle(tuneWhistle)
        return notes.map { note ->
            val midi = note.midi
            if (note.tieContinuation || midi == null) null
            else fingeringFor(midi, whistle, settings.fingerings, settings.foldOutOfRange)
        }
    }
}

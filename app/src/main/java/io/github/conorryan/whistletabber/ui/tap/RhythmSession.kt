package io.github.conorryan.whistletabber.ui.tap

import io.github.conorryan.whistletabber.music.Press
import io.github.conorryan.whistletabber.music.Rhythm
import io.github.conorryan.whistletabber.music.RhythmFit
import io.github.conorryan.whistletabber.music.RhythmSetup

/**
 * One pass of tapping the rhythm over [total] sounding notes at [setup]:
 * presses and releases in ms from the first downbeat after the count-in.
 * One press is held at a time; a press while another is held (the next
 * finger landing first) ends that one there. Pure.
 */
class RhythmSession(val setup: RhythmSetup, val total: Int) {

    private val presses = mutableListOf<Press>()
    private var heldSince: Long? = null

    /** The index of the next sounding note to tap. */
    val next: Int get() = presses.size

    val finished: Boolean get() = presses.size >= total

    /** Nothing has been tapped yet. */
    val empty: Boolean get() = presses.isEmpty()

    /** Presses at [ms]: the index of the sounding note it plays, or null when every note is tapped. */
    fun press(ms: Long): Int? {
        if (heldSince != null) release(ms)
        if (finished) return null
        heldSince = ms
        return presses.size
    }

    /** Ends the held press at [ms], if there is one. */
    fun release(ms: Long) {
        val down = heldSince ?: return
        heldSince = null
        presses += Press(down, maxOf(ms, down))
    }

    /** Drops a press still held, as when tapping stops. */
    fun cancelHeld() {
        heldSince = null
    }

    /** The best-guess rhythm of everything tapped; notes not reached get a beat each. */
    fun rhythm(): Rhythm = RhythmFit.fit(presses.toList(), total, setup)
}

package io.github.conorryan.whistletabber.music

import io.github.conorryan.whistletabber.music.NoteFingering.OutOfRange
import io.github.conorryan.whistletabber.music.NoteFingering.Playable
import io.github.conorryan.whistletabber.web.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingeringEngineTest {

    /** Chromatic chart for a D whistle, written D (62) upwards, default fingerings. */
    private val dWhistleChart = listOf(
        "XXX XXX", "XXX XX½", "XXX XXO", "XXX X½O", "XXX XOO", "XXX OOO",
        "XX½ OOO", "XXO OOO", "X½O OOO", "XOO OOO", "OXX OOO", "OOO OOO",
        "XXX XXX+", "XXX XX½+", "XXX XXO+", "XXX X½O+", "XXX XOO+", "XXX OOO+",
        "XX½ OOO+", "XXO OOO+", "X½O OOO+", "XOO OOO+", "OXX OOO+", "OOO OOO+",
        "OXX XXX++", "XXO XOX++", "XXX XXO++", "XXX X½X++", "XXX XOX++", "XXX OOX++",
        "XX½ OOX++", "XXO OOX++", "OXO XOO++", "XOX OXO++", "XOX OXX++",
    )

    private fun noteFor(midi: Int, whistle: WhistleKey = WhistleKey.D, c: FingeringChoices = FingeringChoices.DEFAULT, fold: Boolean = false) =
        FingeringEngine.fingeringFor(midi, whistle, c, fold)

    @Test
    fun `D whistle chromatic chart, bottom D to third-octave C`() {
        dWhistleChart.forEachIndexed { offset, expected ->
            val result = noteFor(62 + offset)
            if (expected == null) {
                assertEquals("offset $offset", OutOfRange(tooHigh = true), result)
            } else {
                assertEquals("offset $offset", expected, (result as Playable).fingering.notation())
                assertEquals("offset $offset folded", 0, result.folded)
            }
        }
    }

    @Test
    fun `only the third octave is hard`() {
        dWhistleChart.indices.forEach { offset ->
            val f = (noteFor(62 + offset) as? Playable)?.fingering ?: return@forEach
            assertEquals("offset $offset", offset >= 24, f.hard)
        }
    }

    @Test
    fun `every whistle uses the same shapes relative to its bottom note`() {
        for (whistle in WhistleKey.entries) {
            dWhistleChart.forEachIndexed { offset, expected ->
                val result = noteFor(whistle.writtenTonic + offset, whistle)
                assertEquals("$whistle +$offset", expected, (result as? Playable)?.fingering?.notation())
            }
        }
    }

    @Test
    fun `C whistle reading a G major tune needs a half-hole for F sharp`() {
        // Written f# (78) is 18 semitones above the C whistle's written C (60).
        assertEquals("XX½ OOO+", (noteFor(78, WhistleKey.C) as Playable).fingering.notation())
        // ...and on a D whistle it is a plain second-octave F#.
        assertEquals("XXX XOO+", (noteFor(78, WhistleKey.D) as Playable).fingering.notation())
    }

    @Test
    fun `B flat whistle maps written B flat to all holes closed`() {
        assertEquals("XXX XXX", (noteFor(58, WhistleKey.B_FLAT) as Playable).fingering.notation())
    }

    @Test
    fun `each C natural choice is its octave's own`() {
        FingeringOptions.at(10).forEachIndexed { i, option ->
            val low = (noteFor(72, c = FingeringChoices.DEFAULT.with(10, i)) as Playable).fingering
            assertEquals(option.fingering, low)
            assertEquals("OXX OOO+", (noteFor(84, c = FingeringChoices.DEFAULT.with(10, i)) as Playable).fingering.notation())
        }
        FingeringOptions.at(22).forEachIndexed { i, option ->
            assertEquals(option.fingering, (noteFor(84, c = FingeringChoices.DEFAULT.with(22, i)) as Playable).fingering)
            assertEquals(1, option.fingering.octave)
        }
    }

    @Test
    fun `second-octave D can be vented`() {
        val vented = FingeringChoices.DEFAULT.with(12, 1)
        assertEquals("OXX XXX+", (noteFor(74, c = vented) as Playable).fingering.notation())
        assertEquals("XXX XXX", (noteFor(62, c = vented) as Playable).fingering.notation())
    }

    @Test
    fun `a choice does not touch other notes`() {
        val withDefault = (62..96).map { noteFor(it) }
        val withHalf = (62..96).map { noteFor(it, c = FingeringChoices.DEFAULT.with(10, 1)) }
        withDefault.zip(withHalf).forEachIndexed { offset, (a, b) ->
            if (offset != 10) assertEquals("offset $offset", a, b)
        }
    }

    @Test
    fun `without folding, notes outside the range report which side`() {
        assertEquals(OutOfRange(tooHigh = false), noteFor(61))
        assertEquals(OutOfRange(tooHigh = false), noteFor(50))
        assertEquals(OutOfRange(tooHigh = true), noteFor(62 + 35))
    }

    @Test
    fun `folding moves low notes up by octaves`() {
        // Written C#4 (61) on a D whistle: fold up to C#5, all holes open.
        assertEquals(Playable(Fingering.parse("OOO OOO"), folded = 1), noteFor(61, fold = true))
        // B2 (47) is two octaves below written B4 (71).
        assertEquals(Playable(Fingering.parse("XOO OOO"), folded = 2), noteFor(47, fold = true))
    }

    @Test
    fun `folding moves high notes down by octaves`() {
        // Two octaves above the third octave's E: down two.
        assertEquals(Playable(Fingering.parse("XXX XXO++", hard = true), folded = -2), noteFor(62 + 50, fold = true))
        // Offset 35 (C# above the top C) folds to the second octave's C#.
        assertEquals(Playable(Fingering.parse("OOO OOO+"), folded = -1), noteFor(62 + 35, fold = true))
    }

    @Test
    fun `playable notes are never folded`() {
        dWhistleChart.indices.filter { dWhistleChart[it] != null }.forEach { offset ->
            assertEquals(0, (noteFor(62 + offset, fold = true) as Playable).folded)
        }
    }

    @Test
    fun `forNotes skips tie continuations and unknown pitches`() {
        val notes = listOf(
            note(0, 67),
            note(1, 67, tie = true),
            note(2, null),
            note(3, 74),
        )
        val result = FingeringEngine.forNotes(notes, TabSettings(), WhistleKey.D)
        assertEquals(4, result.size)
        assertEquals("XXX OOO", (result[0] as Playable).fingering.notation())
        assertNull(result[1])
        assertNull(result[2])
        assertEquals("XXX XXX+", (result[3] as Playable).fingering.notation())
    }

    @Test
    fun `forNotes in same-fingerings mode ignores the chosen whistle`() {
        val notes = listOf(note(0, 66)) // written F#
        val samePitches = TabSettings(whistle = WhistleKey.C, mode = TransposeMode.SAME_PITCHES)
        val sameFingerings = samePitches.copy(mode = TransposeMode.SAME_FINGERINGS)
        assertEquals("XX½ OOO", (FingeringEngine.forNotes(notes, samePitches, WhistleKey.D)[0] as Playable).fingering.notation())
        assertEquals("XXX XOO", (FingeringEngine.forNotes(notes, sameFingerings, WhistleKey.D)[0] as Playable).fingering.notation())
    }

    @Test
    fun `forNotes honours the fold setting`() {
        val notes = listOf(note(0, 60))
        assertTrue(FingeringEngine.forNotes(notes, TabSettings(foldOutOfRange = false), WhistleKey.D)[0] is OutOfRange)
        val folded = FingeringEngine.forNotes(notes, TabSettings(foldOutOfRange = true), WhistleKey.D)[0] as Playable
        assertEquals(1, folded.folded)
        assertFalse(folded.fingering.hard)
    }

    private fun note(index: Int, midi: Int?, tie: Boolean = false) =
        NoteEvent(index, index * 2, index * 2 + 1, midi, 0, if (tie) null else 0.0, if (tie) null else 0.125, tie)
}

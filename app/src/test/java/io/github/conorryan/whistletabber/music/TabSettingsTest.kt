package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSettingsTest {

    @Test
    fun `defaults are a D whistle, OXX OOO, folding on, same pitches`() {
        val s = TabSettings()
        assertEquals(WhistleKey.D, s.whistle)
        assertEquals(CNatural.OXX_OOO, s.cNatural)
        assertEquals(true, s.foldOutOfRange)
        assertEquals(TransposeMode.SAME_PITCHES, s.mode)
    }

    @Test
    fun `same pitches plays at the chosen whistle's octave`() {
        val tune = WhistleKey.D
        assertEquals(12, TabSettings(whistle = WhistleKey.D).midiTranspose(tune))
        assertEquals(12, TabSettings(whistle = WhistleKey.C).midiTranspose(tune))
        assertEquals(0, TabSettings(whistle = WhistleKey.LOW_D).midiTranspose(tune))
        assertEquals(0, TabSettings(whistle = WhistleKey.LOW_G).midiTranspose(tune))
    }

    @Test
    fun `same fingerings sounds in the chosen whistle's key`() {
        fun t(whistle: WhistleKey, tune: WhistleKey = WhistleKey.D) =
            TabSettings(whistle = whistle, mode = TransposeMode.SAME_FINGERINGS).midiTranspose(tune)
        assertEquals(12, t(WhistleKey.D))
        assertEquals(10, t(WhistleKey.C)) // a tone lower
        assertEquals(13, t(WhistleKey.E_FLAT)) // a semitone higher
        assertEquals(17, t(WhistleKey.HIGH_G)) // a fourth higher
        assertEquals(0, t(WhistleKey.LOW_D)) // an octave lower than high D
        assertEquals(5, t(WhistleKey.LOW_G))
        assertEquals(-2, t(WhistleKey.LOW_C))
        assertEquals(14, t(WhistleKey.D, tune = WhistleKey.C)) // C-whistle fingerings on a D: a tone up
    }

    @Test
    fun `fingering whistle follows the mode`() {
        assertEquals(WhistleKey.C, TabSettings(whistle = WhistleKey.C).fingeringWhistle(WhistleKey.D))
        assertEquals(
            WhistleKey.D,
            TabSettings(whistle = WhistleKey.C, mode = TransposeMode.SAME_FINGERINGS).fingeringWhistle(WhistleKey.D),
        )
    }

    @Test
    fun `soprano whistles sound an octave above written pitch, low whistles as written`() {
        val sounding = WhistleKey.entries.associateWith { it.writtenTonic + it.soundingShift }
        assertEquals(74, sounding[WhistleKey.D]) // D5, 587 Hz
        assertEquals(72, sounding[WhistleKey.C]) // C5
        assertEquals(79, sounding[WhistleKey.HIGH_G]) // G5
        assertEquals(69, sounding[WhistleKey.A]) // A4, 440 Hz
        assertEquals(62, sounding[WhistleKey.LOW_D]) // D4
        assertEquals(67, sounding[WhistleKey.LOW_G]) // G4
        // Higher-pitched whistles always sound higher.
        val order = listOf(WhistleKey.HIGH_G, WhistleKey.F, WhistleKey.E_FLAT, WhistleKey.D, WhistleKey.C,
            WhistleKey.B_FLAT, WhistleKey.A, WhistleKey.LOW_G, WhistleKey.LOW_F, WhistleKey.LOW_D, WhistleKey.LOW_C)
        assertEquals(order.map { sounding.getValue(it) }.sortedDescending(), order.map { sounding.getValue(it) })
    }
}

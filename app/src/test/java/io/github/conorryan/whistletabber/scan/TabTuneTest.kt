package io.github.conorryan.whistletabber.scan

import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.FingeringOptions
import io.github.conorryan.whistletabber.music.WhistleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabTuneTest {

    private val box = Box(0, 0, 1, 1)

    private fun column(notation: String): TabColumn = Fingering.parse(notation).let { TabColumn(it.holes, it.octave, box) }

    @Test
    fun `every fingering the app draws reads back as its own note`() {
        for (offset in 0..FingeringEngine.HIGHEST_OFFSET) {
            FingeringOptions.at(offset).indices.forEach { i ->
                val c = FingeringChoices.DEFAULT.with(offset, i)
                val f = FingeringEngine.fingeringAt(offset, c)!!
                assertEquals("offset $offset option $i", offset, TabTune.offsetOf(TabColumn(f.holes, f.octave, box), c))
            }
        }
    }

    @Test
    fun `fingerings charts use that the app doesn't draw`() {
        assertEquals(12, TabTune.offsetOf(column("OXX XXX+"), FingeringChoices.DEFAULT)) // vented second-octave D
        assertEquals(10, TabTune.offsetOf(column("½OO OOO"), FingeringChoices.DEFAULT)) // someone else's C natural
        assertEquals(22, TabTune.offsetOf(column("OXX OXX+"), FingeringChoices.DEFAULT))
        assertEquals(31, TabTune.offsetOf(column("OXX XXO++"), FingeringChoices.DEFAULT)) // a fife's third-octave A
    }

    @Test
    fun `unknown fingerings have no note`() {
        assertNull(TabTune.offsetOf(column("OXO XOX"), FingeringChoices.DEFAULT))
        assertNull(TabTune.offsetOf(column("XOX XOX"), FingeringChoices.DEFAULT))
    }

    @Test
    fun `an accidental earlier in the bar is cancelled explicitly`() {
        val reading = TabReading(listOf(listOf(column("OXX OOO+"), column("OOO OOO+"), column("OXX OOO"), column("OOO OOO"))))
        assertEquals(
            "X:1\nT:C\nM:none\nL:1/4\n%whistle_tab_key d\nK:D\n=c' ^c' =c ^c |]\n",
            TabTune.toAbc(reading, WhistleKey.D, "C", FingeringChoices.DEFAULT),
        )
    }

    @Test
    fun `a reading becomes ABC, one line per line of tab, unknown fingerings as marked rests`() {
        val reading = TabReading(
            listOf(
                listOf(column("XXX XXX"), column("XXX XOO"), column("XXO OOO")),
                listOf(column("OXX XXX+"), column("OXO XOX"), column("XX½ OOO")),
            ),
        )
        assertEquals(
            "X:1\nT:Scanned\nM:none\nL:1/4\n%whistle_tab_key d\nK:D\nD F A |\nd \"^?\"z ^G |]\n",
            TabTune.toAbc(reading, WhistleKey.D, "Scanned", FingeringChoices.DEFAULT),
        )
    }

    @Test
    fun `bars every four notes let the page wrap long lines`() {
        val reading = TabReading(listOf(List(9) { column("XXX OOO") }, List(2) { column("XXO OOO") }))
        assertEquals(
            "X:1\nT:Long\nM:none\nL:1/4\n%whistle_tab_key d\nK:D\nG G G G | G G G G | G |\nA A |]\n",
            TabTune.toAbc(reading, WhistleKey.D, "Long", FingeringChoices.DEFAULT),
        )
    }

    @Test
    fun `combine appends the readings' lines in order`() {
        val first = TabReading(listOf(listOf(column("XXX XXX"), column("XXX OOO")), listOf(column("XXX XOO"))))
        val second = TabReading(listOf(listOf(column("OXX XXX+"))))
        assertEquals(first.lines + second.lines, TabTune.combine(listOf(first, second)).lines)
        assertEquals(
            "X:1\nT:Two\nM:none\nL:1/4\n%whistle_tab_key d\nK:D\nD G |\nF |\nd |]\n",
            TabTune.toAbc(TabTune.combine(listOf(first, second)), WhistleKey.D, "Two", FingeringChoices.DEFAULT),
        )
    }

    @Test
    fun `the whistle sets the key and the pitches`() {
        val reading = TabReading(listOf(listOf(column("XXX XXX"), column("XXX OOO"), column("XXX XXX+"))))
        assertEquals(
            "X:1\nT:Low\nM:none\nL:1/4\n%whistle_tab_key c low\nK:C\nC F c |]\n",
            TabTune.toAbc(reading, WhistleKey.LOW_C, "Low", FingeringChoices.DEFAULT),
        )
        assertEquals(
            "X:1\nT:Bb\nM:none\nL:1/4\n%whistle_tab_key bb\nK:Bb\nB, E B |]\n",
            TabTune.toAbc(reading, WhistleKey.B_FLAT, "Bb", FingeringChoices.DEFAULT),
        )
    }
}

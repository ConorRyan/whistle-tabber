package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapTuneTest {

    private val playable = (0..FingeringEngine.HIGHEST_OFFSET).filter { FingeringEngine.fingeringAt(it, CNatural.DEFAULT) != null }

    @Test
    fun `notes go four to a bar and four bars to a line`() {
        val draft = TapDraft("Scale", WhistleKey.D, listOf(0, 2, 4, 5, 7, 9, 11, 12) + List(9) { 7 } + listOf(null, 10))
        assertEquals(
            "X:1\nT:Scale\nM:4/4\nL:1/4\n%whistle_tab_key d\nK:D\n" +
                "D E F G | A B c d | A A A A | A A A A |\n" +
                "A z =c |]\n",
            TapTune.toAbc(draft),
        )
    }

    @Test
    fun `an empty tune is one rest, so the page has something to draw`() {
        assertEquals("X:1\nT:\nM:4/4\nL:1/4\n%whistle_tab_key c\nK:C\nz |]\n", TapTune.toAbc(TapDraft("", WhistleKey.C, emptyList())))
    }

    @Test
    fun `every fingering on every whistle reads back`() {
        for (whistle in WhistleKey.entries) {
            val draft = TapDraft("All", whistle, playable + listOf(null) + playable.reversed())
            assertEquals(whistle.name, draft, TapTune.parse(TapTune.toAbc(draft)))
        }
    }

    @Test
    fun `changing the whistle keeps the fingerings and changes the pitches`() {
        val draft = TapDraft("Tune", WhistleKey.D, listOf(0, 4, 7))
        val onC = TapTune.toAbc(draft.copy(whistle = WhistleKey.C))
        assertEquals("X:1\nT:Tune\nM:4/4\nL:1/4\n%whistle_tab_key c\nK:C\nC E G |]\n", onC)
        assertEquals(listOf(0, 4, 7), TapTune.parse(onC)!!.notes)
    }

    @Test
    fun `other header lines survive a round trip`() {
        val abc = "X:3\nT:Mine\nT:Also mine\nR:reel\nZ:Me\n%%MIDI program 74\nM:4/4\nL:1/4\n%whistle_tab_key d\nK:D\nD E F G | A |]\n"
        val draft = TapTune.parse(abc)!!
        assertEquals(TapDraft("Mine", WhistleKey.D, listOf(0, 2, 4, 5, 7), listOf("T:Also mine", "R:reel", "Z:Me", "%%MIDI program 74")), draft)
        assertEquals(
            "X:1\nT:Mine\nT:Also mine\nR:reel\nZ:Me\n%%MIDI program 74\nM:4/4\nL:1/4\n%whistle_tab_key d\nK:D\nD E F G | A |]\n",
            TapTune.toAbc(draft),
        )
    }

    @Test
    fun `without a directive the key suggests the whistle, and is kept`() {
        val draft = TapTune.parse("X:1\nT:G\nM:4/4\nL:1/4\nK:G\nG A |]\n")
        assertEquals(TapDraft("G", WhistleKey.D, listOf(5, 7), key = "G"), draft)
        assertTrue(TapTune.toAbc(draft!!).contains("\nK:G\n"))
    }

    @Test
    fun `tunes in other forms read too`() {
        val ok = "X:1\nT:T\nM:4/4\nL:1/4\nK:D\nD E F G |]\n"
        assertEquals(listOf(0, 2, 4, 5), TapTune.parse(ok)!!.notes)
        assertEquals(null, TapTune.parse(ok)!!.timing)
        assertEquals("none", TapTune.parse(ok.replace("M:4/4", "M:none"))!!.timing!!.freeMeter)
        assertEquals(listOf(24, 24, 24, 24), TapTune.parse(ok.replace("L:1/4", "L:1/8"))!!.timing!!.lengths)
        assertEquals("Ador", TapTune.parse(ok.replace("K:D", "K:Ador"))!!.key)
        assertEquals(listOf(96, 48, 48), TapTune.parse(ok.replace("D E F G", "D2 E F"))!!.timing!!.lengths)
        assertEquals(listOf(32, 32, 32, 48), TapTune.parse(ok.replace("D E F G", "(3DEF G"))!!.timing!!.lengths)
        assertNull(TapTune.parse(ok.replace("D E F G", "[DF] E F G")))
    }

    @Test
    fun `the starter set reads`() {
        val book = java.io.File("src/main/assets/tunes/oneills.abc").readText()
        for (tune in AbcBook.split(book)) assertNotNull(tune.lines().first(), TapTune.parse(tune))
    }
}

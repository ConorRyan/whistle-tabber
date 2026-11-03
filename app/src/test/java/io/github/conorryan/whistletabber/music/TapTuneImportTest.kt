package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Tunes from elsewhere in the tap editor: [TapTune.read] on real ABC. */
class TapTuneImportTest {

    private val starter = AbcBook.split(File("src/main/assets/tunes/oneills.abc").readText())

    private fun head(meter: String = "C|", unit: String = "1/8", key: String = "D") = "X:1\nT:T\nM:$meter\nL:$unit\nK:$key\n"

    private fun roundTrip(abc: String): TapDraft {
        val read = TapTune.read(abc)
        assertNotNull(abc, read)
        assertEquals(abc, emptySet<Loss>(), read!!.losses)
        val written = TapTune.toAbc(read.draft)
        assertEquals("$abc\n--- written ---\n$written", read.draft, TapTune.parse(written))
        return read.draft
    }

    /** The body as written back: the lines after `K:`. */
    private fun body(abc: String): String = TapTune.toAbc(TapTune.parse(abc)!!).substringAfter("\nK:").substringAfter('\n').trimEnd()

    @Test
    fun `every starter tune reads without loss and writes back the same draft`() {
        assertEquals(10, starter.size)
        for (tune in starter) roundTrip(tune)
    }

    @Test
    fun `starter tunes keep their bar lines worked out from the meter`() {
        for (tune in starter) {
            val draft = TapTune.parse(tune)!!
            assertTrue(tune.lines().first { it.startsWith("T:") }, draft.timing?.free == false)
        }
    }

    @Test
    fun `written starter tunes are saved for the node check`() {
        // web-tests/rewritten.test.js plays each pair with abcjs and compares the notes.
        val out = StringBuilder()
        for (tune in starter) out.append(TapTune.toAbc(TapTune.parse(tune)!!)).append('\n')
        val file = File("../web-tests/fixtures/starter-rewritten.abc")
        if (System.getenv("UPDATE_FIXTURES") != null) file.writeText(out.toString())
        assertEquals("run with UPDATE_FIXTURES=1 to refresh", file.readText(), out.toString())
    }

    @Test
    fun `lengths, ties, broken rhythm and triplets`() {
        assertEquals("A2 A/A/A A2 A2- | A2 z6 |]", body(head(meter = "4/4") + "A2 A/A/A A2 A2- | A2 z6 |]"))
        // A dotted pair on its own beat is written the short way.
        assertEquals("A2 A<A A<A A2 | A8 |]", body(head(meter = "4/4") + "A2 A/A3/2 A/A3/2 A2 | A8 |]"))
        assertEquals("e>A f<A (3ABA (3GFE |]", body(head(meter = "4/4") + "e>A f<A (3ABA (3GFE |]"))
        val hornpipe = roundTrip(head(meter = "4/4") + "e>A (3AAA f>A (3AAA |]")
        assertEquals(listOf(36, 12, 16, 16, 16, 36, 12, 16, 16, 16), hornpipe.timing!!.lengths)
    }

    @Test
    fun `repeats, endings and decorations stay in place`() {
        val abc = head(meter = "4/4") + "|: \"G\"~G2 {a}AB (cd) e2 :|\n|[1 f8 :|[2 g8 |]"
        val draft = roundTrip(abc)
        assertEquals(listOf("|:", ":|", "|[1", ":|[2"), draft.anchors.filter { it.visible }.map { it.text })
        assertEquals("|: \"G\"~G2 {a}AB (cd) e2 :|\n|[1 f8 :|[2 g8 |]", body(abc))
    }

    @Test
    fun `a second ending starts where the first did`() {
        // A reel with a pickup: the endings are each 3 eighths, completing the pickup's bar.
        val abc = head() + "B | A2AB AFED | gfed [1 Bcd :| [2 B2d |]"
        val draft = roundTrip(abc)
        assertEquals(false, draft.timing!!.free)
        assertEquals("B | A2AB AFED | gfed [1 Bcd :| [2 B2d |]", body(abc))
        // Tapping goes on from the first ending's last note without a rest.
        assertEquals(setOf(14), TapTune.breaks(draft))
    }

    @Test
    fun `modal keys and meters are read`() {
        val dorian = roundTrip(head(meter = "6/8", key = "Ador") + "ABc dcB | c3 G3 |]")
        assertEquals("Ador", dorian.key)
        assertEquals(WhistleKey.D, dorian.whistle)
        assertEquals(listOf(7, 9, 10, 12, 10, 9, 10, 5), dorian.notes)
        assertEquals("K:Ddor", TapTune.toAbc(dorian.withWhistle(WhistleKey.HIGH_G)).lines().first { it.startsWith("K:") })
        assertEquals(Meter.CUT, roundTrip(head() + "D2FA d2fa |]").timing!!.setup.meter)
    }

    @Test
    fun `a tempo is kept as written and sets the tapper's speed`() {
        val draft = roundTrip("X:1\nT:T\nM:C|\nL:1/8\nQ:1/2=90\nK:D\nD2FA d2fa |]")
        assertEquals("1/2=90", draft.timing!!.tempo)
        assertEquals(90, draft.timing!!.setup.bpm)
        assertTrue(TapTune.toAbc(draft).contains("\nQ:1/2=90\n"))
        // No Q: stays without one.
        assertNull(roundTrip(head() + "D2FA d2fa |]").timing!!.tempo)
    }

    @Test
    fun `body lines and comments are kept`() {
        val abc = head() + "P:A\nD2FA d2fa | % first part\nP:B\na2fd A2FA |]\nW:Words"
        val draft = roundTrip(abc)
        assertEquals(listOf("P:A", "% first part", "P:B", "W:Words"), draft.anchors.filter { it.kind == Anchor.Kind.LINE || it.kind == Anchor.Kind.TEXT }.map { it.text })
    }

    @Test
    fun `bar lines that don't follow the meter are kept as written`() {
        // A bar of 6 eighths in the middle of a C| tune: the bars are free, kept where they are.
        val abc = head() + "D2FA d2fa | D2FA d2 | d2fa D2FA |]"
        assertEquals("C|", roundTrip(abc).timing!!.freeMeter)
        assertEquals("D2FA d2fa | D2FA d2 | d2fa D2FA |]", body(abc))
        // A short first bar is a pickup, not a misplaced bar line.
        assertEquals(null, roundTrip(head() + "D2FA d2 | fa d2FA d2 |]").timing!!.freeMeter)
    }

    @Test
    fun `a scanned tab reads as a free tune of quarters`() {
        val scanned = "X:1\nT:Scan\nM:none\nL:1/4\nK:D\n%whistle_tab_key d\nD E F G | A B c d |\nB \"^?\"z A |]\n"
        val draft = roundTrip(scanned)
        assertEquals("none", draft.timing!!.freeMeter)
        assertEquals(List(11) { 48 }, draft.timing!!.lengths)
        assertEquals("\"^?\"", draft.marks[9]!!.prefix)
    }

    @Test
    fun `what can't be kept is reported, and the rest still reads`() {
        fun losses(abc: String) = TapTune.read(abc)!!.losses
        assertEquals(setOf(Loss.CHORDS), losses(head() + "[DFA]2 d2 [Ace]4 |]"))
        assertEquals(listOf<Int?>(7), TapTune.read(head() + "[DFA]8 |]")!!.draft.notes)
        assertEquals(setOf(Loss.KEY_CHANGE), losses(head() + "D2FA d2fa |\nK:G\nG2Bd g2bg |]"))
        assertEquals(setOf(Loss.METER_CHANGE), losses(head() + "D2FA d2fa |\nM:3/4\nG2Bd g2 |]"))
        assertEquals(setOf(Loss.VOICES), losses(head() + "V:1\nD2FA d2fa |]\nV:2\nA,8 |]"))
        assertEquals(setOf(Loss.FOLDED), losses(head() + "A,2FA d2fa |]"))
        // A key change keeps the sounding pitches: G then F natural in the new key's signature.
        val changed = TapTune.read(head() + "F2 |\nK:C\nF2 |]")!!.draft
        assertEquals(listOf(4, 3), changed.notes)
        // Voices: only the first is kept.
        assertEquals(listOf<Int?>(0, 4, 7), TapTune.read(head() + "V:1\nD2FA |]\nV:2\nA,8 |]")!!.draft.notes)
    }

    @Test
    fun `a tune with no usable key is not read`() {
        assertNull(TapTune.read("X:1\nT:T\nK:none\nDEF |]\n"))
        assertNull(TapTune.read("X:1\nT:T\nK:Bb\nDEF |]\n")) // no whistle for Bb
        assertNull(TapTune.read("X:1\nT:T\nM:4/4\n"))
    }
}

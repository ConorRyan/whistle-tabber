package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TapTuneTimedTest {

    private val reel = RhythmSetup(bpm = 120, meter = Meter.FOUR_FOUR)

    private fun draft(vararg notes: Int?) = TapDraft("Tune", WhistleKey.D, notes.toList())

    private fun rhythm(vararg units: Int, firstBar: Int = 8) =
        Rhythm(units.mapIndexed { i, u -> Rhythm.Event(i, u) }, firstBar)

    /** The tune's body: the lines after `K:`. */
    private fun body(abc: String) = abc.substringAfter("K:D\n").trimEnd()

    @Test
    fun `a whole tune in 4-4`() {
        assertEquals(
            "X:1\nT:Tune\nM:4/4\nL:1/8\nQ:1/4=120\n%whistle_tab_key d\nK:D\nD2 E2 F2 G2 |]\n",
            TapTune.toAbc(draft(0, 2, 4, 5).withRhythm(rhythm(2, 2, 2, 2), reel)),
        )
    }

    @Test
    fun `beams group by beat`() {
        assertEquals("DE FG |]", body(TapTune.toAbc(draft(0, 2, 4, 5).withRhythm(rhythm(1, 1, 1, 1), reel))))
        val jig = RhythmSetup(bpm = 110, meter = Meter.SIX_EIGHT)
        val abc = TapTune.toAbc(draft(0, 2, 4, 5, 7, 9).withRhythm(rhythm(1, 1, 1, 1, 1, 1, firstBar = 6), jig))
        assertEquals("DEF GAB |]", body(abc))
        assertTrue(abc, "M:6/8\nL:1/8\nQ:3/8=110\n" in abc)
    }

    @Test
    fun `a note across a bar line is tied`() {
        assertEquals("D6 E2- | E2 |]", body(TapTune.toAbc(draft(0, 2).withRhythm(rhythm(6, 4), reel))))
    }

    @Test
    fun `a length one note can't have is tied pieces`() {
        assertEquals("D4- DE3 |]", body(TapTune.toAbc(draft(0, 2).withRhythm(rhythm(5, 3), reel))))
    }

    @Test
    fun `rests, and the draft's own rests left out`() {
        val r = Rhythm(listOf(Rhythm.Event(0, 2), Rhythm.Event(null, 2), Rhythm.Event(1, 4)), 8)
        assertEquals("D2 z2 E4 |]", body(TapTune.toAbc(draft(0, null, 2).withRhythm(r, reel))))
    }

    @Test
    fun `a pickup is a short first bar`() {
        assertEquals("A2 | d8 |]", body(TapTune.toAbc(draft(7, 12).withRhythm(rhythm(2, 8, firstBar = 2), reel))))
    }

    @Test
    fun `accidentals are spelled per bar`() {
        // C natural then C sharp in one bar, C sharp alone in the next.
        assertEquals("=c2 ^c6 | c8 |]", body(TapTune.toAbc(draft(10, 11, 11).withRhythm(rhythm(2, 6, 8), reel))))
    }

    @Test
    fun `four bars to a line, sixteenths as the unit`() {
        val fine = RhythmSetup(bpm = 80, meter = Meter.TWO_FOUR, grid = RhythmGrid.SIXTEENTH)
        val abc = TapTune.toAbc(draft(0, 2, 4, 5, 7).withRhythm(rhythm(8, 8, 8, 8, 3, firstBar = 8), fine))
        assertTrue(abc, "L:1/16\n" in abc)
        assertEquals("D8 | E8 | F8 | G8 |\nA3 |]", body(abc))
    }

    @Test
    fun `the result is a storable tune the tap editor reads back`() {
        val draft = TapDraft("Tune", WhistleKey.D, listOf(0, 2), listOf("Z:Me")).withRhythm(rhythm(4, 4), reel)
        val abc = TapTune.toAbc(draft)
        assertTrue(abc, abc.startsWith("X:1\nT:Tune\nZ:Me\nM:4/4"))
        assertEquals(emptyList<AbcProblem>(), AbcBook.headers(abc).problems())
        assertEquals(WhistleKey.D, AbcBook.whistleOf(abc))
        assertEquals(draft, TapTune.parse(abc))
    }

    @Test
    fun `writes exactly the form web-tests checks against abcjs`() {
        val notes = TapDraft("Timed", WhistleKey.D, listOf(7, 0, 2, 10, 11, 12, 9))
        val events = listOf(
            Rhythm.Event(0, 2), Rhythm.Event(1, 6), Rhythm.Event(2, 4), Rhythm.Event(null, 2),
            Rhythm.Event(3, 2), Rhythm.Event(4, 2), Rhythm.Event(5, 5), Rhythm.Event(6, 3),
        )
        assertEquals(
            File("../web-tests/fixtures/timed-tap.abc").readText(),
            TapTune.toAbc(notes.withRhythm(Rhythm(events, firstBarUnits = 2), reel)),
        )
    }

    @Test
    fun `two hundred random timed tunes read back exactly`() {
        val random = Random(20260518)
        val playable = (0..FingeringEngine.HIGHEST_OFFSET).filter { FingeringEngine.fingeringAt(it, CNatural.DEFAULT) != null }
        repeat(200) { n ->
            val whistle = WhistleKey.entries.random(random)
            val count = 1 + random.nextInt(24)
            val notes = List(count) { if (random.nextInt(6) == 0) null else playable.random(random) }
            val setup = RhythmSetup(
                bpm = RhythmSetup.MIN_BPM + random.nextInt(RhythmSetup.MAX_BPM - RhythmSetup.MIN_BPM + 1),
                meter = Meter.entries.random(random),
                grid = RhythmGrid.entries.random(random),
            )
            val draft = TapDraft(
                title = "Random $n",
                whistle = whistle,
                notes = notes,
                extraHeaders = listOf("Z:Me"),
                timing = TapTiming(setup, List(count) { 1 + random.nextInt(8) }, 1 + random.nextInt(setup.unitsPerBar)),
            ).normalized()
            val abc = TapTune.toAbc(draft)
            assertEquals("case $n ($whistle, ${setup.meter.abc} ${setup.grid.unitLength}):\n$abc", draft, TapTune.parse(abc))
        }
    }

    @Test
    fun `tunes in any other timed-looking form are refused`() {
        val head = "X:1\nT:T\nM:4/4\nL:1/8\nQ:1/4=120\nK:D\n"
        fun timed(body: String, header: String = head) = header + body + "\n"
        assertNull(TapTune.parse(timed("D2 | E2 | F2 G2 A2 B2 |]"))) // a short bar in the middle
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("Q:1/4=120\n", "")))) // no tempo
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("Q:1/4=120", "Q:3/8=120")))) // wrong tempo note
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("Q:1/4=120", "Q:1/4=fast")))) // tempo not a number
        assertNull(TapTune.parse(timed("D2- z2 E2 F2 |]"))) // a rest can't be tied
        assertNull(TapTune.parse(timed("D2- E2 F2 G2 |]"))) // a tie must join one pitch
        assertNull(TapTune.parse(timed("D0 E2 F2 G2 |]"))) // no zero lengths
        assertNull(TapTune.parse(timed("[DE]2 F2 G2 A2 |]"))) // no chords
        assertNull(TapTune.parse(timed("(3DEF G A B |]"))) // no tuplets
        assertNull(TapTune.parse(timed("|: D2 E2 F2 G2 :|"))) // no repeats
        assertNull(TapTune.parse(timed("C2 D2 E2 F2 |]"))) // C below the D whistle
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("M:4/4", "M:7/8")))) // unknown meter
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("L:1/8", "L:1/12")))) // not a grid unit
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |]", head.replace("K:D", "K:Ador")))) // not a major key
        assertNull(TapTune.parse(timed("D2 E2 F2 G2 |] D2 |]"))) // no second ending
    }
}

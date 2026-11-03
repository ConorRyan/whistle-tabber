package io.github.conorryan.whistletabber.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmFitTest {

    /** 4/4 at 120: a beat is 500 ms, an eighth 250 ms. */
    private val common = RhythmSetup(bpm = 120, meter = Meter.FOUR_FOUR)

    /** Lengths in eighths (or [grid] units), as ticks. */
    private fun note(i: Int, units: Int, grid: RhythmGrid = RhythmGrid.EIGHTH) = Rhythm.Event(i, units * grid.ticks)
    private fun rest(units: Int) = Rhythm.Event(null, units * RhythmGrid.EIGHTH.ticks)
    private fun bar(eighths: Int) = eighths * TICKS_PER_EIGHTH

    /** Presses starting at [downs] ms, each held [hold] ms. */
    private fun presses(downs: List<Long>, hold: Long = 400) = downs.map { Press(it, it + hold) }

    @Test
    fun `presses on the beat are quarter notes`() {
        val r = RhythmFit.fit(presses(listOf(0, 500, 1000, 1500)), 4, common)
        assertEquals(listOf(note(0, 2), note(1, 2), note(2, 2), note(3, 2)), r.events)
        assertEquals(bar(8), r.firstBarTicks)
    }

    @Test
    fun `small timing errors snap to the grid`() {
        val jitter = listOf(35L, -40, 20, -25, 40, -10)
        val downs = jitter.mapIndexed { i, j -> i * 500L + j }
        val r = RhythmFit.fit(presses(downs), 6, common)
        assertEquals(List(6) { note(it, 2) }, r.events)
    }

    @Test
    fun `a constant delay is taken off before snapping`() {
        // 110 ms late is almost half an eighth; with ±30 ms of jitter some would round the wrong way.
        val jitter = listOf(30L, -30, 25, 30, -20, 30, -30, 30)
        val downs = jitter.mapIndexed { i, j -> i * 250L + 110 + j }
        val r = RhythmFit.fit(presses(downs, hold = 200), 8, common)
        assertEquals(List(8) { note(it, 1) }, r.events)
        assertEquals(bar(8), r.firstBarTicks)
    }

    @Test
    fun `letting go early isn't a rest`() {
        val r = RhythmFit.fit(presses(listOf(0, 500, 1000), hold = 150), 3, common)
        assertEquals(listOf(note(0, 2), note(1, 2), note(2, 1)), r.events)
    }

    @Test
    fun `a long gap is a rest`() {
        val r = RhythmFit.fit(listOf(Press(0, 400), Press(1500, 2400)), 2, common)
        assertEquals(listOf(note(0, 2), rest(4), note(1, 4)), r.events)
    }

    @Test
    fun `a tune starting off the downbeat has a pickup bar`() {
        val onBeatFour = RhythmFit.fit(presses(listOf(1500, 2000)), 2, common)
        assertEquals(bar(2), onBeatFour.firstBarTicks)
        // A pickup tapped during the count-in counts too.
        val inCountIn = RhythmFit.fit(presses(listOf(-500, 0)), 2, common)
        assertEquals(bar(2), inCountIn.firstBarTicks)
        assertEquals(listOf(note(0, 2), note(1, 2)), inCountIn.events)
    }

    @Test
    fun `two presses on the same slot keep their order`() {
        val r = RhythmFit.fit(listOf(Press(0, 40), Press(60, 400), Press(500, 900)), 3, common)
        assertEquals(listOf(note(0, 1), note(1, 1), note(2, 2)), r.events)
    }

    @Test
    fun `a jig counts dotted quarters`() {
        val jig = RhythmSetup(bpm = 120, meter = Meter.SIX_EIGHT)
        val eighth = 500.0 / 3
        val downs = (0 until 6).map { (it * eighth).toLong() }
        val r = RhythmFit.fit(presses(downs, hold = 120), 6, jig)
        assertEquals(List(6) { note(it, 1) }, r.events)
        assertEquals(bar(6), r.firstBarTicks)
    }

    @Test
    fun `the sixteenth grid catches dotted rhythms`() {
        // 4/4 at 60: a beat is 1000 ms, a sixteenth 250 ms. Dotted eighth, sixteenth, quarter.
        val slow = RhythmSetup(bpm = 60, meter = Meter.FOUR_FOUR, grid = RhythmGrid.SIXTEENTH)
        val r = RhythmFit.fit(listOf(Press(0, 700), Press(750, 950), Press(1000, 1900)), 3, slow)
        assertEquals(listOf(note(0, 3, RhythmGrid.SIXTEENTH), note(1, 1, RhythmGrid.SIXTEENTH), note(2, 4, RhythmGrid.SIXTEENTH)), r.events)
        assertEquals(bar(8), r.firstBarTicks)
    }

    @Test
    fun `notes not reached get a beat each`() {
        val r = RhythmFit.fit(presses(listOf(0, 250)), 4, common)
        assertEquals(listOf(note(0, 1), note(1, 2), note(2, 2), note(3, 2)), r.events)
        assertEquals(listOf(note(0, 2), note(1, 2)), RhythmFit.fit(emptyList(), 2, common).events)
    }

    /** A quarter, an eighth-note triplet, two quarters, at 4/4 120 (beats at 0, 500, 1000, 1500), shifted by [jitter] and [delay]. */
    private fun withTriplet(jitter: List<Long> = List(6) { 0L }, delay: Long = 0): List<Press> {
        val downs = listOf(0L, 500, 667, 833, 1000, 1500).mapIndexed { i, t -> t + jitter[i] + delay }
        return presses(downs, hold = 150)
    }

    private fun ticks(r: Rhythm) = r.events.map { it.ticks }

    @Test
    fun `three presses on thirds of a beat are a triplet`() {
        assertEquals(listOf(48, 16, 16, 16, 48, 24), ticks(RhythmFit.fit(withTriplet(), 6, common)))
        // Played a little unevenly, and late.
        val sloppy = withTriplet(jitter = listOf(20L, -25, 20, -20, 15, -10), delay = 60)
        assertEquals(listOf(48, 16, 16, 16, 48, 24), ticks(RhythmFit.fit(sloppy, 6, common)))
    }

    @Test
    fun `triplets are off when switched off and in compound meters`() {
        val straight = ticks(RhythmFit.fit(withTriplet(), 6, common.copy(triplets = false)))
        assertEquals(listOf(48, 24, 24, 24), straight.take(4))
        // In 6/8 three to a beat is the plain rhythm: eighths.
        val jig = RhythmSetup(bpm = 120, meter = Meter.SIX_EIGHT)
        val eighth = 500.0 / 3
        val r = RhythmFit.fit(presses((0 until 6).map { (it * eighth).toLong() }, hold = 120), 6, jig)
        assertEquals(List(6) { 24 }, ticks(r))
    }

    @Test
    fun `uneven eighths are not mistaken for triplets`() {
        // Swung eighths, and a dotted eighth with a sixteenth: two presses to a beat.
        val swung = presses(listOf(0L, 290, 500, 790, 1000, 1290, 1500, 1790), hold = 150)
        assertTrue(RhythmFit.fit(swung, 8, common).events.none { it.ticks % 3 != 0 })
        val slow = RhythmSetup(bpm = 60, meter = Meter.FOUR_FOUR, grid = RhythmGrid.SIXTEENTH)
        val dotted = RhythmFit.fit(listOf(Press(0, 700), Press(750, 950), Press(1000, 1900)), 3, slow)
        assertEquals(listOf(36, 12, 48), ticks(dotted))
    }

    @Test
    fun `a tapped triplet is written as one`() {
        val draft = TapDraft("T", WhistleKey.D, listOf(0, 2, 4, 5, 7, 9)).withRhythm(RhythmFit.fit(withTriplet(), 6, common), common)
        val abc = TapTune.toAbc(draft)
        // Seven eighths: shorter than a bar, so it is written as a pickup.
        assertTrue(abc, abc.contains("D2 (3EFG"))
        assertEquals(draft, TapTune.parse(abc))
    }
}

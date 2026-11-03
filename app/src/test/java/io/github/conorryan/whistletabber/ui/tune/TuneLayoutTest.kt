package io.github.conorryan.whistletabber.ui.tune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneLayoutTest {

    @Test
    fun `a phone on its side is short, upright it is not`() {
        assertTrue(TuneLayout.isShort(360))
        assertTrue(TuneLayout.isShort(411))
        assertFalse(TuneLayout.isShort(480))
        assertFalse(TuneLayout.isShort(800))
    }

    @Test
    fun `the header moves with the page until it is gone`() {
        assertEquals(0, TuneLayout.headerShift(0, 300))
        assertEquals(120, TuneLayout.headerShift(120, 300))
        assertEquals(300, TuneLayout.headerShift(300, 300))
        assertEquals(300, TuneLayout.headerShift(5000, 300))
    }

    @Test
    fun `the card starts under the header and stops at the top`() {
        assertEquals(300, TuneLayout.pageTop(0, 300))
        assertEquals(180, TuneLayout.pageTop(120, 300))
        assertEquals(0, TuneLayout.pageTop(900, 300))
    }

    @Test
    fun `overscroll and an unmeasured header change nothing`() {
        assertEquals(0, TuneLayout.headerShift(-40, 300))
        assertEquals(300, TuneLayout.pageTop(-40, 300))
        assertEquals(0, TuneLayout.headerShift(100, 0))
        assertEquals(0, TuneLayout.pageTop(100, 0))
    }
}

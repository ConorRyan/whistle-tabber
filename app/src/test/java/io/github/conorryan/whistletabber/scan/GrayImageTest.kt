package io.github.conorryan.whistletabber.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayImageTest {

    @Test
    fun `luma weighs the channels and puts transparency on white`() {
        assertEquals(0, GrayImage.lumaOf(0xFF000000.toInt()))
        assertEquals(255, GrayImage.lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(76, GrayImage.lumaOf(0xFFFF0000.toInt()))
        assertEquals(255, GrayImage.lumaOf(0x00000000)) // transparent black is paper
        assertEquals(127, GrayImage.lumaOf(0x80000000.toInt()))
    }

    @Test
    fun `enlarging keeps flat areas flat and edges in place`() {
        val half = GrayImage(4, 2, intArrayOf(0, 0, 255, 255, 0, 0, 255, 255))
        val big = half.scaled(16, 8)
        assertEquals(16, big.width)
        assertEquals(8, big.height)
        assertEquals(0, big[0, 0])
        assertEquals(255, big[15, 7])
        assertTrue(big[6, 4] < 64 && big[9, 4] > 191)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixel count must match`() {
        GrayImage(2, 2, IntArray(3))
    }
}

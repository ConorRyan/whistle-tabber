package io.github.conorryan.whistletabber.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The native AAudio stream on a real device: it opens, plays and reports its position. */
@RunWith(AndroidJUnit4::class)
class NativeTapStreamTest {

    private var output: AAudioOutput? = null

    @After
    fun tearDown() {
        output?.release()
    }

    @Test
    fun aStreamOpensPlaysAndReportsItsPosition() {
        assertTrue("libtapaudio didn't load", NativeTapStream.loaded)
        val out = AAudioOutput.open()
        assertNotNull("AAudio didn't open", out)
        out!!
        output = out
        assertTrue(out.rate in 8_000..192_000)
        assertTrue(out.burst > 0)
        assertTrue(out.bufferFrames in 1..out.capacityFrames)
        assertTrue(out.start())

        val chunk = ShortArray(out.burst)
        var stamp: FrameTime? = null
        val deadline = System.nanoTime() + 2_000_000_000L
        var written = 0L
        while (System.nanoTime() < deadline && (stamp == null || stamp.frame <= 0)) {
            assertTrue(out.write(chunk))
            written += chunk.size
            stamp = out.timestamp()
        }
        assertNotNull("no timestamp after ${written} frames", stamp)
        assertTrue(stamp!!.frame in 1..written)
        assertTrue(out.setBufferFrames(out.bufferFrames + out.burst) > 0)
    }
}

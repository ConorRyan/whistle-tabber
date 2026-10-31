package io.github.conorryan.whistletabber.audio

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TapAudioTest {

    /** An output at 1000 frames a second that records what it is sent; [glitches] writes report an underrun each. */
    private class FakeOutput(override val path: OutputPath = OutputPath.AAUDIO_EXCLUSIVE, var glitches: Int = 0) : PcmOutput {
        override val rate = 1000
        override val burst = 10
        override var bufferFrames = 20
        override val capacityFrames = 100
        override var underruns = 0
        var stamp: FrameTime? = null
        var started = false
        @Volatile var releases = 0
        val written = mutableListOf<Short>()
        val wrote = CountDownLatch(5)
        var opens = 0

        override fun start(): Boolean = true.also { started = true }
        override fun write(chunk: ShortArray): Boolean {
            synchronized(written) { written += chunk.toList() }
            if (glitches > 0) {
                glitches--
                underruns++
            }
            wrote.countDown()
            Thread.sleep(1)
            return true
        }
        override fun setBufferFrames(frames: Int): Int = frames.also { bufferFrames = it }
        override fun timestamp(): FrameTime? = stamp
        override fun release() { releases++ }

        fun peakSince(from: Int): Int = synchronized(written) { written.drop(from).maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0 }
        fun size(): Int = synchronized(written) { written.size }
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun audioWith(out: FakeOutput) = TapAudio(context) { out.opens++; out }

    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 2_000
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(2)
        assertTrue(condition())
    }

    @Test
    fun `AAudio is used when it opens, else AudioTrack, and never before Android 8_1`() {
        val aaudio = FakeOutput(OutputPath.AAUDIO_EXCLUSIVE)
        val track = FakeOutput(OutputPath.AUDIO_TRACK_FAST)
        assertSame(aaudio, openOutput(Build.VERSION_CODES.P, { aaudio }, { track }))
        assertSame(track, openOutput(Build.VERSION_CODES.P, { null }, { track }))
        assertSame(track, openOutput(Build.VERSION_CODES.P, { error("no library") }, { track }))
        assertSame(track, openOutput(Build.VERSION_CODES.O, { aaudio }, { track }))
        assertNull(openOutput(Build.VERSION_CODES.P, { null }, { null }))
    }

    @Test
    fun `the metronome plays through the output, timed by its own clock, and the output closes once`() = runBlocking {
        val out = FakeOutput()
        val audio = audioWith(out)
        audio.resume()
        audio.open()
        audio.prepare(setOf(74), PadSound.NOTE)
        assertTrue(out.started)
        assertTrue(out.wrote.await(2, TimeUnit.SECONDS))
        assertEquals(0, out.peakSince(0)) // silent until something plays

        out.stamp = FrameTime(0, 0) // so relative() is defined before the clicks start
        audio.startMetronome(bpm = 60, beatsPerBar = 4) // a beat every 1000 frames
        val from = out.size()
        waitFor { out.peakSince(from) > 10_000 } // the first count-in click

        // The downbeat is a bar after the clicks started; frame 1000 heard at 5 s puts it at 5 s + (start + 4000 - 1000) ms.
        out.stamp = FrameTime(1000, 5_000_000_000)
        val downbeat = 5_000 + audio.downbeatFrame() - 1000
        assertEquals(0L, audio.relative(downbeat))
        assertEquals(250L, audio.relative(downbeat + 250))
        assertTrue(audio.downbeatFrame() in 4_000..(4_000L + from + 2 * out.burst))
        assertEquals(OutputPath.AAUDIO_EXCLUSIVE, audio.info()!!.path)

        audio.close()
        audio.close()
        assertEquals(1, out.releases)
        assertNull(audio.info())
    }

    @Test
    fun `a palette tap sounds without the metronome`() = runBlocking {
        val out = FakeOutput()
        val audio = audioWith(out)
        audio.resume()
        audio.open()
        audio.prepareTaps(setOf(74))
        assertTrue(out.wrote.await(2, TimeUnit.SECONDS))
        val from = out.size()
        audio.tap(74)
        waitFor { out.peakSince(from) > 1_000 }
        audio.close()
    }

    @Test
    fun `an open after the screen paused does nothing, and resuming opens again`() = runBlocking {
        val out = FakeOutput()
        val audio = audioWith(out)
        audio.open() // never resumed
        audio.resume()
        audio.close() // paused before the open ran
        audio.open()
        assertEquals(0, out.opens)
        audio.tap(74) // nothing to play into, no crash
        audio.resume()
        audio.open()
        audio.open()
        assertEquals(1, out.opens)
        audio.close()
        waitFor { out.releases == 1 }
    }

    @Test
    fun `a glitch reported over several writes grows the buffer once`() = runBlocking {
        val out = FakeOutput(glitches = 3)
        val audio = audioWith(out)
        audio.resume()
        audio.open()
        assertTrue(out.wrote.await(2, TimeUnit.SECONDS))
        waitFor { out.size() > 200 }
        assertEquals(30, out.bufferFrames) // 20 plus one burst, not three
        assertFalse(out.bufferFrames > out.capacityFrames)
        audio.close()
    }
}

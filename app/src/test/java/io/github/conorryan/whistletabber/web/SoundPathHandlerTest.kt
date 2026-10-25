package io.github.conorryan.whistletabber.web

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.music.WhistleSynth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundPathHandlerTest {

    @Test
    fun `note name is read from abcjs soundfont paths`() {
        assertEquals("D5", SoundPathHandler.noteNameIn("whistle-mp3/D5.mp3"))
        assertEquals("Bb4", SoundPathHandler.noteNameIn("whistle-mp3/Bb4.mp3"))
        assertEquals("C8", SoundPathHandler.noteNameIn("acoustic_grand_piano-mp3/C8.mp3"))
    }

    @Test
    fun `anything else is not a sound path`() {
        listOf(
            "whistle-mp3/D5.wav",
            "whistle-mp3/../D5.mp3",
            "whistle/D5.mp3",
            "whistle-mp3/D#5.mp3",
            "whistle-mp3/D5.mp3?x=1",
            "",
        ).forEach { assertNull(it, SoundPathHandler.noteNameIn(it)) }
    }

    @Test
    fun `serves synthesised WAV and renders each note once`() {
        var renders = 0
        val handler = SoundPathHandler { name -> renders++; WhistleSynth.wavForNote(name) }

        val first = handler.bytesFor("whistle-mp3/D5.mp3")
        val second = handler.bytesFor("flute-mp3/D5.mp3")

        assertArrayEquals(WhistleSynth.wavForNote("D5"), first)
        assertArrayEquals(first, second)
        assertEquals(1, renders)
    }

    @Test
    fun `handle answers audio-wav and declines unknown paths`() {
        val handler = SoundPathHandler()
        val response = handler.handle("whistle-mp3/A5.mp3")!!
        assertEquals("audio/wav", response.mimeType)
        assertEquals("RIFF", String(response.data.readNBytes(4), Charsets.US_ASCII))
        assertNull(handler.handle("whistle-mp3/nope.mp3"))
    }

    @Test
    fun `asset loader routes bundled pages and sounds on the app origin only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = buildAssetLoader(context)

        val page = loader.shouldInterceptRequest(Uri.parse(PAGE_URL))
        assertNotNull(page)
        assertEquals("text/html", page!!.mimeType)

        val sound = loader.shouldInterceptRequest(Uri.parse("$APP_ORIGIN/sound/whistle-mp3/D5.mp3"))
        assertEquals("audio/wav", sound!!.mimeType)

        assertNull(loader.shouldInterceptRequest(Uri.parse("https://example.com/assets/web/index.html")))
        assertNull(loader.shouldInterceptRequest(Uri.parse("$APP_ORIGIN/other/thing")))
    }

    @Test
    fun `bundled page scripts are all present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val html = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
        Regex("""<script src="([^"]+)"""").findAll(html).forEach {
            context.assets.open("web/${it.groupValues[1]}").close()
        }
        assertEquals("6.7.2", context.assets.open("web/abcjs-VERSION").bufferedReader().use { it.readText().trim() })
    }
}

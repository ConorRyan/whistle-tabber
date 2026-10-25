package io.github.conorryan.whistletabber.web

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end through a real WebView: abcjs renders the bundled tune, the page
 * reports its notes, the synthesised samples decode, and playback runs.
 * Emulator only (see AGENTS.md).
 */
@RunWith(AndroidJUnit4::class)
class TuneWebControllerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: TuneWebController
    private lateinit var abc: String

    @Before
    fun setUp() {
        compose.runOnUiThread {
            controller = TuneWebController(compose.activity)
            abc = compose.activity.assets.open("tunes/ballyhooley.abc").bufferedReader().use { it.readText() }
        }
        compose.setContent { TuneWebViewHost(controller, Modifier.fillMaxSize()) }
    }

    private suspend inline fun <reified T : BridgeEvent> awaitEvent(timeoutMs: Long = 20_000): T =
        withTimeout(timeoutMs) { controller.events.first { it is T || it is BridgeEvent.Error }.let { it as? T ?: error("Page error: $it") } }

    /** Subscribes immediately, before the command that triggers the event is sent ([events] has no replay). */
    /** Each WebView holds an AudioContext; Chromium caps how many may live at once. */
    @After
    fun tearDown() {
        compose.runOnUiThread { controller.destroy() }
    }

    private fun <T> CoroutineScope.listen(block: suspend CoroutineScope.() -> T): Deferred<T> =
        async(start = CoroutineStart.UNDISPATCHED, block = block)

    private fun send(command: BridgeCommand) = compose.runOnUiThread { controller.send(command) }

    private suspend fun js(script: String): String {
        val result = CompletableDeferred<String>()
        withContext(Dispatchers.Main) { controller.webView.evaluateJavascript(script) { result.complete(it) } }
        return withTimeout(5_000) { result.await() }
    }

    @Test
    fun rendersTuneAndReportsNotes() = runBlocking {
        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render(abc))
        val event = rendered.await()

        assertEquals(88, event.notes.size)
        assertEquals(listOf<String>(), event.warnings)
        assertEquals(16, event.notes.last().bar)
        assertEquals("1", js("document.querySelectorAll('#paper svg').length"))
        val noteCount = js("document.querySelectorAll('#paper .abcjs-note').length").toInt()
        assertEquals(88, noteCount)
    }

    @Test
    fun synthesisedSamplesDecodeAndPlaybackAdvances() = runBlocking {
        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render(abc))
        rendered.await()

        val loaded = listen { awaitEvent<BridgeEvent.AudioLoaded>() }
        val playing = listen {
            withTimeout(30_000) {
                controller.events.first { it is BridgeEvent.Playing && (it.measure ?: 0) >= 1 }
            }
        }
        send(BridgeCommand.Play)

        val audio = loaded.await()
        assertEquals("every sample should decode", listOf<String>(), audio.errors)
        assertTrue("expected several distinct notes, got ${audio.loaded}", audio.loaded >= 8)
        playing.await()
        assertTrue(js("document.querySelectorAll('#paper .abcjs-highlight').length").toInt() > 0)

        send(BridgeCommand.Pause)
    }

    @Test
    fun commandsSentBeforeReadyAreDelivered() = runBlocking {
        // setUp sent nothing; the first render races the page load and must be queued.
        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render("X:1\nT:Short\nM:4/4\nL:1/4\nK:D\nDEFG|\n"))
        assertEquals(listOf(62, 64, 66, 67), rendered.await().notes.map { it.midi })
    }

    @Test
    fun transposeTextRewritesTheKeyAndEveryPitch() = runBlocking {
        val original = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render(abc))
        val before = original.await().notes.map { it.midi }

        val transposed = listen { awaitEvent<BridgeEvent.TransposedText>() }
        send(BridgeCommand.TransposeText(abc, -5)) // G to D
        val event = transposed.await()
        assertEquals(-5, event.steps)
        assertTrue(Regex("""^K:\s*D\s*$""", RegexOption.MULTILINE).containsMatchIn(event.abc))

        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render(event.abc))
        assertEquals(before.map { it?.minus(5) }, rendered.await().notes.map { it.midi })
    }

    @Test
    fun abcWithQuotesAndBackslashesSurvivesTheBridge() = runBlocking {
        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render("X:1\nT:Tom's \"Reel\"\nM:4/4\nL:1/4\nK:D\nDE \\\nFG|\n"))
        assertEquals(4, rendered.await().notes.size)
        assertEquals("\"Tom's \\\"Reel\\\"\"", js("document.querySelector('#paper .abcjs-title').textContent"))
    }
}

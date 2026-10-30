package io.github.conorryan.whistletabber.web

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.WhistleKey
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
            abc = InstrumentationRegistry.getInstrumentation().context.assets.open("tunes/ballyhooley.abc").bufferedReader().use { it.readText() }
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

    /** Every fingering in an order that puts naturals and sharps of one letter in the same bar. */
    private val tapped = listOf(10, 11, 22, 23, 9, 10, 8, 9, 0, 1, 2, 3, 3, 4, 5, 6, 6, 7, 12, null, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 21, 20)

    /**
     * The page reports `rendered` again when its width changes, so a late
     * report of the last tune can arrive; wait for the one with [expected] notes.
     */
    private suspend fun awaitNotes(expected: List<Int?>, send: () -> Unit) = coroutineScope {
        var seen: List<Int?>? = null
        val match = listen {
            withTimeoutOrNull(20_000) {
                controller.events.first { event ->
                    if (event is BridgeEvent.Error) error("Page error: $event")
                    (event as? BridgeEvent.Rendered)?.notes?.map { it.midi }?.also { seen = it } == expected
                }
            }
        }
        send()
        assertEquals(expected, if (match.await() != null) expected else seen)
    }

    @Test
    fun tappedTunesPlayTheTappedPitchesOnEveryWhistle() = runBlocking {
        for (whistle in WhistleKey.entries) {
            awaitNotes(tapped.filterNotNull().map { whistle.writtenTonic + it }) {
                send(BridgeCommand.Render(TapTune.toAbc(TapDraft("Taps", whistle, tapped))))
            }
        }
    }

    /**
     * Bottom two octaves only: abcjs 6.7.2's strTranspose drops the natural
     * from `=f' ^f'` (it writes `_e' e'`), so third-octave pairs like that
     * change pitch in any tune.
     */
    @Test
    fun aTappedTuneChangesKeyWithEveryPitch() = runBlocking {
        val tapped = tapped.filter { it == null || it < 24 }
        val tune = TapTune.toAbc(TapDraft("Taps", WhistleKey.D, tapped))
        val transposed = listen { awaitEvent<BridgeEvent.TransposedText>() }
        send(BridgeCommand.TransposeText(tune, -2)) // D to C
        val abc = transposed.await().abc
        awaitNotes(tapped.filterNotNull().map { 60 + it }) { send(BridgeCommand.Render(abc)) }
    }
}

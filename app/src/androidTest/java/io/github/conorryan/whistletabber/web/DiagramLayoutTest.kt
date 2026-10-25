package io.github.conorryan.whistletabber.web

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.WhistleSynth
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fingering diagrams drawn into abcjs's SVG: one per note, under its note,
 * clear of the next staff line and of each other. Emulator only.
 */
@RunWith(AndroidJUnit4::class)
class DiagramLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: TuneWebController
    private val requestedNotes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Before
    fun setUp() {
        compose.runOnUiThread {
            val sound = SoundPathHandler { name -> requestedNotes += name; WhistleSynth.wavForNote(name) }
            controller = TuneWebController(compose.activity, sound)
        }
        compose.setContent { TuneWebViewHost(controller, Modifier.fillMaxSize()) }
    }

    /** Each WebView holds an AudioContext; Chromium caps how many may live at once. */
    @After
    fun tearDown() {
        compose.runOnUiThread { controller.destroy() }
    }

    /** Fixtures live in the test APK's assets (app/src/androidTest/assets/). */
    private fun asset(name: String) =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() }

    private fun <T> CoroutineScope.listen(block: suspend CoroutineScope.() -> T): Deferred<T> =
        async(start = CoroutineStart.UNDISPATCHED, block = block)

    private suspend inline fun <reified T : BridgeEvent> awaitEvent(timeoutMs: Long = 20_000): T =
        withTimeout(timeoutMs) { controller.events.first { it is T || it is BridgeEvent.Error }.let { it as? T ?: error("Page error: $it") } }

    private fun send(command: BridgeCommand) = compose.runOnUiThread { controller.send(command) }

    private suspend fun js(script: String): String {
        val result = CompletableDeferred<String>()
        withContext(Dispatchers.Main) { controller.webView.evaluateJavascript(script) { result.complete(it) } }
        return withTimeout(5_000) { result.await() }
    }

    private suspend fun layout(): List<JsonObject> =
        Json.parseToJsonElement(js("window.whistlePage.layoutReport()")).jsonArray.map { it.jsonObject }

    /** Renders [abc], sends diagrams for [settings], waits for them to be drawn; returns the specs. */
    private suspend fun CoroutineScope.renderWithDiagrams(
        abc: String,
        settings: TabSettings = TabSettings(),
        midiTranspose: Int = 12,
    ): List<DiagramSpec?> {
        val rendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        send(BridgeCommand.Render(abc, midiTranspose = midiTranspose))
        val notes = rendered.await().notes
        val specs = FingeringEngine.forNotes(notes, settings, WhistleKey.D).map(DiagramSpec::from)
        send(BridgeCommand.Diagrams(specs))
        withTimeout(5_000) {
            while (js("document.querySelectorAll('.wt-diagram').length").toInt() != specs.count { it != null }) delay(50)
        }
        return specs
    }

    private fun JsonObject.d(key: String) = getValue(key).jsonPrimitive.double

    @Test
    fun oneDiagramPerSoundingNote_ballyhooley() = runBlocking {
        val specs = renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        assertEquals(88, specs.count { it != null })
        assertEquals((0 until 88).toList(), layout().map { it.getValue("index").jsonPrimitive.int })
    }

    @Test
    fun diagramsSitUnderTheirNotesAndClearOfTheNextLine() = runBlocking {
        renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        val report = layout()
        assertTrue(report.isNotEmpty())
        for (d in report) {
            val i = d.getValue("index")
            // Centred over the note head column (allowing for stems and accidentals).
            assertTrue("diagram $i x=${d.d("centerX")} not under note ${d.d("noteLeft")}..${d.d("noteRight")}",
                d.d("centerX") >= d.d("noteLeft") - 2 && d.d("centerX") <= d.d("noteRight") + 2)
            assertTrue("diagram $i overlaps its own staff line", d.d("top") >= d.d("lineBottom"))
            d.getValue("nextLineTop").jsonPrimitive.doubleOrNull?.let { next ->
                assertTrue("diagram $i (bottom ${d.d("bottom")}) runs into the next line ($next)", d.d("bottom") <= next)
            }
        }
    }

    @Test
    fun neighbouringDiagramsDoNotOverlap() = runBlocking {
        renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        layout().groupBy { it.getValue("line").jsonPrimitive.int }.forEach { (line, diagrams) ->
            diagrams.sortedBy { it.d("left") }.zipWithNext().forEach { (a, b) ->
                assertTrue("line $line: diagrams ${a["index"]} and ${b["index"]} overlap", a.d("right") <= b.d("left"))
            }
        }
    }

    @Test
    fun secondOctaveNotesCarryAPlus() = runBlocking {
        val specs = renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        val plusCount = js("document.querySelectorAll('.wt-diagram .wt-octave').length").toInt()
        assertEquals(specs.count { it != null && it.octave == 1 }, plusCount)
        assertTrue(plusCount > 0)
    }

    @Test
    fun lowNotesBelowTheStaffStillClearTheNextLine() = runBlocking {
        // Notes under the staff, chords below and a tied note push the staff's bottom down.
        val abc = "X:1\nT:Low\nM:4/4\nL:1/8\nK:D\n" +
            "|:\"_low\"D,2 E,F, G,A,B,C|D2 E2- E2 F2|A,B,CD EFGA|Bcde f2 d2:|\n" +
            "|:d'c'ba gfed|cBAG FEDC|D8|d8:|\n"
        val specs = renderWithDiagrams(abc, TabSettings(foldOutOfRange = true))
        assertTrue("folded notes expected", specs.any { (it?.folded ?: 0) != 0 })
        for (d in layout()) {
            d.getValue("nextLineTop").jsonPrimitive.doubleOrNull?.let { next ->
                assertTrue("diagram ${d["index"]} runs into the next line: $d", d.d("bottom") <= next)
            }
        }
    }

    @Test
    fun outOfRangeNotesShowAQuestionMarkWhenNotFolding() = runBlocking {
        val abc = "X:1\nT:Range\nM:4/4\nL:1/4\nK:C\nC D E F|\n"
        val specs = renderWithDiagrams(abc, TabSettings(foldOutOfRange = false))
        assertTrue(specs[0]!!.outOfRange) // written C4 is below a D whistle
        assertEquals("1", js("document.querySelectorAll('g.wt-out-of-range').length"))
    }

    @Test
    fun diagramsAreRedrawnWhenTheWidthChanges() = runBlocking {
        renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        val linesBefore = js("document.querySelectorAll('.abcjs-staff-wrapper').length").toInt()
        val rerendered = listen { awaitEvent<BridgeEvent.Rendered>() }
        js("document.getElementById('sheet').style.width = '260px'; window.dispatchEvent(new Event('resize')); 1")
        rerendered.await()
        val linesAfter = js("document.querySelectorAll('.abcjs-staff-wrapper').length").toInt()
        assertTrue("narrower page should wrap to more lines ($linesBefore -> $linesAfter)", linesAfter > linesBefore)
        assertEquals("88", js("document.querySelectorAll('.wt-diagram').length"))
    }

    @Test
    fun sameFingeringsOnACWhistlePlaysATonelower() = runBlocking {
        val abc = asset("tunes/ballyhooley.abc")
        val settings = TabSettings(whistle = WhistleKey.C, mode = TransposeMode.SAME_FINGERINGS)
        val specsD = renderWithDiagrams(abc, TabSettings(), midiTranspose = TabSettings().midiTranspose(WhistleKey.D))
        val specsC = renderWithDiagrams(abc, settings, midiTranspose = settings.midiTranspose(WhistleKey.D))
        assertEquals("fingerings don't change", specsD, specsC)

        val loaded = listen { awaitEvent<BridgeEvent.AudioLoaded>() }
        send(BridgeCommand.Play)
        assertEquals(listOf<String>(), loaded.await().errors)
        send(BridgeCommand.Pause)
        // Written F#4 sounds as E5 on a C whistle (not F#5 as on a D whistle).
        assertTrue("requested $requestedNotes", "E5" in requestedNotes)
        assertFalse("requested $requestedNotes", "Gb5" in requestedNotes)
    }

    @Test
    fun highlightedNoteHighlightsItsDiagram() = runBlocking {
        renderWithDiagrams(asset("tunes/ballyhooley.abc"))
        val seen = Collections.synchronizedList(mutableListOf<BridgeEvent>())
        val playing = listen {
            runCatching {
                withTimeout(30_000) { controller.events.first { seen += it; it is BridgeEvent.Playing } }
            }.getOrElse { error("no playing event; saw ${seen.map { it::class.simpleName + (if (it is BridgeEvent.Error) ": " + it.message else "") }}") }
        }
        send(BridgeCommand.Play)
        playing.await()
        val counts = mutableListOf<Int>()
        runCatching {
            withTimeout(5_000) {
                while (true) {
                    val n = js("document.querySelectorAll('.wt-diagram.wt-highlight').length").toInt()
                    counts += n
                    if (n == 1) break
                    delay(20)
                }
            }
        }.onFailure { error("highlighted diagram counts seen: ${counts.distinct()}; playing class count: ${js("document.querySelectorAll('.abcjs-highlight').length")}") }
        send(BridgeCommand.Pause)
    }
}

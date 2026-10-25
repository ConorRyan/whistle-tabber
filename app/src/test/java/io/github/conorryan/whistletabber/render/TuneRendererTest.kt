package io.github.conorryan.whistletabber.render

import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The render cycle, tested through the TuneRenderer interface with a fake page. */
@OptIn(ExperimentalCoroutinesApi::class)
class TuneRendererTest {

    private val abc = "X:1\nT:Test Tune\nK:D\nGAc|\n"

    // Written G, A, c (C natural) and a tied continuation.
    private val notes = listOf(
        NoteEvent(0, 10, 11, 67, 0, 0.0, 0.125, false),
        NoteEvent(1, 11, 12, 69, 0, 0.125, 0.125, false),
        NoteEvent(2, 12, 13, 72, 0, 0.25, 0.125, false),
        NoteEvent(3, 13, 14, 72, 0, null, null, true),
    )

    private val dWhistle = TuneRenderer.FingeringInputs(TabSettings(), WhistleKey.D)
    private val cWhistle = TuneRenderer.FingeringInputs(TabSettings(whistle = WhistleKey.C), WhistleKey.D)

    private fun renderer(scope: CoroutineScope, debounceMs: Long = 0) = TuneRenderer(scope, debounceMs)

    @Test
    fun `a show before attach is held and delivered when a page attaches`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12))
        assertTrue(page.sent.isEmpty())
        r.attach(page)
        runCurrent()
        assertEquals(listOf(BridgeCommand.Render(abc, midiTranspose = 12)), page.sent)
    }

    @Test
    fun `a repeat show sends nothing, and a re-emit with identical fingerings sends no duplicate diagrams`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12, fingering = dWhistle))
        page.emit(BridgeEvent.Rendered(notes, emptyList()))
        runCurrent()
        assertEquals(2, page.sent.size) // Render + Diagrams

        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12, fingering = dWhistle))
        runCurrent()
        assertEquals(2, page.sent.size) // nothing new
    }

    @Test
    fun `a settings-only change redraws diagrams without re-rendering`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12, fingering = dWhistle))
        page.emit(BridgeEvent.Rendered(notes, emptyList()))
        runCurrent()
        assertEquals(BridgeCommand.Render::class, page.sent[0]::class)
        assertEquals(BridgeCommand.Diagrams::class, page.sent[1]::class)

        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12, fingering = dWhistle.copy(settings = TabSettings(cNatural = CNatural.HALF_TOP))))
        runCurrent()
        assertEquals(3, page.sent.size) // no second Render
        val diagrams = (page.sent[2] as BridgeCommand.Diagrams).diagrams
        assertEquals("HOOOOO", diagrams[2]!!.holes)
    }

    @Test
    fun `new text or transpose re-renders and stops playback`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12))
        page.emit(BridgeEvent.Rendered(notes, emptyList()))
        page.emit(BridgeEvent.Playing(10, 2))
        runCurrent()
        assertTrue(r.state.value.playing)
        assertEquals(2, r.state.value.bar)

        r.show(TuneRenderer.RenderRequest("X:1\nT:Test Tune\nK:G\ncdf|", midiTranspose = 12))
        runCurrent()
        assertEquals(BridgeCommand.Render("X:1\nT:Test Tune\nK:G\ncdf|", midiTranspose = 12), page.sent.last())
        assertFalse(r.state.value.playing)
        assertNull(r.state.value.bar)
    }

    @Test
    fun `a render without fingerings never draws diagrams`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc)) // the editor
        page.emit(BridgeEvent.Rendered(notes, emptyList()))
        runCurrent()
        assertEquals(listOf(BridgeCommand.Render(abc)), page.sent)
    }

    @Test
    fun `transpose resolves with the matching reply and drops a stale one`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        var result: String? = "unset"
        backgroundScope.launch { result = r.transpose(abc, 5) }
        runCurrent()
        assertEquals(BridgeCommand.TransposeText(abc, 5), page.sent.last())

        page.emit(BridgeEvent.TransposedText("X:1\nT:Test Tune\nK:X\n", 7)) // stale steps
        runCurrent()
        assertEquals("unset", result)

        page.emit(BridgeEvent.TransposedText("X:1\nT:Test Tune\nK:G\ncdf|", 5))
        runCurrent()
        assertEquals("X:1\nT:Test Tune\nK:G\ncdf|", result)
    }

    @Test
    fun `a newer transpose supersedes the older one, which returns null`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        var older: String? = "unset"
        var newer: String? = "unset"
        backgroundScope.launch { older = r.transpose(abc, 5) }
        runCurrent()
        backgroundScope.launch { newer = r.transpose(abc, -5) }
        runCurrent()
        assertEquals("unset", newer)

        page.emit(BridgeEvent.TransposedText("X:1\nT:Test Tune\nK:A\n", -5))
        runCurrent()
        assertNull(older)
        assertEquals("X:1\nT:Test Tune\nK:A\n", newer)
    }

    @Test
    fun `a page error fails the pending transpose and surfaces in state`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        var result: String? = "unset"
        backgroundScope.launch { result = r.transpose(abc, 5) }
        runCurrent()

        page.emit(BridgeEvent.Error("boom"))
        runCurrent()
        assertNull(result)
        assertEquals("boom", r.state.value.error)
    }

    @Test
    fun `a fresh page is attached by re-showing it the last request`() = runTest {
        val first = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(first)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc, midiTranspose = 12))
        first.emit(BridgeEvent.Playing(10, 2))
        runCurrent()
        assertTrue(r.state.value.playing)

        val second = FakeTunePage()
        r.attach(second)
        assertEquals(listOf(BridgeCommand.Render(abc, midiTranspose = 12)), second.sent)
        assertFalse(r.state.value.playing)
    }

    @Test
    fun `playback controls translate to commands and events mirror the state`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()

        r.togglePlay()
        r.restart()
        r.setTempo(80)
        runCurrent()
        assertEquals(
            listOf(BridgeCommand.Play, BridgeCommand.Seek(0.0), BridgeCommand.Tempo(80)),
            page.sent,
        )
        assertTrue(r.state.value.playing) // optimistic
        assertEquals(80, r.state.value.tempo)

        r.togglePlay()
        runCurrent()
        assertEquals(BridgeCommand.Pause, page.sent.last())
        assertFalse(r.state.value.playing)

        page.emit(BridgeEvent.Finished)
        runCurrent()
        assertFalse(r.state.value.playing)
        assertNull(r.state.value.bar)

        page.emit(BridgeEvent.Playing(10, 3))
        runCurrent()
        assertTrue(r.state.value.playing)
        assertEquals(3, r.state.value.bar)

        r.dismissError()
        assertNull(r.state.value.error)
    }

    @Test
    fun `shows are debounced and the newest one wins`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope, debounceMs = 400)
        r.attach(page)
        runCurrent()

        r.show(TuneRenderer.RenderRequest(abc))
        r.show(TuneRenderer.RenderRequest("X:1\nT:Test Tune\nK:G\ncdf|"))
        advanceTimeBy(399)
        assertTrue(page.sent.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(BridgeCommand.Render("X:1\nT:Test Tune\nK:G\ncdf|")), page.sent)
    }

    @Test
    fun `warnings come from the last render`() = runTest {
        val page = FakeTunePage()
        val r = renderer(backgroundScope)
        r.attach(page)
        runCurrent()
        r.show(TuneRenderer.RenderRequest(abc))
        page.emit(BridgeEvent.Rendered(notes, listOf("w")))
        runCurrent()
        assertEquals(listOf("w"), r.state.value.warnings)
    }
}

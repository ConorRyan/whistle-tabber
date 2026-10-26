package io.github.conorryan.whistletabber.ui.tune

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.FakeTunePage
import io.github.conorryan.whistletabber.ui.TestGraph
import io.github.conorryan.whistletabber.ui.settings.TabSettingsEditor
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TuneViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var graph: TestGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = TestGraph(tmp.root)
    }

    @After
    fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private val abc = "X:1\nT:Test Tune\nK:D\nGAc|\n"

    // Written G, A, c (C natural) and a tied continuation.
    private val notes = listOf(
        NoteEvent(0, 10, 11, 67, 0, 0.0, 0.125, false),
        NoteEvent(1, 11, 12, 69, 0, 0.125, 0.125, false),
        NoteEvent(2, 12, 13, 72, 0, 0.25, 0.125, false),
        NoteEvent(3, 13, 14, 72, 0, null, null, true),
    )

    private fun saved(text: String = abc): Long = runBlocking { graph.repository.save(text) }

    private fun editor(scope: CoroutineScope) = TabSettingsEditor(graph.store, scope)

    /** A ViewModel with a fake page bound, as the screen would bind the WebView. */
    private fun bound(id: Long, editor: TabSettingsEditor): Pair<TuneViewModel, FakeTunePage> {
        val page = FakeTunePage()
        return TuneViewModel(id, graph.repository, editor, graph.files, graph.recentlyDeleted)
            .also { it.bindPage(page) } to page
    }

    @Test
    fun `renders at soprano pitch, then sends diagrams for the rendered notes`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 12), awaitItem())
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals(listOf("CCCOOO", "CCOOOO", "OCCOOO"), diagrams.take(3).map { it!!.holes })
            assertNull(diagrams[3])
        }
        assertEquals("Test Tune", vm.state.value.tune?.title)
    }

    @Test
    fun `changing C natural re-sends diagrams without re-rendering`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem() // render
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem() // first diagrams
            vm.settingsEditor.setCNatural(CNatural.HALF_TOP)
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("HOOOOO", diagrams[2]!!.holes)
            expectNoEvents()
        }
    }

    @Test
    fun `same-pitches change of my whistle keeps playback but changes fingerings`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem()
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem()
            vm.settingsEditor.setWhistle(WhistleKey.C) // C soprano: same +12 transpose
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("CCOOOO", diagrams[0]!!.holes) // G on a C whistle
            expectNoEvents()
        }
    }

    @Test
    fun `a change of playback transpose re-renders, and diagrams follow the new render`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem()
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem()
            vm.settingsEditor.setMode(TransposeMode.SAME_FINGERINGS) // D whistle: still +12, diagrams only
            assertEquals(BridgeCommand.Diagrams::class, awaitItem()::class)
            vm.settingsEditor.setWhistle(WhistleKey.C) // now plays a tone lower
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 10), awaitItem())
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("CCCOOO", diagrams[0]!!.holes) // still D-whistle fingerings
        }
    }

    @Test
    fun `low whistle renders without the octave shift`() = runTest {
        graph.store.setWhistle(WhistleKey.LOW_D)
        val (_, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 0), awaitItem())
        }
    }

    @Test
    fun `the tune's own whistle sets the fingerings kept in same-fingerings mode`() = runTest {
        graph.store.setMode(TransposeMode.SAME_FINGERINGS)
        val text = AbcBook.withWhistle(abc, WhistleKey.C)
        val (vm, page) = bound(saved(text), editor(backgroundScope))
        page.commands.test {
            // C-whistle fingerings played on my D whistle sound a tone higher.
            assertEquals(BridgeCommand.Render(text, midiTranspose = 14), awaitItem())
            page.emit(BridgeEvent.Rendered(notes, emptyList()))
            assertEquals("CCOOOO", (awaitItem() as BridgeCommand.Diagrams).diagrams[0]!!.holes) // G on a C whistle
        }
        assertEquals(WhistleKey.C, vm.state.value.tuneWhistle)
    }

    @Test
    fun `setting the tune's whistle writes the directive and re-renders`() = runTest {
        graph.store.setMode(TransposeMode.SAME_FINGERINGS)
        val id = saved()
        val (vm, page) = bound(id, editor(backgroundScope))
        page.commands.test {
            awaitItem()
            vm.setTuneWhistle(WhistleKey.C)
            val render = awaitItem() as BridgeCommand.Render
            assertEquals(WhistleKey.C, AbcBook.whistleOf(render.abc))
            assertEquals(14, render.midiTranspose)
        }
        assertEquals(WhistleKey.C, graph.repository.get(id)!!.whistle)
    }

    @Test
    fun `key change previews, then saves a copy and opens it`() = runTest {
        val id = saved()
        turbineScope {
            val (vm, page) = bound(id, editor(backgroundScope))
            val commands = page.commands.testIn(backgroundScope)
            val effects = vm.effects.testIn(backgroundScope)
            commands.awaitItem() // render
            vm.previewKey("G")
            assertEquals(BridgeCommand.TransposeText(abc, 5), commands.awaitItem())
            assertEquals(KeyChangePreview.Preview("G", "G", 5), vm.state.value.preview)

            val inG = "X:1\nT:Test Tune\nK:G\ncdf|\n"
            page.emit(BridgeEvent.TransposedText(inG, 5))
            assertEquals(BridgeCommand.Render(inG, midiTranspose = 12), commands.awaitItem())

            vm.savePreviewAsCopy()
            val newId = (effects.awaitItem() as TuneEffect.OpenTune).id
            val copy = graph.repository.get(newId)!!
            assertEquals("Test Tune (G)", copy.title)
            assertEquals("G", copy.key)
            assertEquals(abc, graph.repository.get(id)!!.abc) // the original is untouched
            assertNull(vm.state.value.preview)
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 12), commands.awaitItem()) // back to the original
        }
    }

    @Test
    fun `a rewritten text for other steps is ignored`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem()
            vm.previewKey("A") // D to A: down a fourth
            assertEquals(BridgeCommand.TransposeText(abc, -5), awaitItem())
            page.emit(BridgeEvent.TransposedText("X:1\nT:Test Tune\nK:A\n|\n", 7)) // a stale reply
            expectNoEvents()
        }
        assertEquals(KeyChangePreview.Preview("A", "A", -5), vm.state.value.preview)
    }

    @Test
    fun `replace saves the previewed text over the tune`() = runTest {
        val id = saved()
        val (vm, page) = bound(id, editor(backgroundScope))
        page.commands.test {
            awaitItem()
            vm.previewKey("E")
            assertEquals(BridgeCommand.TransposeText(abc, 2), awaitItem())
            val inE = "X:1\nT:Test Tune\nK:E\nABd|\n"
            page.emit(BridgeEvent.TransposedText(inE, 2))
            assertEquals(BridgeCommand.Render(inE, midiTranspose = 12), awaitItem())
            vm.replaceWithPreview()
            expectNoEvents() // the same text is already on the page
        }
        assertEquals("E", graph.repository.observe(id).first { it?.key == "E" }!!.key)
        assertNull(vm.state.value.preview)
    }

    @Test
    fun `picking the current key clears the preview`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem()
            vm.previewKey("D")
            expectNoEvents()
        }
        assertNull(vm.state.value.preview)
    }

    @Test
    fun `delete closes the screen and leaves the tune for the library's undo`() = runTest {
        val id = saved()
        val (vm, _) = bound(id, editor(backgroundScope))
        vm.effects.test {
            vm.delete()
            assertEquals(TuneEffect.Close, awaitItem())
        }
        assertNull(graph.repository.get(id))
        assertEquals(id, graph.recentlyDeleted.offer.value?.single()?.id)
        assertEquals(false, vm.state.value.missing)
    }

    @Test
    fun `an unknown id is missing`() = runTest {
        val (vm, _) = bound(999, editor(backgroundScope))
        assertNull(vm.state.first { it.missing }.tune)
    }

    @Test
    fun `playback controls forward commands`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.commands.test {
            awaitItem()
            vm.togglePlay()
            vm.togglePlay()
            vm.restart()
            vm.setTempo(80)
            assertEquals(BridgeCommand.Play, awaitItem())
            assertEquals(BridgeCommand.Pause, awaitItem())
            assertEquals(BridgeCommand.Seek(0.0), awaitItem())
            assertEquals(BridgeCommand.Tempo(80), awaitItem())
        }
        assertEquals(80, vm.renderState.value.tempo)
    }

    @Test
    fun `render state follows page events`() = runTest {
        val (vm, page) = bound(saved(), editor(backgroundScope))
        page.emit(BridgeEvent.Rendered(notes, listOf("w")))
        assertEquals(listOf("w"), vm.renderState.value.warnings)
        page.emit(BridgeEvent.Playing(10, 2))
        assertEquals(2, vm.renderState.value.bar)
        assertTrue(vm.renderState.value.playing)
        page.emit(BridgeEvent.Finished)
        assertNull(vm.renderState.value.bar)
        assertEquals(false, vm.renderState.value.playing)
        page.emit(BridgeEvent.Error("boom"))
        assertEquals("boom", vm.renderState.value.error)
        vm.dismissError()
        assertNull(vm.renderState.value.error)
    }
}

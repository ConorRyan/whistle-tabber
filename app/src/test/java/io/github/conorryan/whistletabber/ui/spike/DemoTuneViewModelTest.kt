package io.github.conorryan.whistletabber.ui.spike

import app.cash.turbine.test
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.DiagramSpec
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTuneViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: TabSettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = TabSettingsStore(
            PreferenceDataStoreFactory.create(produceFile = { tmp.newFile("tab.preferences_pb").also { it.delete() } }),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val abc = "X:1\nT:t\nK:D\nGAc|\n"

    // Written G, A, c (C natural) and a tied continuation.
    private val notes = listOf(
        NoteEvent(0, 10, 11, 67, 0, 0.0, 0.125, false),
        NoteEvent(1, 11, 12, 69, 0, 0.125, 0.125, false),
        NoteEvent(2, 12, 13, 72, 0, 0.25, 0.125, false),
        NoteEvent(3, 13, 14, 72, 0, null, null, true),
    )

    @Test
    fun `renders at soprano pitch, then sends diagrams for the rendered notes`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 12), awaitItem())
            vm.onEvent(BridgeEvent.Rendered(notes, emptyList()))
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals(listOf("CCCOOO", "CCOOOO", "OCCOOO"), diagrams.take(3).map { it!!.holes })
            assertNull(diagrams[3])
        }
    }

    @Test
    fun `changing C natural re-sends diagrams without re-rendering`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            awaitItem() // render
            vm.onEvent(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem() // first diagrams
            vm.setCNatural(CNatural.HALF_TOP)
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("HOOOOO", diagrams[2]!!.holes)
            expectNoEvents()
        }
    }

    @Test
    fun `same-pitches whistle change keeps playback but changes fingerings`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            awaitItem()
            vm.onEvent(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem()
            vm.setWhistle(WhistleKey.C) // C soprano: same +12 transpose
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("CCOOOO", diagrams[0]!!.holes) // G on a C whistle
            expectNoEvents()
        }
    }

    @Test
    fun `a change of playback transpose re-renders, and diagrams follow the new render`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            awaitItem()
            vm.onEvent(BridgeEvent.Rendered(notes, emptyList()))
            awaitItem()
            vm.setMode(TransposeMode.SAME_FINGERINGS) // D whistle: still +12, diagrams only
            assertEquals(BridgeCommand.Diagrams::class, awaitItem()::class)
            vm.setWhistle(WhistleKey.C) // now plays a tone lower
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 10), awaitItem())
            vm.onEvent(BridgeEvent.Rendered(notes, emptyList()))
            val diagrams = (awaitItem() as BridgeCommand.Diagrams).diagrams
            assertEquals("CCCOOO", diagrams[0]!!.holes) // still D-whistle fingerings
        }
    }

    @Test
    fun `low whistle renders without the octave shift`() = runTest {
        store.setWhistle(WhistleKey.LOW_D)
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            assertEquals(BridgeCommand.Render(abc, midiTranspose = 0), awaitItem())
        }
    }

    @Test
    fun `playback controls forward commands`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.commands.test {
            awaitItem()
            vm.play()
            vm.pause()
            vm.restart()
            vm.setTempo(80)
            assertEquals(BridgeCommand.Play, awaitItem())
            assertEquals(BridgeCommand.Pause, awaitItem())
            assertEquals(BridgeCommand.Seek(0.0), awaitItem())
            assertEquals(BridgeCommand.Tempo(80), awaitItem())
        }
    }

    @Test
    fun `status reflects page events`() = runTest {
        val vm = DemoTuneViewModel(store, abc)
        vm.onEvent(BridgeEvent.Rendered(notes, listOf("w")))
        assertEquals("4 notes, 1 warnings", vm.state.value.status)
        vm.onEvent(BridgeEvent.Playing(10, 2))
        assertEquals("Bar 3", vm.state.value.status)
        vm.onEvent(BridgeEvent.Ready("v"))
        assertEquals("Bar 3", vm.state.value.status)
        vm.onEvent(BridgeEvent.Error("boom"))
        assertEquals("Error: boom", vm.state.value.status)
    }

    @Test
    fun `diagram specs carry octave, hard, folded and out-of-range`() {
        val spec = DiagramSpec.from(
            io.github.conorryan.whistletabber.music.FingeringEngine.fingeringFor(62 + 29, WhistleKey.D, CNatural.DEFAULT, fold = false),
        )!!
        assertEquals(DiagramSpec("CCCOOC", octave = 2, hard = true), spec)
        assertEquals(
            DiagramSpec(outOfRange = true),
            DiagramSpec.from(io.github.conorryan.whistletabber.music.FingeringEngine.fingeringFor(50, WhistleKey.D, CNatural.DEFAULT, fold = false)),
        )
        assertEquals(
            DiagramSpec("OOOOOO", folded = 1),
            DiagramSpec.from(io.github.conorryan.whistletabber.music.FingeringEngine.fingeringFor(61, WhistleKey.D, CNatural.DEFAULT, fold = true)),
        )
        assertNull(DiagramSpec.from(null))
    }
}

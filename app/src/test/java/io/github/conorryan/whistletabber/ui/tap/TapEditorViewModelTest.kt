package io.github.conorryan.whistletabber.ui.tap

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.FakeTunePage
import io.github.conorryan.whistletabber.ui.TestGraph
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TapEditorViewModelTest {

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

    private fun vm(
        id: Long? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        settings: Flow<TabSettings> = flowOf(TabSettings(whistle = WhistleKey.C)),
    ) = TapEditorViewModel(id, saved, graph.repository, settings, debounceMs = 0)

    private suspend fun TapEditorViewModel.loaded(): TapEditorUiState = state.first { it.draft != null }

    private val tapped = "X:1\nT:Tapped\nZ:Me\nM:4/4\nL:1/4\n%whistle_tab_key d\nK:D\nD E F |]\n"

    @Test
    fun `a new tune starts empty, for my whistle`() = runTest {
        val s = vm().loaded()
        assertEquals(TapDraft("", WhistleKey.C, emptyList()), s.draft)
        assertFalse(s.dirty)
        assertFalse(s.canSave)
    }

    @Test
    fun `taps insert at the cursor, delete removes the note before it`() = runTest {
        val vm = vm()
        vm.loaded()
        vm.tap(0)
        vm.tap(4)
        vm.rest()
        assertEquals(listOf(0, 4, null), vm.state.value.draft!!.notes)
        assertEquals(3, vm.state.value.cursor)
        vm.moveCursor(1)
        vm.tap(2)
        assertEquals(listOf(0, 2, 4, null), vm.state.value.draft!!.notes)
        assertEquals(2, vm.state.value.cursor)
        vm.deleteBack()
        vm.deleteBack()
        assertEquals(listOf(4, null), vm.state.value.draft!!.notes)
        assertEquals(0, vm.state.value.cursor)
        vm.deleteBack() // nothing before the cursor
        assertEquals(listOf(4, null), vm.state.value.draft!!.notes)
        vm.moveCursor(99)
        assertEquals(2, vm.state.value.cursor)
        vm.tap(25) // no fingering
        assertEquals(listOf(4, null), vm.state.value.draft!!.notes)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `saving needs a title and a note`() = runTest {
        val vm = vm()
        vm.loaded()
        vm.setTitle("Mine")
        assertFalse(vm.state.value.canSave)
        vm.rest()
        assertFalse(vm.state.value.canSave)
        vm.tap(7)
        assertTrue(vm.state.value.canSave)
        vm.setTitle(" ")
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `a new tune is saved as ABC, then an existing one in place`() = runTest {
        val vm = vm()
        vm.loaded()
        vm.setTitle("Mine")
        vm.tap(0)
        vm.tap(12)
        val id = vm.effects.let { effects ->
            var id = 0L
            effects.test {
                vm.save()
                id = (awaitItem() as TapEditorEffect.Saved).id
            }
            id
        }
        assertEquals(TapTune.toAbc(TapDraft("Mine", WhistleKey.C, listOf(0, 12))), graph.repository.get(id)!!.abc)
        assertFalse(vm.state.value.dirty)

        val again = vm(id)
        assertEquals(TapDraft("Mine", WhistleKey.C, listOf(0, 12)), again.loaded().draft)
        again.tap(7)
        again.effects.test {
            again.save()
            assertEquals(TapEditorEffect.Saved(id), awaitItem())
        }
        assertEquals(listOf(0, 12, 7), TapTune.parse(graph.repository.get(id)!!.abc)!!.notes)
        assertEquals(1, graph.repository.tunes.first().size)
    }

    @Test
    fun `changing the whistle keeps the fingerings and other headers`() = runTest {
        val id = runBlocking { graph.repository.save(tapped) }
        val vm = vm(id)
        vm.loaded()
        vm.setWhistle(WhistleKey.LOW_D)
        vm.effects.test {
            vm.save()
            awaitItem()
        }
        assertEquals(
            "X:1\nT:Tapped\nZ:Me\nM:4/4\nL:1/4\n%whistle_tab_key d low\nK:D\nD E F |]\n",
            graph.repository.get(id)!!.abc,
        )
    }

    @Test
    fun `the draft survives in the saved state`() = runTest {
        val saved = SavedStateHandle()
        vm(saved = saved).also { it.loaded() }.apply {
            setTitle("Half done")
            tap(5)
            tap(9)
            rest()
            moveCursor(1)
            setWhistle(WhistleKey.A)
        }
        val s = vm(saved = saved).loaded()
        assertEquals(TapDraft("Half done", WhistleKey.A, listOf(5, 9, null)), s.draft)
        assertEquals(1, s.cursor)
        assertTrue(s.dirty)
    }

    @Test
    fun `a tune changed as text, or gone, says so`() = runTest {
        val id = runBlocking { graph.repository.save("X:1\nT:Text\nK:D\nDEF|\n") }
        assertTrue(vm(id).state.first { it.unsupported }.unsupported)
        assertTrue(vm(999).state.first { it.missing }.missing)
    }

    @Test
    fun `the preview shows the tapped fingerings on the tune's whistle, whatever my settings`() = runTest {
        val page = FakeTunePage()
        // My whistle is C and I see other whistles' tunes with my fingerings; the editor ignores that.
        val vm = vm(settings = flowOf(TabSettings(whistle = WhistleKey.C, mode = TransposeMode.SAME_PITCHES)))
        vm.bindPage(page)
        vm.loaded()
        vm.setWhistle(WhistleKey.D)
        vm.tap(0)
        val render = page.sent.filterIsInstance<BridgeCommand.Render>().last()
        assertEquals(TapTune.toAbc(TapDraft("", WhistleKey.D, listOf(0))), render.abc)
        assertEquals(12, render.midiTranspose)
        page.emit(BridgeEvent.Rendered(listOf(NoteEvent(0, 0, 1, 62, 0, 0.0, 0.25, false)), emptyList()))
        val diagrams = page.sent.filterIsInstance<BridgeCommand.Diagrams>().last().diagrams
        assertEquals("CCCCCC", diagrams.single()!!.holes)
    }
}

package io.github.conorryan.whistletabber.ui.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.conorryan.whistletabber.render.FakeTunePage
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.ui.TestGraph
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class EditorViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var graph: TestGraph

    @Before
    fun setUp() {
        graph = TestGraph(tmp.root)
    }

    @After
    fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private val abc = "X:1\nT:Old\nK:D\nDEF|\n"

    private fun vm(id: Long?, saved: SavedStateHandle = SavedStateHandle()) =
        EditorViewModel(id, saved, graph.repository)

    /** A ViewModel with a fake page bound, as the screen would bind the WebView. */
    private fun bound(id: Long?): Pair<EditorViewModel, FakeTunePage> {
        val page = FakeTunePage()
        return vm(id).also { it.bindPage(page) } to page
    }

    @Test
    fun `the preview renders 400 ms after typing stops`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (vm, page) = bound(null)
        page.commands.test {
            runCurrent()
            advanceTimeBy(401)
            assertEquals(BridgeCommand.Render(EditorViewModel.TEMPLATE), awaitItem())
            vm.setText("X:1\nT:A\nK:D\nD|")
            advanceTimeBy(200)
            vm.setText("X:1\nT:AB\nK:D\nD|")
            advanceTimeBy(399)
            expectNoEvents()
            advanceTimeBy(2)
            assertEquals(BridgeCommand.Render("X:1\nT:AB\nK:D\nD|"), awaitItem())
        }
    }

    @Test
    fun `validation needs a title and a key`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(null)
        assertEquals(listOf(AbcProblem.NO_TITLE), vm.state.value.problems) // the template has no title
        assertFalse(vm.state.value.canSave)
        vm.setText("X:1\nT:\nR:reel\n")
        assertEquals(listOf(AbcProblem.NO_TITLE, AbcProblem.NO_KEY), vm.state.value.problems)
        vm.setText("X:1\nT:Fine\nK:G\n")
        assertEquals(emptyList<AbcProblem>(), vm.state.value.problems)
        assertTrue(vm.state.value.canSave)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `warnings come from the preview render`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val (vm, page) = bound(null)
        page.emit(BridgeEvent.Rendered(emptyList(), listOf("Unknown decoration")))
        assertEquals(listOf("Unknown decoration"), vm.renderState.value.warnings)
    }

    @Test
    fun `a new tune is saved and reported`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(null)
        vm.effects.test {
            vm.setText("X:1\nT:Brand New\nK:D\nDEF|")
            vm.save()
            val id = (awaitItem() as EditorEffect.Saved).id
            assertEquals("Brand New", graph.repository.get(id)!!.title)
        }
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun `an existing tune loads and saves in place`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val id = runBlocking { graph.repository.save(abc) }
        val vm = vm(id)
        assertEquals(abc, vm.state.first { it.text != null }.text)
        assertFalse(vm.state.value.dirty)
        vm.effects.test {
            vm.setText(abc.replace("T:Old", "T:New"))
            vm.save()
            assertEquals(EditorEffect.Saved(id), awaitItem())
        }
        assertEquals("New", graph.repository.get(id)!!.title)
        assertEquals(1, graph.repository.tunes.first().size)
    }

    @Test
    fun `the draft survives in the saved state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val id = runBlocking { graph.repository.save(abc) }
        val saved = SavedStateHandle()
        vm(id, saved).also { it.state.first { s -> s.text != null } }.setText("X:1\nT:Draft\nK:D\n")
        val restored = vm(id, saved)
        val state = restored.state.first { it.text != null }
        assertEquals("X:1\nT:Draft\nK:D\n", state.text)
        assertTrue(state.dirty)
    }

    @Test
    fun `saving is refused while there are problems`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(null)
        vm.effects.test {
            vm.save() // template: no title
            expectNoEvents()
        }
        assertEquals(0, graph.repository.tunes.first().size)
    }

    @Test
    fun `a missing tune says so`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        assertTrue(vm(999).state.first { it.missing }.missing)
    }
}

package io.github.conorryan.whistletabber.ui.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.TestGraph
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
import org.junit.Assert.assertFalse
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
    fun `a new tune can start from a given draft, unsaved`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val draft = "X:1\nT:From an image\nK:D\nD E F |]\n"
        val state = EditorViewModel(null, SavedStateHandle(), graph.repository, draft).state.first { it.text != null }
        assertEquals(draft, state.text)
        assertTrue(state.dirty)
        assertTrue(state.canSave)
    }

    @Test
    fun `an existing tune ignores a given draft`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val id = runBlocking { graph.repository.save(abc) }
        val state = EditorViewModel(id, SavedStateHandle(), graph.repository, "X:1\nT:Other\nK:D\n").state.first { it.text != null }
        assertEquals(abc, state.text)
    }

    @Test
    fun `a missing tune says so`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        assertTrue(vm(999).state.first { it.missing }.missing)
    }

    @Test
    fun `picking a whistle writes the directive into the text, auto removes it`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(null)
        vm.state.first { it.text != null }
        // The template has no directive, so its K:D suggests a D whistle.
        assertNull(vm.state.value.explicitWhistle)
        assertEquals(WhistleKey.D, vm.state.value.suggestedWhistle)
        assertEquals(WhistleKey.D, vm.state.value.whistle)
        vm.setWhistle(WhistleKey.C)
        val withDirective = vm.state.value.text!!
        assertEquals(WhistleKey.C, AbcBook.whistleOf(withDirective))
        assertEquals(WhistleKey.C, vm.state.value.explicitWhistle)
        assertEquals(WhistleKey.C, vm.state.value.whistle)
        assertTrue(vm.state.value.dirty)
        vm.setWhistle(null)
        val withoutDirective = vm.state.value.text!!
        assertNull(AbcBook.whistleOf(withoutDirective))
        assertNull(vm.state.value.explicitWhistle)
        assertEquals(WhistleKey.D, vm.state.value.whistle) // back to the key's suggestion
    }
}

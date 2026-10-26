package io.github.conorryan.whistletabber.ui.library

import app.cash.turbine.test
import io.github.conorryan.whistletabber.data.tunes.FakeDocumentsProvider
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.ui.TestGraph
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var graph: TestGraph

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, FakeDocumentsProvider.AUTHORITY)
        graph = TestGraph(tmp.root)
    }

    @After
    fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.vm() = LibraryViewModel(
        graph.repository, graph.importer, graph.files, graph.recentlyDeleted, backgroundScope,
    )

    private fun tune(title: String, rhythm: String = "reel") = "X:1\nT:$title\nR:$rhythm\nK:D\nDEF|\n"

    private fun save(vararg abc: String) = runBlocking { abc.forEach { graph.repository.save(it) } }

    @Test
    fun `an empty library says so once loaded`() = runTest {
        val vm = vm()
        val state = vm.state.first { it.loaded }
        assertTrue(state.libraryEmpty)
        assertEquals(emptyList<Any>(), state.tunes)
    }

    @Test
    fun `search and filters narrow the list`() = runTest {
        save(tune("The Kesh", "jig"), tune("Drowsy Maggie"), tune("Morrison's", "jig"))
        val vm = vm()
        vm.state.test {
            assertEquals(3, awaitUntil { it.loaded && it.tunes.size == 3 }.tunes.size)
            vm.setQuery("kesh")
            assertEquals(listOf("The Kesh"), awaitUntil { it.tunes.size == 1 }.tunes.map { it.title })
            vm.setQuery("")
            vm.toggleRhythm("jig")
            val jigs = awaitUntil { it.filters.rhythm == "jig" && it.tunes.size == 2 }
            assertEquals(listOf("The Kesh", "Morrison's"), jigs.tunes.map { it.title })
            assertEquals(listOf("jig", "reel"), jigs.rhythms)
            vm.toggleRhythm("jig") // again: off
            assertNull(awaitUntil { it.filters.rhythm == null && it.tunes.size == 3 }.filters.rhythm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starring a tune and filtering by star`() = runTest {
        save(tune("A"), tune("B"))
        val vm = vm()
        val a = vm.state.first { it.tunes.size == 2 }.tunes.first { it.title == "A" }
        vm.setStarred(a, true)
        vm.toggleStarredOnly()
        val starred = vm.state.first { it.filters.starredOnly && it.tunes.size == 1 }
        assertEquals("A", starred.tunes.single().title)
        assertFalse(starred.libraryEmpty)
        vm.clearFilters()
        assertEquals(2, vm.state.first { !it.filters.active && it.tunes.size == 2 }.tunes.size)
    }

    @Test
    fun `adding the starter set reports what was added`() = runTest {
        val vm = vm()
        vm.messages.test {
            vm.addStarterSet()
            assertEquals(LibraryMessage.Imported(ImportResult(10, 0, emptyList())), awaitItem())
        }
        assertEquals(10, vm.state.first { it.tunes.size == 10 }.tunes.size)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `importing files reports the importer's result`() = runTest {
        File(graph.context.cacheDir, "a.abc").writeText(tune("One"))
        val vm = vm()
        vm.messages.test {
            vm.importFiles(listOf(FakeDocumentsProvider.uri("a.abc")))
            assertEquals(LibraryMessage.Imported(ImportResult(1, 0, emptyList())), awaitItem())
        }
    }

    @Test
    fun `export writes the whole library`() = runTest {
        save(tune("One"), tune("Two"))
        File(graph.context.cacheDir, "out.abc").writeText("")
        val vm = vm()
        vm.messages.test {
            vm.exportAll(FakeDocumentsProvider.uri("out.abc"))
            assertEquals(LibraryMessage.Exported(2), awaitItem())
        }
        val text = File(graph.context.cacheDir, "out.abc").readText()
        assertEquals(listOf("X:1", "X:2"), text.lines().filter { it.startsWith("X:") })
    }

    @Test
    fun `a deletion elsewhere is offered for undo, and undo restores it`() = runTest {
        save(tune("Gone"))
        val vm = vm()
        val id = vm.state.first { it.tunes.size == 1 }.tunes.single().id
        vm.messages.test {
            graph.recentlyDeleted.delete(id)
            val deleted = awaitItem() as LibraryMessage.Deleted
            assertEquals(listOf("Gone"), deleted.tunes.map { it.title })
            vm.undoDelete(deleted.tunes)
        }
        assertEquals(id, vm.state.first { it.tunes.size == 1 }.tunes.single().id)
        assertNull(graph.recentlyDeleted.offer.value)
    }

    @Test
    fun `selection toggles, selects all shown, and clears`() = runTest {
        save(tune("A"), tune("B"), tune("C", "jig"))
        val vm = vm()
        val ids = vm.state.first { it.tunes.size == 3 }.tunes.associate { it.title to it.id }
        assertFalse(vm.state.value.selecting)
        vm.toggleSelected(ids.getValue("A"))
        vm.toggleSelected(ids.getValue("B"))
        vm.toggleSelected(ids.getValue("A"))
        assertEquals(setOf(ids.getValue("B")), vm.state.first { it.selected.size == 1 }.selected)
        assertTrue(vm.state.value.selecting)

        vm.toggleRhythm("reel")
        vm.selectAll()
        assertEquals(setOf(ids.getValue("A"), ids.getValue("B")), vm.state.first { it.selected.size == 2 }.selected)
        vm.clearSelection()
        assertFalse(vm.state.first { it.selected.isEmpty() }.selecting)
    }

    @Test
    fun `tunes hidden by a filter drop out of the selection`() = runTest {
        save(tune("Reel"), tune("Jig", "jig"))
        val vm = vm()
        vm.state.first { it.tunes.size == 2 }
        vm.selectAll()
        assertEquals(2, vm.state.first { it.selected.size == 2 }.selected.size)
        vm.toggleRhythm("jig")
        val jigs = vm.state.first { it.tunes.size == 1 }
        assertEquals(setOf(jigs.tunes.single().id), jigs.selected)
    }

    @Test
    fun `deleting the selection offers the batch for undo, and undo restores it`() = runTest {
        save(tune("A"), tune("B"), tune("Kept"))
        val vm = vm()
        val ids = vm.state.first { it.tunes.size == 3 }.tunes.associate { it.title to it.id }
        vm.toggleSelected(ids.getValue("A"))
        vm.toggleSelected(ids.getValue("B"))
        vm.messages.test {
            vm.deleteSelected()
            val deleted = awaitItem() as LibraryMessage.Deleted
            assertEquals(setOf("A", "B"), deleted.tunes.map { it.title }.toSet())
            val left = vm.state.first { it.tunes.size == 1 }
            assertEquals("Kept", left.tunes.single().title)
            assertFalse(left.selecting)
            vm.undoDelete(deleted.tunes)
        }
        assertEquals(ids.values.toSet(), vm.state.first { it.tunes.size == 3 }.tunes.map { it.id }.toSet())
        assertNull(graph.recentlyDeleted.offer.value)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<LibraryUiState>.awaitUntil(
        predicate: (LibraryUiState) -> Boolean,
    ): LibraryUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }
}

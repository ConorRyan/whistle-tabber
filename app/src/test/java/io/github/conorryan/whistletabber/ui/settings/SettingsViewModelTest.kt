package io.github.conorryan.whistletabber.ui.settings

import app.cash.turbine.test
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.TestGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class SettingsViewModelTest {

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

    @Test
    fun `writes persist to the store`() = runTest {
        val editor = TabSettingsEditor(graph.store, backgroundScope)
        val vm = SettingsViewModel(editor, graph.starterTunes, backgroundScope)
        assertEquals(editor, vm.settingsEditor)
        editor.setWhistle(WhistleKey.LOW_D)
        editor.setCNatural(CNatural.OXX_XOX)
        editor.setFoldOutOfRange(false)
        editor.setMode(TransposeMode.SAME_FINGERINGS)
        val expected = TabSettings(WhistleKey.LOW_D, CNatural.OXX_XOX, false, TransposeMode.SAME_FINGERINGS)
        assertEquals(expected, graph.store.settings.first { it == expected })
    }

    @Test
    fun `adding the starter set twice skips the second time`() = runTest {
        val vm = SettingsViewModel(TabSettingsEditor(graph.store, backgroundScope), graph.starterTunes, backgroundScope)
        vm.starterAdded.test {
            vm.addStarterSet()
            assertEquals(ImportResult(50, 0, emptyList()), awaitItem())
            vm.addStarterSet()
            assertEquals(ImportResult(0, 50, emptyList()), awaitItem())
        }
        assertEquals(false, vm.addingStarter.value)
    }
}

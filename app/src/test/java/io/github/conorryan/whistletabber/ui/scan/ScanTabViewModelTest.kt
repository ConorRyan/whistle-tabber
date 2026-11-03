package io.github.conorryan.whistletabber.ui.scan

import android.net.Uri
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.scan.GrayImage
import io.github.conorryan.whistletabber.scan.TabPainter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanTabViewModelTest {

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val uri: Uri = Uri.parse("content://images/1")
    private val otherUri: Uri = Uri.parse("content://images/2")

    /** D, G, then an unknown fingering. */
    private val chart: GrayImage = TabPainter(1100, 160).apply {
        listOf("XXX XXX", "XXX OOO", "OXO XOX").forEachIndexed { i, n -> column(40.0 + i * 50, 10.0, n, 8.0) }
    }.image()

    /** F, a fingering on its own image. */
    private val otherChart: GrayImage = TabPainter(1100, 160).apply {
        column(40.0, 10.0, "XXX XOO", 8.0)
    }.image()

    private fun vm(
        settings: TabSettings = TabSettings(),
        load: suspend (Uri) -> GrayImage? = { chart },
    ) = ScanTabViewModel(load, flowOf(settings), "Tune from an image", Dispatchers.Main)

    @Test
    fun `starts with my whistle and the default title, nothing read`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val s = vm(TabSettings(whistle = WhistleKey.LOW_D, fingerings = FingeringChoices.DEFAULT.with(10, 1))).state.value
        assertEquals(WhistleKey.LOW_D, s.whistle)
        assertEquals(FingeringChoices.DEFAULT.with(10, 1), s.fingerings)
        assertEquals("Tune from an image", s.title)
        assertTrue(s.images.isEmpty())
        assertFalse(s.canOpen)
        assertNull(s.abc())
    }

    @Test
    fun `a picked image is read into ABC`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.add(listOf(uri))
        val s = vm.state.value
        assertFalse(s.busy)
        assertEquals(3, s.found)
        assertEquals(1, s.unknown)
        assertTrue(s.canOpen)
        assertEquals(
            "X:1\nT:Tune from an image\nM:none\nL:1/4\n%whistle_tab_key d\nK:D\nD G \"^?\"z |]\n",
            s.abc(),
        )
    }

    @Test
    fun `a later image appends its notes in order`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { if (it == otherUri) otherChart else chart })
        vm.add(listOf(uri))
        vm.add(listOf(otherUri))
        val s = vm.state.value
        assertEquals(2, s.images.size)
        assertEquals(chart, s.images[0].image)
        assertEquals(otherChart, s.images[1].image)
        assertEquals(4, s.found)
        val abc = s.abc()!!
        assertTrue(abc, abc.endsWith("D G \"^?\"z |\nF |]\n"))
    }

    @Test
    fun `several images picked at once append in order`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { if (it == otherUri) otherChart else chart })
        vm.add(listOf(uri, otherUri))
        val s = vm.state.value
        assertEquals(2, s.images.size)
        assertEquals(4, s.found)
        val abc = s.abc()!!
        assertTrue(abc, abc.endsWith("D G \"^?\"z |\nF |]\n"))
    }

    @Test
    fun `whistle and title change the ABC, a blank title can't open`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.add(listOf(uri))
        vm.setWhistle(WhistleKey.C)
        vm.setTitle("  My Tune ")
        val abc = vm.state.value.abc()!!
        assertTrue(abc, "T:My Tune\n" in abc && "K:C\nC F" in abc && "%whistle_tab_key c\n" in abc)
        vm.setTitle(" ")
        assertFalse(vm.state.value.canOpen)
    }

    @Test
    fun `busy while reading, then the image and reading appear`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate = CompletableDeferred<GrayImage?>()
        val vm = vm(load = { gate.await() })
        vm.add(listOf(uri))
        assertTrue(vm.state.value.busy)
        assertFalse(vm.state.value.canOpen)
        gate.complete(chart)
        assertFalse(vm.state.value.busy)
        assertEquals(chart, vm.state.value.images.single().image)
    }

    @Test
    fun `an image that can't be opened says so`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { null })
        vm.add(listOf(uri))
        assertTrue(vm.state.value.unreadable)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.images.isEmpty())
    }

    @Test
    fun `a batch with one unreadable image appends the readable one`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { if (it == otherUri) null else chart })
        vm.add(listOf(uri, otherUri))
        val s = vm.state.value
        assertTrue(s.unreadable)
        assertEquals(1, s.images.size)
        assertEquals(3, s.found)
    }

    @Test
    fun `an image without tab reads as nothing found`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { GrayImage(100, 50, IntArray(5000) { 255 }) })
        vm.add(listOf(uri))
        assertEquals(0, vm.state.value.found)
        assertFalse(vm.state.value.canOpen)
        assertNull(vm.state.value.abc())
    }

    @Test
    fun `start over empties the images`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.add(listOf(uri))
        vm.startOver()
        val s = vm.state.value
        assertFalse(s.busy)
        assertTrue(s.images.isEmpty())
        assertNull(s.abc())
    }

    @Test
    fun `a paste without an image says so, and adding clears it`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.pasteFailed()
        assertTrue(vm.state.value.pasteEmpty)
        vm.startOver()
        assertFalse(vm.state.value.pasteEmpty)
        vm.pasteFailed()
        vm.add(listOf(uri))
        val s = vm.state.value
        assertFalse(s.pasteEmpty)
        assertEquals(3, s.found)
    }
}

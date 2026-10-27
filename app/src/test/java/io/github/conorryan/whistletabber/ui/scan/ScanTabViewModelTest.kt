package io.github.conorryan.whistletabber.ui.scan

import android.net.Uri
import io.github.conorryan.whistletabber.music.CNatural
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

    /** D, G, then an unknown fingering. */
    private val chart: GrayImage = TabPainter(1100, 160).apply {
        listOf("XXX XXX", "XXX OOO", "OXO XOX").forEachIndexed { i, n -> column(40.0 + i * 50, 10.0, n, 8.0) }
    }.image()

    private fun vm(
        settings: TabSettings = TabSettings(),
        load: suspend (Uri) -> GrayImage? = { chart },
    ) = ScanTabViewModel(load, flowOf(settings), "Tune from an image", Dispatchers.Main)

    @Test
    fun `starts with my whistle and the default title, nothing read`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val s = vm(TabSettings(whistle = WhistleKey.LOW_D, cNatural = CNatural.HALF_TOP)).state.value
        assertEquals(WhistleKey.LOW_D, s.whistle)
        assertEquals(CNatural.HALF_TOP, s.cNatural)
        assertEquals("Tune from an image", s.title)
        assertNull(s.reading)
        assertFalse(s.canOpen)
        assertNull(s.abc())
    }

    @Test
    fun `a picked image is read into ABC`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.pick(uri)
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
    fun `whistle and title change the ABC, a blank title can't open`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm()
        vm.pick(uri)
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
        vm.pick(uri)
        assertTrue(vm.state.value.busy)
        assertFalse(vm.state.value.canOpen)
        gate.complete(chart)
        assertFalse(vm.state.value.busy)
        assertEquals(chart, vm.state.value.image)
    }

    @Test
    fun `an image that can't be opened says so`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { null })
        vm.pick(uri)
        assertTrue(vm.state.value.unreadable)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.image)
    }

    @Test
    fun `an image without tab reads as nothing found`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = vm(load = { GrayImage(100, 50, IntArray(5000) { 255 }) })
        vm.pick(uri)
        assertEquals(0, vm.state.value.found)
        assertFalse(vm.state.value.canOpen)
        assertNull(vm.state.value.abc())
    }
}

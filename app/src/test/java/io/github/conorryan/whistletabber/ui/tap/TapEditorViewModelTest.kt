package io.github.conorryan.whistletabber.ui.tap

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.Press
import io.github.conorryan.whistletabber.music.RhythmFit
import io.github.conorryan.whistletabber.music.RhythmSetup
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.TestGraph
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
    ) = TapEditorViewModel(id, saved, graph.repository, settings)

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

    private val reel = RhythmSetup(bpm = 120, meter = Meter.FOUR_FOUR)

    /** A loaded editor with notes [offsets] and the rhythm tapper open at [reel]. */
    private suspend fun tapper(vararg offsets: Int): TapEditorViewModel {
        val vm = vm()
        vm.loaded()
        vm.setTitle("Timed")
        offsets.forEach { vm.tap(it) }
        vm.openRhythm()
        vm.setRhythmSetup(reel)
        vm.startTapping()
        return vm
    }

    private fun TapEditorViewModel.hold(down: Long, up: Long): Int? = press(down).also { release(up) }

    @Test
    fun `tapping every note gives the tune a rhythm, and saving writes it`() = runTest {
        val vm = tapper(0, 2, 4)
        val untimed = vm.state.value.draft!!
        assertEquals(RhythmPhase.TAPPING, vm.state.value.rhythm!!.phase)
        assertEquals(0, vm.hold(0, 900))
        assertEquals(1, vm.state.value.rhythm!!.next)
        assertEquals(2, vm.hold(1000, 1400))
        assertEquals(4, vm.hold(1500, 1900))
        val s = vm.state.value
        assertEquals(RhythmPhase.DONE, s.rhythm!!.phase)
        assertTrue(s.timed)
        assertTrue(s.dirty)

        val presses = listOf(Press(0, 900), Press(1000, 1400), Press(1500, 1900))
        val expected = TapTune.toAbc(untimed.withRhythm(RhythmFit.fit(presses, 3, reel), reel))
        assertTrue(expected, "C4 D2 E2 |]" in expected)
        assertEquals(s.draft, TapTune.parse(expected))

        vm.keepRhythm()
        assertNull(vm.state.value.rhythm)
        val id = vm.effects.let { effects ->
            var id = 0L
            effects.test {
                vm.save()
                id = (awaitItem() as TapEditorEffect.Saved).id
            }
            id
        }
        val abc = graph.repository.get(id)!!.abc
        assertEquals(expected, abc)
        // A saved timed tune stays tap-editable: it reads back exactly.
        assertEquals(vm.state.value.draft, TapTune.parse(abc))
        assertFalse(vm.state.value.dirty)
        vm.setTitle("Timed again")
        assertTrue(vm.state.value.timed) // a title isn't a change of notes
    }

    @Test
    fun `tapping in a timed tune inserts a note as long as the one before it`() = runTest {
        val vm = tapper(0, 2)
        vm.hold(0, 400)
        vm.hold(500, 900)
        vm.keepRhythm()
        assertTrue(vm.state.value.timed)
        val before = vm.state.value.draft!!
        val beforeTiming = before.timing!!
        vm.moveCursor(1)
        vm.tap(7)
        val draft = vm.state.value.draft!!
        val timing = draft.timing!!
        assertEquals(before.notes.toMutableList().apply { add(1, 7) }, draft.notes)
        assertEquals(beforeTiming.lengths.toMutableList().apply { add(1, beforeTiming.lengths[0]) }, timing.lengths)
        assertEquals(beforeTiming.setup, timing.setup)
    }

    @Test
    fun `deleting a note in a timed tune takes its length with it`() = runTest {
        val vm = tapper(0, 2, 4)
        vm.hold(0, 400)
        vm.hold(500, 900)
        vm.hold(1000, 1400)
        vm.keepRhythm()
        val before = vm.state.value.draft!!
        val beforeTiming = before.timing!!
        vm.moveCursor(2)
        vm.deleteBack()
        val draft = vm.state.value.draft!!
        assertEquals(before.notes.toMutableList().apply { removeAt(1) }, draft.notes)
        assertEquals(beforeTiming.lengths.toMutableList().apply { removeAt(1) }, draft.timing!!.lengths)
    }

    @Test
    fun `removing the rhythm makes every note a quarter again`() = runTest {
        val vm = tapper(0, 2)
        vm.hold(0, 400)
        vm.hold(500, 900)
        vm.keepRhythm()
        assertTrue(vm.state.value.timed)
        vm.removeRhythm()
        val s = vm.state.value
        val draft = s.draft!!
        assertFalse(s.timed)
        assertNull(draft.timing)
        assertTrue(s.dirty)
    }

    @Test
    fun `cancel and try again restore the draft from before the tapper opened`() = runTest {
        val vm = tapper(0, 2)
        vm.hold(0, 400)
        vm.hold(500, 900)
        vm.keepRhythm()
        val timed = vm.state.value.draft!!

        vm.openRhythm() // tap the rhythm again
        vm.startTapping()
        vm.hold(0, 2000) // a different rhythm
        vm.closeRhythm() // cancel
        assertEquals(timed, vm.state.value.draft)

        vm.openRhythm()
        vm.startTapping()
        vm.hold(0, 2000)
        vm.stopTapping() // finishes with the new rhythm
        assertEquals(RhythmPhase.DONE, vm.state.value.rhythm!!.phase)
        assertTrue(vm.state.value.draft != timed)
        vm.retryRhythm() // try again
        assertEquals(timed, vm.state.value.draft)
        assertEquals(RhythmPhase.SETUP, vm.state.value.rhythm!!.phase)
    }

    @Test
    fun `the rhythm survives in the saved state`() = runTest {
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.loaded()
        vm.setTitle("Timed")
        vm.tap(0)
        vm.tap(2)
        vm.openRhythm()
        vm.setRhythmSetup(reel)
        vm.startTapping()
        vm.hold(0, 400)
        vm.hold(500, 900)
        vm.keepRhythm()
        val expected = vm.state.value.draft!!
        assertTrue(expected.timing != null)

        val again = vm(saved = saved)
        val s = again.loaded()
        assertEquals(expected, s.draft)
        assertTrue(s.timed)
        assertTrue(s.dirty)
    }

    @Test
    fun `stopping early times the rest a beat each, with nothing tapped it's back to the setup`() = runTest {
        val vm = tapper(0, 2, 4)
        vm.stopTapping()
        assertEquals(RhythmPhase.SETUP, vm.state.value.rhythm!!.phase)
        vm.startTapping()
        vm.hold(0, 2000)
        vm.stopTapping()
        assertEquals(RhythmPhase.DONE, vm.state.value.rhythm!!.phase)
        assertTrue(TapTune.toAbc(vm.state.value.draft!!).contains("C8 | D2 E2 |]"))
    }

    @Test
    fun `a press while one is held ends it there, and only while tapping`() = runTest {
        val vm = tapper(0, 2, 4)
        assertEquals(0, vm.press(0))
        assertEquals(2, vm.press(500)) // the next finger landed first
        vm.release(600) // the first finger's lift isn't sent, this is the second's
        assertEquals(2, vm.state.value.rhythm!!.next)
        assertEquals(4, vm.press(1000))
        vm.release(1400)
        assertEquals(RhythmPhase.DONE, vm.state.value.rhythm!!.phase)
        assertTrue(TapTune.toAbc(vm.state.value.draft!!).contains("C2 D2 E2 |]"))
        vm.retryRhythm()
        assertEquals(RhythmPhase.SETUP, vm.state.value.rhythm!!.phase)
        assertNull(vm.press(0))
    }

    @Test
    fun `closing the tapper leaves the tune as it was`() = runTest {
        val vm = tapper(0, 2)
        vm.hold(0, 400)
        vm.hold(500, 900)
        assertTrue(vm.state.value.timed)
        vm.closeRhythm()
        val s = vm.state.value
        assertNull(s.rhythm)
        assertFalse(s.timed)
    }

    @Test
    fun `the tapper needs notes, and remembers its setup`() = runTest {
        val saved = SavedStateHandle()
        val empty = vm(saved = saved)
        empty.loaded()
        empty.openRhythm()
        assertNull(empty.state.value.rhythm)
        empty.rest()
        empty.openRhythm()
        assertNull(empty.state.value.rhythm) // a rest has nothing to tap
        empty.tap(0)
        empty.openRhythm()
        empty.setRhythmSetup(RhythmSetup(bpm = 999, meter = Meter.SIX_EIGHT))
        assertEquals(RhythmSetup(bpm = RhythmSetup.MAX_BPM, meter = Meter.SIX_EIGHT), empty.state.value.rhythm!!.setup)
        assertEquals(1, empty.state.value.rhythm!!.total)

        val again = vm(saved = saved)
        again.loaded()
        again.openRhythm()
        assertEquals(RhythmSetup(bpm = RhythmSetup.MAX_BPM, meter = Meter.SIX_EIGHT), again.state.value.rhythm!!.setup)
    }

    @Test
    fun `the pad sound is part of the setup and remembered`() = runTest {
        val saved = SavedStateHandle()
        val vm = vm(saved = saved)
        vm.loaded()
        vm.tap(0)
        vm.openRhythm()
        assertEquals(PadSound.NOTE, vm.state.value.rhythm!!.padSound)
        vm.setPadSound(PadSound.TICK)
        assertEquals(PadSound.TICK, vm.state.value.rhythm!!.padSound)
        vm.startTapping()
        vm.setPadSound(PadSound.SILENT) // not while tapping
        assertEquals(PadSound.TICK, vm.state.value.rhythm!!.padSound)

        val again = vm(saved = saved)
        again.loaded()
        again.openRhythm()
        assertEquals(PadSound.TICK, again.state.value.rhythm!!.padSound)
    }
}

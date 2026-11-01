package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import io.github.conorryan.whistletabber.audio.OutputPath
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.audio.TapAudio
import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.RhythmSetup
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTiming
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TapEditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val empty = TapEditorUiState(draft = TapDraft("", WhistleKey.D, emptyList()))

    @Test
    fun `an empty tune explains what to do and can't be saved`() {
        compose.setContent { WhistleTabberTheme { TapEditorContent(empty, isNew = true) } }
        compose.onNodeWithTag("tapEmpty").assertExists()
        compose.onNodeWithTag("save").assertIsNotEnabled()
        compose.onNodeWithTag("delete").assertIsNotEnabled()
    }

    @Test
    fun `the palette names its notes and taps their offsets`() {
        val taps = mutableListOf<Int>()
        compose.setContent { WhistleTabberTheme { TapEditorContent(empty, isNew = true, onTap = { taps += it }) } }
        compose.onNodeWithText("F♯").performClick()
        compose.onNode(hasContentDescription("OXX OOO")).performClick() // C natural
        compose.onNodeWithTag("key-1").assertDoesNotExist()
        compose.onNodeWithTag("more").performClick()
        compose.onNodeWithTag("key-1").performClick()
        assertEquals(listOf(4, 10, 1), taps)
    }

    @Test
    fun `the octave chips move the palette up`() {
        var state by mutableStateOf(empty)
        val taps = mutableListOf<Int>()
        compose.setContent {
            WhistleTabberTheme {
                TapEditorContent(state, isNew = true, onTap = { taps += it }, onOctave = { state = state.copy(octave = it) })
            }
        }
        compose.onNodeWithTag("octave-1").performClick()
        compose.onNodeWithText("D+").performClick()
        compose.onNodeWithTag("octave-2").performClick()
        compose.onNodeWithTag("key-24").performClick()
        compose.onNodeWithTag("key-36").assertDoesNotExist() // C♯ has no third-octave fingering
        assertEquals(listOf(12, 24), taps)
    }

    @Test
    fun `the strip shows each note and moves the cursor when one is tapped`() {
        val cursors = mutableListOf<Int>()
        val state = TapEditorUiState(draft = TapDraft("T", WhistleKey.D, listOf(0, null, 12)), cursor = 3)
        var deleted = false
        compose.setContent {
            WhistleTabberTheme { TapEditorContent(state, isNew = false, onCursor = { cursors += it }, onDelete = { deleted = true }) }
        }
        compose.onNodeWithTag("note-1").assertExists()
        compose.onNode(hasContentDescription("Rest")).assertExists()
        compose.onNodeWithTag("note-0").performScrollTo().performClick()
        compose.onNodeWithTag("left").performClick()
        compose.onNodeWithTag("right").assertIsNotEnabled()
        compose.onNodeWithTag("delete").performClick()
        compose.onNodeWithTag("save").assertIsEnabled()
        assertEquals(listOf(1, 2), cursors)
        assertTrue(deleted)
    }

    @Test
    fun `leaving with changes asks first`() {
        var closed = false
        compose.setContent {
            WhistleTabberTheme { TapEditorContent(empty.copy(dirty = true), isNew = true, onClose = { closed = true }) }
        }
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.onNodeWithTag("discard").performClick()
        assertTrue(closed)
    }

    @Test
    fun `a tune changed as text says so`() {
        compose.setContent { WhistleTabberTheme { TapEditorContent(TapEditorUiState(unsupported = true), isNew = false) } }
        compose.onNodeWithTag("unsupported").assertExists()
    }

    @Test
    fun `the whistle chip opens the written-for picker`() {
        var picked: WhistleKey? = null
        compose.setContent {
            WhistleTabberTheme { TapEditorContent(empty, isNew = true, onWhistle = { picked = it }) }
        }
        compose.onNodeWithTag("whistleChip").performScrollTo().performClick()
        compose.onNodeWithTag("whistle-C").performClick()
        assertEquals(WhistleKey.C, picked)
    }

    private val notes = TapEditorUiState(draft = TapDraft("T", WhistleKey.D, listOf(0, null, 2)), cursor = 3)

    @Test
    fun `the rhythm tapper replaces the palette and sets up the tapping`() {
        var state by mutableStateOf(notes)
        val setups = mutableListOf<RhythmSetup>()
        val pads = mutableListOf<PadSound>()
        var started = false
        compose.setContent {
            WhistleTabberTheme {
                TapEditorContent(
                    state,
                    isNew = false,
                    onOpenRhythm = { state = state.copy(rhythm = RhythmPanel(RhythmSetup(), total = 2)) },
                    onRhythmSetup = { setups += it },
                    onPadSound = { pads += it },
                    onStartTapping = { started = true },
                )
            }
        }
        compose.onNodeWithTag("rhythmPanel").assertDoesNotExist()
        compose.onNodeWithTag("openRhythm").performScrollTo().performClick()
        // The setup opens in a bottom sheet; the editor, palette included, stays underneath.
        compose.onNodeWithTag("rhythmPanel").assertExists()
        compose.onNodeWithTag("rest").assertExists()
        compose.onNodeWithTag("openRhythm").assertIsNotEnabled()
        compose.onNodeWithText("Tempo: 100 beats a minute").assertExists()
        compose.onNodeWithTag("meter-6/8").performScrollTo().performClick()
        compose.onNodeWithTag("pad-TICK").performScrollTo().performClick()
        compose.onNodeWithTag("rhythmStart").performScrollTo().performClick()
        assertEquals(listOf(RhythmSetup(meter = Meter.SIX_EIGHT)), setups)
        assertEquals(listOf(PadSound.TICK), pads)
        assertTrue(started)
    }

    @Test
    fun `the pad reports presses and releases and the strip shows the next note`() {
        val events = mutableListOf<String>()
        val tapping = notes.copy(rhythm = RhythmPanel(RhythmSetup(), phase = RhythmPhase.TAPPING, next = 1, total = 2))
        compose.setContent {
            WhistleTabberTheme {
                TapEditorContent(
                    tapping,
                    isNew = false,
                    beat = -3,
                    latencyMs = 42,
                    audioInfo = TapAudio.Info(bufferFrames = 192, burstFrames = 96, rate = 48_000, path = OutputPath.AAUDIO_EXCLUSIVE, underruns = 0),
                    onPress = { events += "down" }, onRelease = { events += "up" })
            }
        }
        compose.onNodeWithText("Count-in: 2").assertExists()
        compose.onNodeWithText("Note 2 of 2").assertExists()
        compose.onNodeWithText("Audio delay: about 42 ms").assertExists()
        compose.onNodeWithText("Buffer 192 frames (bursts of 96) at 48000 Hz, AAudio, exclusive, 0 underruns").assertExists()
        compose.onNodeWithTag("note-2").assertIsSelected() // the second sounding note
        compose.onNodeWithTag("note-0").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithTag("rhythmPad").performTouchInput {
            down(center)
            up()
        }
        assertEquals(listOf("down", "up"), events)
        // The next finger lands before the last lifts: a press, and the earlier lift is ignored.
        events.clear()
        compose.onNodeWithTag("rhythmPad").performTouchInput {
            down(0, center)
            down(1, centerLeft)
            up(0)
            up(1)
        }
        assertEquals(listOf("down", "down", "up"), events)
    }

    @Test
    fun `a tapped rhythm can be kept or tapped again`() {
        var kept = false
        var retried = false
        val timed = TapDraft("T", WhistleKey.D, listOf(0, null, 2), timing = TapTiming(RhythmSetup(), listOf(2, 4, 2), 8))
        val done = TapEditorUiState(draft = timed, cursor = 3, rhythm = RhythmPanel(RhythmSetup(), phase = RhythmPhase.DONE, next = 2, total = 2))
        compose.setContent {
            WhistleTabberTheme { TapEditorContent(done, isNew = false, onKeepRhythm = { kept = true }, onRetryRhythm = { retried = true }) }
        }
        compose.onNodeWithTag("rhythmRetry").performClick()
        compose.onNodeWithTag("rhythmKeep").performClick()
        assertTrue(kept && retried)
    }

    @Test
    fun `a timed strip shows bar gaps from the lengths, a mark per note, and can drop the rhythm`() {
        val timing = TapTiming(RhythmSetup(bpm = 120, meter = Meter.FOUR_FOUR), listOf(2, 2, 2, 2, 2), 8)
        val state = TapEditorUiState(draft = TapDraft("T", WhistleKey.D, listOf(0, 2, 4, 5, 7), timing = timing), cursor = 5)
        var removed = false
        compose.setContent {
            WhistleTabberTheme { TapEditorContent(state, isNew = false, onRemoveRhythm = { removed = true }) }
        }
        compose.onNodeWithTag("barGap-0").assertDoesNotExist()
        compose.onNodeWithTag("barGap-4").performScrollTo().assertExists()
        compose.onNodeWithTag("length-4", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("removeRhythm").performScrollTo().performClick()
        assertTrue(removed)
    }
}

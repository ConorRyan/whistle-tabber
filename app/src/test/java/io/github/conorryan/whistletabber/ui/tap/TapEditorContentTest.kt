package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.conorryan.whistletabber.music.TapDraft
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
}

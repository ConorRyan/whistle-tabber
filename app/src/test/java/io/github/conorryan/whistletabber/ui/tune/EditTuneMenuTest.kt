package io.github.conorryan.whistletabber.ui.tune

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditTuneMenuTest {

    @get:Rule
    val compose = createComposeRule()

    /** The dial with its own open state, as TuneScreen shows it. */
    private fun setContent(tapEditable: Boolean, onEdit: () -> Unit = {}, onEditTaps: () -> Unit = {}) {
        compose.setContent {
            WhistleTabberTheme {
                var open by remember { mutableStateOf(false) }
                EditTuneMenu(open = open, onOpen = { open = it }, tapEditable = tapEditable, onEdit = onEdit, onEditTaps = onEditTaps)
            }
        }
    }

    @Test
    fun `a tap-editable tune expands into both edits and each closes it`() {
        var edits = 0
        var taps = 0
        setContent(tapEditable = true, onEdit = { edits++ }, onEditTaps = { taps++ })
        compose.onNodeWithTag("editTaps").assertDoesNotExist()
        compose.onNodeWithTag("edit").performClick()
        compose.onNodeWithTag("editTaps").assertIsDisplayed()
        compose.onNodeWithTag("editAbc").assertIsDisplayed()

        compose.onNodeWithTag("editTaps").performClick()
        assertEquals(1, taps)
        compose.onNodeWithTag("editTaps").assertDoesNotExist()

        compose.onNodeWithTag("edit").performClick()
        compose.onNodeWithTag("editAbc").performClick()
        assertEquals(1, edits)
        compose.onNodeWithTag("editAbc").assertDoesNotExist()
    }

    @Test
    fun `a tune that is not tap-editable goes straight to the ABC editor`() {
        var edits = 0
        var taps = 0
        setContent(tapEditable = false, onEdit = { edits++ }, onEditTaps = { taps++ })
        compose.onNodeWithTag("editTaps").assertDoesNotExist()
        compose.onNodeWithTag("edit").performClick()
        assertEquals(1, edits)
        assertEquals(0, taps)
        compose.onNodeWithTag("editAbc").assertDoesNotExist()
    }
}

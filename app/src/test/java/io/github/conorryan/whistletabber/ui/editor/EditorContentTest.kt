package io.github.conorryan.whistletabber.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `problems are listed and block saving`() {
        val text = "X:1\nT:\nR:reel\n"
        compose.setContent {
            WhistleTabberTheme {
                EditorContent(EditorUiState(text = text, problems = EditorViewModel.problemsOf(text)), isNew = true)
            }
        }
        compose.onNodeWithText("Add a title line, e.g. T:The Kesh").assertIsDisplayed()
        compose.onNodeWithText("Add a key line, e.g. K:G").assertIsDisplayed()
        compose.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun `a valid tune can be saved and shows the page's warnings`() {
        var saved = false
        compose.setContent {
            WhistleTabberTheme {
                EditorContent(
                    EditorUiState(text = "X:1\nT:A\nK:D\n", warnings = listOf("Unknown decoration")),
                    isNew = false,
                    onSave = { saved = true },
                )
            }
        }
        compose.onNodeWithText("Edit tune").assertIsDisplayed()
        compose.onNodeWithText("Unknown decoration").assertIsDisplayed()
        compose.onNodeWithTag("save").assertIsEnabled().performClick()
        assertTrue(saved)
    }

    @Test
    fun `leaving with changes asks first`() {
        var closed = false
        compose.setContent {
            WhistleTabberTheme {
                EditorContent(EditorUiState(text = "X:1\nT:A\nK:D\n", dirty = true), isNew = false, onClose = { closed = true })
            }
        }
        compose.onNodeWithText("Edit tune").assertIsDisplayed()
        compose.onNodeWithTag("discard").assertDoesNotExist()
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.onNodeWithTag("discard").performClick()
        assertTrue(closed)
    }
}

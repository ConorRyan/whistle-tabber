package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.components.resolvedNoteNames
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FingeringPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides resolvedNoteNames(NoteNames.LETTERS)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        FingeringChoicesGrid(WhistleKey.D, FingeringChoices.DEFAULT) { _, _ -> }
                    }
                }
            }
        }
    }

    @Test
    fun `a note whose only fingering is a fife one opens its sheet and says so`() {
        show()
        val fife = "* " + ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.fingering_fife)
        // The legend is under the grid even before anything is opened.
        assertTrue(compose.onAllNodesWithText(fife).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("fingering-25").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-25-0").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(fife).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun `a single non-fife fingering stays plain`() {
        show()
        // The third-octave D has one ordinary fingering: no dot, no sheet.
        compose.onNodeWithTag("fingeringDot-24").assertDoesNotExist()
        compose.onNodeWithTag("fingering-24-0").assertDoesNotExist()
    }
}

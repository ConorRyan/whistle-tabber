package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabSettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(noteNames: NoteNames, onNoteNames: (NoteNames) -> Unit = {}) {
        val settings = TabSettings(noteNames = noteNames)
        compose.setContent {
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides settings.noteNames) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TabSettingsContent(settings, {}, {}, {}, {}, onNoteNames)
                    }
                }
            }
        }
    }

    @Test
    fun `whistle chips use letters by default`() {
        show(NoteNames.LETTERS)
        compose.onNodeWithTag("whistle-HIGH_G").assertTextEquals("High G")
        compose.onNodeWithTag("whistle-E_FLAT").assertTextEquals("Eb")
        compose.onNodeWithTag("whistle-LOW_D").assertTextEquals("Low D")
        compose.onNodeWithTag("notes-LETTERS").performScrollTo().assertIsSelected()
    }

    @Test
    fun `solfege follows the setting, not the language`() {
        show(NoteNames.SOLFEGE)
        compose.onNodeWithTag("whistle-LOW_D").assertTextEquals("Ré grave")
        compose.onNodeWithTag("whistle-B_FLAT").assertTextEquals("Si♭")
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a French phone still shows letters when letters are chosen`() {
        show(NoteNames.LETTERS)
        compose.onNodeWithTag("whistle-LOW_D").assertTextEquals("Low D")
        compose.onNodeWithTag("whistle-HIGH_G").assertTextEquals("High G")
    }

    @Test
    fun `picking a style reports it`() {
        var picked: NoteNames? = null
        show(NoteNames.LETTERS) { picked = it }
        compose.onNodeWithTag("notes-SOLFEGE").performScrollTo().performClick()
        assertEquals(NoteNames.SOLFEGE, picked)
    }
}

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
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.components.resolvedNoteNames
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

    private fun show(
        noteNames: NoteNames,
        onNoteNames: (NoteNames) -> Unit = {},
        settings: TabSettings = TabSettings(noteNames = noteNames),
        onFingering: (Int, Int) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides resolvedNoteNames(settings.noteNames)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TabSettingsContent(settings, {}, onFingering, {}, {}, onNoteNames)
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
    fun `auto gives letters in English`() {
        show(NoteNames.AUTO)
        compose.onNodeWithTag("whistle-LOW_D").assertTextEquals("Low D")
        compose.onNodeWithTag("notes-AUTO").performScrollTo().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `auto gives solfege in French`() {
        show(NoteNames.AUTO)
        compose.onNodeWithTag("whistle-LOW_D").assertTextEquals("Ré grave")
        compose.onNodeWithTag("whistle-E_FLAT").assertTextEquals("Mi♭")
    }

    @Test
    fun `picking a style reports it`() {
        var picked: NoteNames? = null
        show(NoteNames.LETTERS, onNoteNames = { picked = it })
        compose.onNodeWithTag("notes-SOLFEGE").performScrollTo().performClick()
        assertEquals(NoteNames.SOLFEGE, picked)
    }

    @Test
    fun `fingerings are folded away until the heading is tapped`() {
        show(NoteNames.LETTERS)
        compose.onNodeWithTag("fingering-0").assertDoesNotExist()
        compose.onNodeWithTag("fingeringsToggle").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-0").assertExists()
        compose.onNodeWithTag("fingeringsToggle").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-0").assertDoesNotExist()
    }

    private fun expandFingerings() {
        compose.onNodeWithTag("fingeringsToggle").performScrollTo().performClick()
    }

    @Test
    fun `every note shows, named in my whistle's key, and a dot marks the choosable ones`() {
        show(NoteNames.LETTERS)
        expandFingerings()
        compose.onNodeWithTag("fingering-0").performScrollTo().assertTextEquals("D")
        compose.onNodeWithTag("fingering-12").performScrollTo().assertTextEquals("D+")
        compose.onNodeWithTag("fingering-34").performScrollTo().assertTextEquals("C++")
        compose.onNodeWithTag("fingeringDot-12", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("fingeringDot-0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping a note offers its fingerings and reports the pick`() {
        var picked: Pair<Int, Int>? = null
        show(NoteNames.LETTERS, onFingering = { o, i -> picked = o to i })
        expandFingerings()
        compose.onNodeWithTag("fingering-12").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-12-0").assertIsSelected()
        compose.onNodeWithTag("fingering-12-1").performClick()
        assertEquals(12 to 1, picked)
    }

    @Test
    fun `the chosen fingering is the one selected`() {
        show(NoteNames.LETTERS, settings = TabSettings(fingerings = FingeringChoices.DEFAULT.with(12, 1)))
        expandFingerings()
        compose.onNodeWithTag("fingering-12").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-12-1").assertIsSelected()
    }

    @Test
    fun `a note with one fingering opens nothing`() {
        show(NoteNames.LETTERS)
        expandFingerings()
        compose.onNodeWithTag("fingering-0").performScrollTo().performClick()
        compose.onNodeWithTag("fingering-0-0").assertDoesNotExist()
    }
}

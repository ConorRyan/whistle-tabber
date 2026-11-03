package io.github.conorryan.whistletabber.ui.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tune(id: Long, title: String, rhythm: String? = "reel", starred: Boolean = false) =
        Tune(id, "X:1", title, emptyList(), rhythm, "D", "4/4", WhistleKey.D, starred, "user", 0, 0)

    @Test
    fun `rows show title, rhythm, key and whistle`() {
        val state = LibraryUiState(
            tunes = listOf(tune(1, "Drowsy Maggie"), tune(2, "The Kesh", "jig", starred = true)),
            rhythms = listOf("jig", "reel"),
            loaded = true,
        )
        var opened: Long? = null
        var starred: Pair<Long, Boolean>? = null
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(state, onOpenTune = { opened = it }, onStar = { t, s -> starred = t.id to s })
            }
        }
        compose.onNodeWithText("Drowsy Maggie").assertIsDisplayed()
        // The rhythm is a badge of its own; key and whistle share a line.
        compose.onNodeWithTag("tune-1").assert(hasText("Reel") and hasText("D · D whistle"))
        compose.onNodeWithTag("tune-2").assert(hasText("Jig") and hasText("D · D whistle"))
        compose.onNodeWithTag("chip-jig").assertIsDisplayed()

        compose.onNodeWithTag("tune-2").performClick()
        assertEquals(2L, opened)
        // Scroll the row clear of the New tune button, as a user would.
        compose.onNodeWithTag("tuneList").performScrollToIndex(1)
        compose.onNodeWithTag("star-2").performClick()
        assertEquals(2L to false, starred) // already starred: unstar
    }

    @Test
    fun `rows name the whistle in the chosen note names`() {
        val state = LibraryUiState(tunes = listOf(tune(1, "Drowsy Maggie")), loaded = true)
        compose.setContent {
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides NoteNames.SOLFEGE) {
                    LibraryContent(state, onOpenTune = {}, onStar = { _, _ -> })
                }
            }
        }
        compose.onNodeWithText("D · Ré whistle").assertIsDisplayed()
    }

    @Test
    fun `an empty library offers the starter set`() {
        var added = false
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(LibraryUiState(loaded = true, libraryEmpty = true), onAddStarterSet = { added = true })
            }
        }
        compose.onNodeWithTag("emptyLibrary").assertIsDisplayed()
        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
        compose.onNodeWithTag("addStarterSet").performClick()
        assertTrue(added)
    }

    @Test
    fun `find tunes is in the add menu and on the empty library`() {
        var found = 0
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(LibraryUiState(loaded = true, libraryEmpty = true), onFindTunes = { found++ })
            }
        }
        compose.onNodeWithTag("findTunes").performClick()
        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addFindTunes").performClick()
        assertEquals(2, found)
    }

    @Test
    fun `the add menu expands and its actions close it and navigate`() {
        var wrote = 0
        var taps = 0
        var scans = 0
        var imports = 0
        var pastes = 0
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(
                    LibraryUiState(tunes = listOf(tune(1, "A")), loaded = true),
                    onNewTune = { wrote++ },
                    onWriteTaps = { taps++ },
                    onScanTab = { scans++ },
                    onImport = { imports++ },
                    onPaste = { pastes++ },
                )
            }
        }
        compose.onNodeWithTag("addWriteText").assertDoesNotExist()
        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addWriteText").assertIsDisplayed()
        compose.onNodeWithTag("addWriteTaps").performClick()
        assertEquals(1, taps)
        compose.onNodeWithTag("addWriteText").assertDoesNotExist()

        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addWriteText").performClick()
        assertEquals(1, wrote)

        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addScanTab").performClick()
        assertEquals(1, scans)

        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addImport").performClick()
        assertEquals(1, imports)

        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addPaste").performClick()
        assertEquals(1, pastes)
    }

    @Test
    fun `a tap on the scrim closes the add menu`() {
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(LibraryUiState(tunes = listOf(tune(1, "A")), loaded = true))
            }
        }
        compose.onNodeWithTag("newTune").performClick()
        compose.onNodeWithTag("addWriteText").assertIsDisplayed()
        compose.onNodeWithTag("addScrim").performClick()
        compose.onNodeWithTag("addWriteText").assertDoesNotExist()
    }

    @Test
    fun `export is a button in the top bar`() {
        var exported = false
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(LibraryUiState(tunes = listOf(tune(1, "A")), loaded = true), onExport = { exported = true })
            }
        }
        compose.onNodeWithTag("export").performClick()
        assertTrue(exported)
    }

    @Test
    fun `no matches offers to clear the filters`() {
        var cleared = false
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(
                    LibraryUiState(filters = LibraryFilters(query = "zzz"), loaded = true),
                    onClearFilters = { cleared = true },
                )
            }
        }
        compose.onNodeWithTag("noMatches").assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `nothing is shown before the first load`() {
        compose.setContent { WhistleTabberTheme { LibraryContent(LibraryUiState()) } }
        compose.onNodeWithTag("emptyLibrary").assertDoesNotExist()
        compose.onNodeWithTag("tuneList").assertDoesNotExist()
    }

    @Test
    fun `a long press selects a tune`() {
        var toggled: Long? = null
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(
                    LibraryUiState(tunes = listOf(tune(1, "A"), tune(2, "B")), loaded = true),
                    onToggleSelected = { toggled = it },
                )
            }
        }
        compose.onNodeWithTag("selectedCount").assertDoesNotExist()
        compose.onNodeWithTag("tune-2").performTouchInput { longClick() }
        assertEquals(2L, toggled)
    }

    @Test
    fun `while selecting, the bar acts on the selection and a tap toggles instead of opening`() {
        var opened: Long? = null
        var toggled: Long? = null
        var selectedAll = false
        var deleted = false
        var exported = false
        var cleared = false
        compose.setContent {
            WhistleTabberTheme {
                LibraryContent(
                    LibraryUiState(tunes = listOf(tune(1, "A"), tune(2, "B"), tune(3, "C")), loaded = true, selected = setOf(1, 3)),
                    onOpenTune = { opened = it },
                    onToggleSelected = { toggled = it },
                    onSelectAll = { selectedAll = true },
                    onDeleteSelected = { deleted = true },
                    onExportSelected = { exported = true },
                    onClearSelection = { cleared = true },
                )
            }
        }
        compose.onNodeWithTag("selectedCount").assertTextEquals("2 selected")
        compose.onNodeWithTag("newTune").assertDoesNotExist()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.onNodeWithTag("star-1").assertDoesNotExist()
        compose.onNodeWithTag("select-1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("tune-1").assertIsOn()
        compose.onNodeWithTag("tune-2").assertIsOff()

        compose.onNodeWithTag("tune-2").performClick()
        assertEquals(2L, toggled)
        assertNull(opened)

        compose.onNodeWithTag("selectAll").performClick()
        assertTrue(selectedAll)
        compose.onNodeWithTag("exportSelected").performClick()
        assertTrue(exported)
        compose.onNodeWithTag("deleteSelected").performClick()
        assertTrue(deleted)
        compose.onNodeWithTag("clearSelection").performClick()
        assertTrue(cleared)
    }
}

package io.github.conorryan.whistletabber.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.conorryan.whistletabber.data.tunes.Tune
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
        compose.onNodeWithText("Reel · D · D whistle").assertIsDisplayed()
        compose.onNodeWithText("Jig · D · D whistle").assertIsDisplayed()
        compose.onNodeWithTag("chip-jig").assertIsDisplayed()

        compose.onNodeWithTag("tune-2").performClick()
        assertEquals(2L, opened)
        compose.onNodeWithTag("star-2").performClick()
        assertEquals(2L to false, starred) // already starred: unstar
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
}

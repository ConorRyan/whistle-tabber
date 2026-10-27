package io.github.conorryan.whistletabber.ui.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.scan.Box
import io.github.conorryan.whistletabber.scan.GrayImage
import io.github.conorryan.whistletabber.scan.TabColumn
import io.github.conorryan.whistletabber.scan.TabReading
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
class ScanTabContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun column(notation: String) = Fingering.parse(notation).let { TabColumn(it.holes, it.octave, Box(1, 1, 5, 30)) }

    private val image = GrayImage(40, 40, IntArray(1600) { 255 })

    private val read = ScanTabUiState(
        image = image,
        reading = TabReading(listOf(listOf(column("XXX XXX"), column("XXX OOO"), column("OXO XOX")))),
        title = "Tune from an image",
    )

    @Test
    fun `before an image, explains and offers to choose one`() {
        var chose = false
        compose.setContent { WhistleTabberTheme { ScanTabContent(ScanTabUiState(), onChoose = { chose = true }) } }
        compose.onNodeWithText("Only the fingering diagrams are read", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Choose image").performClick()
        assertTrue(chose)
    }

    @Test
    fun `a reading shows the count, the unknown diagrams and the image`() {
        compose.setContent { WhistleTabberTheme { ScanTabContent(read) } }
        compose.onNodeWithText("3 notes found").assertIsDisplayed()
        compose.onNodeWithText("1 diagram not recognised, marked ? in the tune").assertIsDisplayed()
        compose.onNodeWithText("Choose another image").assertIsDisplayed()
        compose.onNodeWithTag("image").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `title, whistle and open reach their callbacks`() {
        var title = ""
        var whistle: WhistleKey? = null
        var opened = false
        compose.setContent {
            WhistleTabberTheme {
                ScanTabContent(read, onTitle = { title = it }, onWhistle = { whistle = it }, onOpen = { opened = true })
            }
        }
        compose.onNodeWithTag("title").performTextReplacement("The Kesh")
        compose.onNodeWithTag("scanWhistle-${WhistleKey.C.name}").performScrollTo().performClick()
        compose.onNodeWithTag("open").performScrollTo().assertIsEnabled().performClick()
        assertEquals("The Kesh", title)
        assertEquals(WhistleKey.C, whistle)
        assertTrue(opened)
    }

    @Test
    fun `open waits for a title`() {
        compose.setContent { WhistleTabberTheme { ScanTabContent(read.copy(title = "")) } }
        compose.onNodeWithTag("open").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `while reading, choosing waits`() {
        compose.setContent { WhistleTabberTheme { ScanTabContent(ScanTabUiState(busy = true)) } }
        compose.onNodeWithText("Reading the image…").assertIsDisplayed()
        compose.onNodeWithTag("choose").assertIsNotEnabled()
    }

    @Test
    fun `an image that can't be opened`() {
        compose.setContent { WhistleTabberTheme { ScanTabContent(ScanTabUiState(unreadable = true)) } }
        compose.onNodeWithTag("unreadable").assertIsDisplayed()
    }

    @Test
    fun `an image without tab`() {
        compose.setContent { WhistleTabberTheme { ScanTabContent(ScanTabUiState(image = image, reading = TabReading(emptyList()))) } }
        compose.onNodeWithTag("none").assertIsDisplayed()
        compose.onNodeWithText("Only the fingering diagrams are read", substring = true).assertIsDisplayed()
    }

    @Test
    fun `back goes back`() {
        var back = false
        compose.setContent { WhistleTabberTheme { ScanTabContent(ScanTabUiState(), onBack = { back = true }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}

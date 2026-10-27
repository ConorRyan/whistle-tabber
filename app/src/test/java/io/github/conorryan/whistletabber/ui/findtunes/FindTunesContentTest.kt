package io.github.conorryan.whistletabber.ui.findtunes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FindTunesContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every site is listed and opens its address`() {
        val opened = mutableListOf<String>()
        compose.setContent { WhistleTabberTheme { FindTunesContent(onOpen = { opened += it }) } }
        compose.onNodeWithText("O'Neill's Project").assertIsDisplayed()
        compose.onNodeWithText("abcnotation.com", substring = false).assertIsDisplayed()
        TUNE_SITES.indices.forEach { compose.onNodeWithTag("site-$it").performScrollTo().performClick() }
        assertEquals(TUNE_SITES.map { it.url }, opened)
    }

    @Test
    fun `back goes back`() {
        var back = false
        compose.setContent { WhistleTabberTheme { FindTunesContent(onBack = { back = true }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun `sites are https, never TheSession`() {
        for (site in TUNE_SITES) {
            assertTrue(site.url, site.url.startsWith("https://"))
            assertFalse(site.url, "thesession" in site.url.lowercase())
        }
    }

    @Test
    fun `host drops the scheme, www and path`() {
        assertEquals("norbeck.nu", TUNE_SITES.single { "norbeck" in it.url }.host)
        assertEquals("abcnotation.com", TUNE_SITES.single { "abcnotation" in it.url }.host)
    }
}

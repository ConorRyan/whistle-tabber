package io.github.conorryan.whistletabber.ui.tune

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp") // tall enough for the 11-whistle drop-down
class TuneHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private val tune = Tune(
        id = 1,
        abc = "X:1\nT:Test Tune\nK:D\nGAc|\n",
        title = "Test Tune",
        alternateTitles = emptyList(),
        rhythm = "reel",
        key = "D",
        meter = "4/4",
        whistle = WhistleKey.D,
        starred = false,
        source = "",
        createdAt = 0,
        updatedAt = 0,
    )

    private fun setContent(
        shownWhistle: WhistleKey = WhistleKey.D,
        settingsWhistle: WhistleKey = WhistleKey.D,
        onShowOn: (WhistleKey) -> Unit = {},
    ) {
        compose.setContent {
            WhistleTabberTheme {
                TuneHeader(
                    tune = tune,
                    tuneWhistle = WhistleKey.D,
                    shownWhistle = shownWhistle,
                    settingsWhistle = settingsWhistle,
                    onShowOn = onShowOn,
                )
            }
        }
    }

    @Test
    fun `the key chip is only a label`() {
        setContent()
        compose.onNodeWithTag("keyChip")
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }

    @Test
    fun `the chips say what the tune is written for and shown on`() {
        setContent(shownWhistle = WhistleKey.C, settingsWhistle = WhistleKey.D)
        compose.onNodeWithText("Written for D").assertIsDisplayed()
        compose.onNodeWithText("On C whistle").assertIsDisplayed()
    }

    @Test
    fun `the whistle menu lists every whistle, marks Settings' own and picks`() {
        var picked: WhistleKey? = null
        setContent(onShowOn = { picked = it })
        compose.onNodeWithTag("onWhistleChip").performClick()
        compose.onNodeWithTag("onWhistle-D").assertIsDisplayed()
        compose.onNodeWithText("D (Settings)").assertIsDisplayed()
        compose.onNodeWithTag("onWhistle-HIGH_G").assertIsDisplayed()
        compose.onNodeWithTag("onWhistle-LOW_C").assertIsDisplayed()
        WhistleKey.entries.forEach { whistle ->
            compose.onNodeWithTag("onWhistle-${whistle.name}").assertIsDisplayed()
        }
        compose.onNodeWithTag("onWhistle-LOW_G").performClick()
        assertEquals(WhistleKey.LOW_G, picked)
        compose.onNodeWithTag("onWhistle-LOW_G").assertDoesNotExist()
    }
}

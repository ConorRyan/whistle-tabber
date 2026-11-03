package io.github.conorryan.whistletabber.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The navigation of saving from the tap editor ([onTapEditorSaved]): a
 * converted copy saves as a new tune, which is opened; the same tune goes
 * back to where it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavHostTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `saving a converted copy opens the new tune`() {
        val nav = show()
        compose.runOnUiThread {
            nav.navigate(TuneRoute(1))
            nav.navigate(TapEditorRoute(1))
            onTapEditorSaved(nav, id = 1, savedId = 42)
        }
        assertEquals(TuneRoute(42), nav.currentBackStackEntry!!.toRoute<TuneRoute>())
        // The tap editor is gone: back from the copy is the original tune.
        compose.runOnUiThread { nav.popBackStack() }
        assertEquals(TuneRoute(1), nav.currentBackStackEntry!!.toRoute<TuneRoute>())
        compose.runOnUiThread { nav.popBackStack() }
        assertEquals(LibraryRoute, nav.currentBackStackEntry!!.toRoute<LibraryRoute>())
    }

    @Test
    fun `saving in place goes back to the tune`() {
        val nav = show()
        compose.runOnUiThread {
            nav.navigate(TuneRoute(1))
            nav.navigate(TapEditorRoute(1))
            onTapEditorSaved(nav, id = 1, savedId = 1)
        }
        assertEquals(TuneRoute(1), nav.currentBackStackEntry!!.toRoute<TuneRoute>())
    }

    @Test
    fun `saving a new tune opens it`() {
        val nav = show()
        compose.runOnUiThread {
            nav.navigate(TapEditorRoute())
            onTapEditorSaved(nav, id = null, savedId = 7)
        }
        assertEquals(TuneRoute(7), nav.currentBackStackEntry!!.toRoute<TuneRoute>())
        compose.runOnUiThread { nav.popBackStack() }
        assertEquals(LibraryRoute, nav.currentBackStackEntry!!.toRoute<LibraryRoute>())
    }

    /** AppNavHost's graph with blank screens: only the back stack matters here. */
    private fun show(): NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = LibraryRoute) {
                composable<LibraryRoute> { Text("library") }
                composable<TuneRoute> { Text("tune") }
                composable<TapEditorRoute> { Text("tap") }
            }
        }
        compose.waitForIdle()
        return nav
    }
}

package io.github.conorryan.whistletabber

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.data.tunes.SharedTunes
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.ui.AppNavHost
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.components.resolvedNoteNames
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity would see the same intent again.
        if (savedInstanceState == null) receive(intent)
        setContent {
            val noteNamesFlow = remember { graph.tabSettingsStore.settings.map { it.noteNames } }
            val noteNames by noteNamesFlow.collectAsStateWithLifecycle(NoteNames.DEFAULT)
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides resolvedNoteNames(noteNames)) {
                    AppNavHost(graph)
                }
            }
        }
    }

    /** singleTask: files shared while the app is open arrive here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private val graph: AppGraph get() = (application as WhistleTabberApplication).graph

    private fun receive(intent: Intent) {
        SharedTunes.from(intent)?.let(graph.incomingTunes::receive)
    }
}

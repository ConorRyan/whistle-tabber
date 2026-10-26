package io.github.conorryan.whistletabber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.ui.AppNavHost
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as WhistleTabberApplication).graph
        setContent {
            val noteNamesFlow = remember { graph.tabSettingsStore.settings.map { it.noteNames } }
            val noteNames by noteNamesFlow.collectAsStateWithLifecycle(NoteNames.DEFAULT)
            WhistleTabberTheme {
                CompositionLocalProvider(LocalNoteNames provides noteNames) {
                    AppNavHost(graph)
                }
            }
        }
    }
}

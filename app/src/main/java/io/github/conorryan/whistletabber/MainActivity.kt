package io.github.conorryan.whistletabber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.conorryan.whistletabber.ui.AppNavHost
import io.github.conorryan.whistletabber.ui.theme.WhistleTabberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as WhistleTabberApplication).graph
        setContent {
            WhistleTabberTheme {
                AppNavHost(graph)
            }
        }
    }
}

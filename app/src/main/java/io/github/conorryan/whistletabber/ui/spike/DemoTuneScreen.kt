package io.github.conorryan.whistletabber.ui.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.conorryan.whistletabber.AppGraph
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost
import kotlin.math.roundToInt

/**
 * Demo screen: renders and plays one bundled O'Neill's tune with fingering
 * diagrams. Replaced by the library and tune screens in Session 3.
 */
@Composable
fun DemoTuneScreen(graph: AppGraph) {
    val context = LocalContext.current
    val abc = remember { context.assets.open("tunes/ballyhooley.abc").bufferedReader().use { it.readText() } }
    val vm: DemoTuneViewModel = viewModel(
        factory = viewModelFactory { initializer { DemoTuneViewModel(graph.tabSettingsStore, abc) } },
    )
    val controller = remember { TuneWebController(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tempo by remember { mutableFloatStateOf(100f) }
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
    LaunchedEffect(controller) {
        vm.commands.collect(controller::send)
    }
    LaunchedEffect(controller) {
        controller.events.collect(vm::onEvent)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TuneWebViewHost(controller, Modifier.fillMaxWidth().weight(1f).testTag("tuneWebView"))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${whistleLabel(state.settings.whistle)} · ${state.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).testTag("status"),
                )
                IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("openSettings")) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.settings_title))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::play) { Text(stringResource(R.string.play)) }
                OutlinedButton(onClick = vm::pause) { Text(stringResource(R.string.pause)) }
                OutlinedButton(onClick = vm::restart) { Text(stringResource(R.string.restart)) }
            }
            Text(stringResource(R.string.tempo_percent, tempo.roundToInt()))
            Slider(
                value = tempo,
                onValueChange = { tempo = it },
                onValueChangeFinished = { vm.setTempo(tempo.roundToInt()) },
                valueRange = 40f..150f,
            )
        }
    }

    if (showSettings) {
        TabSettingsSheet(
            settings = state.settings,
            onWhistle = vm::setWhistle,
            onCNatural = vm::setCNatural,
            onFold = vm::setFoldOutOfRange,
            onMode = vm::setMode,
            onDismiss = { showSettings = false },
        )
    }
}

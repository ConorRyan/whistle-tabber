package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.ui.library.importSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adding by vm.addingStarter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    LaunchedEffect(vm) {
        vm.starterAdded.collect { snackbar.showSnackbar(importSummary(context.resources, it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TabSettingsContent(
                settings = settings,
                onWhistle = vm.settingsEditor::setWhistle,
                onCNatural = vm.settingsEditor::setCNatural,
                onFold = vm.settingsEditor::setFoldOutOfRange,
                onMode = vm.settingsEditor::setMode,
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_library), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.starter_set), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.starter_set_help), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = vm::addStarterSet, enabled = !adding, modifier = Modifier.testTag("addStarterSet")) {
                Text(stringResource(R.string.add_starter_set))
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.licences), style = MaterialTheme.typography.titleMedium)
            for (res in listOf(R.string.licence_abcjs, R.string.licence_oneills, R.string.licence_wfg, R.string.licence_libraries)) {
                Text(stringResource(res), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

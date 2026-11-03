package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.ui.components.CardDivider
import io.github.conorryan.whistletabber.ui.components.GroupLabel
import io.github.conorryan.whistletabber.ui.components.ScreenTitle
import io.github.conorryan.whistletabber.ui.components.SectionCard
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
                // The title is the big one under the bar.
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenTitle(stringResource(R.string.settings_screen_title), Modifier.padding(bottom = 12.dp))
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.settings_title))
                SectionCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(16.dp)) {
                        TabSettingsContent(
                            settings = settings,
                            onWhistle = vm.settingsEditor::setWhistle,
                            onFingering = vm.settingsEditor::setFingering,
                            onFold = vm.settingsEditor::setFoldOutOfRange,
                            onMode = vm.settingsEditor::setMode,
                            onNoteNames = vm.settingsEditor::setNoteNames,
                        )
                    }
                }

                GroupLabel(stringResource(R.string.settings_library), Modifier.padding(top = 12.dp))
                SectionCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.starter_set), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.starter_set_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = vm::addStarterSet, enabled = !adding, shape = RoundedCornerShape(50), modifier = Modifier.testTag("addStarterSet")) {
                            Text(stringResource(R.string.add_starter_set))
                        }
                    }
                }

                GroupLabel(stringResource(R.string.settings_about), Modifier.padding(top = 12.dp))
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.about_app), style = MaterialTheme.typography.bodyMedium)
                    }
                    CardDivider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.licences), style = MaterialTheme.typography.titleMedium)
                        for (res in listOf(R.string.licence_abcjs, R.string.licence_oneills, R.string.licence_wfg, R.string.licence_libraries)) {
                            Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

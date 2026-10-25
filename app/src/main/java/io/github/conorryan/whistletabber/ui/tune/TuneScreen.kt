package io.github.conorryan.whistletabber.ui.tune

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.data.tunes.ExportNames
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.KeyChange
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.whistleLabel
import io.github.conorryan.whistletabber.ui.settings.TabSettingsContent
import io.github.conorryan.whistletabber.ui.settings.WhistleChips
import io.github.conorryan.whistletabber.render.TuneRenderer
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneScreen(
    vm: TuneViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTune: (Long) -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val renderState by vm.renderState.collectAsStateWithLifecycle()
    val controller = remember { TuneWebController(context) }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var whistleSheet by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.export(uri)
    }

    DisposableEffect(controller) {
        vm.bindPage(controller)
        onDispose { controller.destroy() }
    }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is TuneEffect.OpenTune -> onOpenTune(effect.id)
                TuneEffect.Close -> onBack()
                TuneEffect.Exported -> snackbar.showSnackbar(context.getString(R.string.tune_exported))
                TuneEffect.ExportFailed -> snackbar.showSnackbar(context.getString(R.string.export_failed))
            }
        }
    }

    val tune = state.tune
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tune?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (tune != null) {
                        IconButton(onClick = { vm.setStarred(!tune.starred) }, modifier = Modifier.testTag("star")) {
                            Icon(
                                if (tune.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(if (tune.starred) R.string.unstar else R.string.star),
                            )
                        }
                        IconButton(onClick = { whistleSheet = true }, modifier = Modifier.testTag("openWhistle")) {
                            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.settings_title))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("menu")) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_edit)) },
                                    onClick = { menuOpen = false; onEdit() },
                                    modifier = Modifier.testTag("menuEdit"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_change_key)) },
                                    onClick = { menuOpen = false; keyDialog = true },
                                    enabled = KeyChange.steps(tune.key, "C") != null,
                                    modifier = Modifier.testTag("menuKey"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_export_tune)) },
                                    onClick = { menuOpen = false; exportLauncher.launch(ExportNames.tune(tune.title)) },
                                    modifier = Modifier.testTag("menuExport"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = { menuOpen = false; vm.delete() },
                                    modifier = Modifier.testTag("menuDelete"),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.missing) {
                Text(stringResource(R.string.tune_missing), modifier = Modifier.padding(16.dp).testTag("missing"))
                return@Column
            }
            state.preview?.let { preview ->
                PreviewBar(
                    preview = preview,
                    onCopy = vm::savePreviewAsCopy,
                    onReplace = vm::replaceWithPreview,
                    onCancel = vm::cancelPreview,
                )
            }
            TuneWebViewHost(controller, Modifier.fillMaxWidth().weight(1f).testTag("tuneWebView"))
            PlaybackBar(
                state = renderState,
                onTogglePlay = vm::togglePlay,
                onRestart = vm::restart,
                onTempo = vm::setTempo,
                onDismissError = vm::dismissError,
            )
        }
    }

    if (whistleSheet && tune != null) {
        ModalBottomSheet(onDismissRequest = { whistleSheet = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WrittenForSection(tune, vm::setTuneWhistle)
                HorizontalDivider()
                TabSettingsContent(
                    settings = state.settings,
                    onWhistle = vm::setMyWhistle,
                    onCNatural = vm::setCNatural,
                    onFold = vm::setFoldOutOfRange,
                    onMode = vm::setMode,
                )
            }
        }
    }

    if (keyDialog && tune != null) {
        KeyDialog(
            tune = tune,
            onPick = { keyDialog = false; vm.previewKey(it) },
            onDismiss = { keyDialog = false },
        )
    }
}

/** The tune's own whistle: an explicit directive, or "from the key". */
@Composable
private fun WrittenForSection(tune: Tune, onSelect: (WhistleKey?) -> Unit) {
    val explicit = AbcBook.whistleOf(tune.abc)
    val suggested = AbcBook.suggestWhistle(tune.key) ?: WhistleKey.DEFAULT
    Text(stringResource(R.string.written_for), style = MaterialTheme.typography.titleMedium)
    FilterChip(
        selected = explicit == null,
        onClick = { onSelect(null) },
        label = { Text(stringResource(R.string.written_for_auto, whistleLabel(suggested))) },
        modifier = Modifier.testTag("tuneWhistle-auto"),
    )
    WhistleChips(explicit, onSelect = { onSelect(it) }, tagPrefix = "tuneWhistle")
    Text(stringResource(R.string.written_for_help), style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyDialog(tune: Tune, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val current = tune.key?.let(AbcBook::parseKey)?.first
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_change_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.key_change_help), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyChange.TONICS.forEach { tonic ->
                        FilterChip(
                            selected = tonic == current,
                            onClick = { onPick(tonic) },
                            label = { Text(KeyChange.keyName(tune.key, tonic)) },
                            modifier = Modifier.testTag("key-$tonic"),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PreviewBar(preview: KeyPreview, onCopy: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("preview")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.preview_in, preview.keyName), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onCopy, enabled = preview.abc != null, modifier = Modifier.testTag("saveCopy")) {
                    Text(stringResource(R.string.save_copy))
                }
                OutlinedButton(onClick = onReplace, enabled = preview.abc != null, modifier = Modifier.testTag("replace")) {
                    Text(stringResource(R.string.replace))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun PlaybackBar(
    state: TuneRenderer.RendererState,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onTempo: (Int) -> Unit,
    onDismissError: () -> Unit,
) {
    var tempo by remember(state.tempo) { mutableFloatStateOf(state.tempo.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(onClick = onTogglePlay, modifier = Modifier.testTag("play")) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
                )
            }
            IconButton(onClick = onRestart, modifier = Modifier.testTag("restart")) {
                Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.restart))
            }
            val status = when {
                state.error != null -> stringResource(R.string.page_error, state.error)
                state.bar != null -> stringResource(R.string.bar_n, state.bar + 1)
                state.warnings.isNotEmpty() -> pluralStringResource(R.plurals.warnings_count, state.warnings.size, state.warnings.size)
                else -> ""
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("status"),
            )
            if (state.error != null) {
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.cancel)) }
            }
        }
        Text(stringResource(R.string.tempo_percent, tempo.roundToInt()), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = tempo,
            onValueChange = { tempo = it },
            onValueChangeFinished = { onTempo(tempo.roundToInt()) },
            valueRange = 40f..150f,
            modifier = Modifier.testTag("tempo"),
        )
    }
}

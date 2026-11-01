package io.github.conorryan.whistletabber.ui.tune

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.data.tunes.ExportNames
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.KeyChange
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.TuneRenderer
import io.github.conorryan.whistletabber.ui.components.HeaderChip
import io.github.conorryan.whistletabber.ui.components.ScreenTitle
import io.github.conorryan.whistletabber.ui.components.whistleLabel
import io.github.conorryan.whistletabber.ui.settings.WrittenForSection
import io.github.conorryan.whistletabber.ui.theme.LocalWhistleColors
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuneScreen(
    vm: TuneViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEditTaps: () -> Unit,
    onOpenTune: (Long) -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val renderState by vm.renderState.collectAsStateWithLifecycle()
    val controller = remember { TuneWebController(context) }
    val snackbar = remember { SnackbarHostState() }
    var writtenForSheet by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

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
                // The title is the big one under the bar.
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                                tint = if (tune.starred) LocalWhistleColors.current.star else LocalContentColor.current,
                            )
                        }
                        IconButton(onClick = { exportLauncher.launch(ExportNames.tune(tune.title)) }, modifier = Modifier.testTag("export")) {
                            Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.action_export_tune))
                        }
                        // No confirmation: the library's snackbar offers undo.
                        IconButton(onClick = vm::delete, modifier = Modifier.testTag("delete")) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (tune != null) {
                PlaybackBar(
                    state = renderState,
                    onTogglePlay = vm::togglePlay,
                    onRestart = vm::restart,
                    onTempo = vm::setTempo,
                    onDismissError = vm::dismissError,
                    edit = {
                        EditTuneMenu(
                            open = editOpen,
                            onOpen = { editOpen = it },
                            tapEditable = state.tapEditable,
                            onEdit = onEdit,
                            onEditTaps = onEditTaps,
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.missing) {
                Text(stringResource(R.string.tune_missing), modifier = Modifier.padding(16.dp).testTag("missing"))
                return@Column
            }
            if (tune != null) {
                TuneHeader(
                    tune = tune,
                    tuneWhistle = state.tuneWhistle,
                    onKey = { keyDialog = true },
                    onWrittenFor = { writtenForSheet = true },
                )
            }
            state.preview?.let { preview ->
                PreviewBar(
                    preview = preview,
                    onCopy = vm::savePreviewAsCopy,
                    onReplace = vm::replaceWithPreview,
                    onCancel = vm::cancelPreview,
                )
            }
            val pageShape = MaterialTheme.shapes.large
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .clip(pageShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, pageShape),
            ) {
                TuneWebViewHost(controller, Modifier.fillMaxSize().testTag("tuneWebView"))
            }
        }
    }

    if (writtenForSheet && tune != null) {
        ModalBottomSheet(onDismissRequest = { writtenForSheet = false }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                WrittenForSection(
                    explicit = tune.explicitWhistle,
                    suggested = AbcBook.suggestWhistle(tune.key) ?: WhistleKey.DEFAULT,
                    onSelect = vm::setTuneWhistle,
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

/**
 * The tune's name in big type, then its rhythm and meter, its key (a tap
 * changes it) and the whistle it is written for (a tap picks another).
 * My whistle stays in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuneHeader(
    tune: Tune,
    tuneWhistle: WhistleKey,
    onKey: () -> Unit,
    onWrittenFor: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ScreenTitle(tune.title, Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val rhythm = listOfNotNull(tune.rhythm?.replaceFirstChar { it.titlecase() }, tune.meter).joinToString(" · ")
            if (rhythm.isNotEmpty()) {
                Text(
                    rhythm,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .background(colors.primaryContainer, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            tune.key?.let { key ->
                HeaderChip(
                    text = key,
                    onClick = onKey,
                    enabled = KeyChange.steps(tune.key, "C") != null,
                    container = colors.surfaceContainerLow,
                    content = colors.onSurface,
                    border = colors.outline,
                    modifier = Modifier.testTag("keyChip"),
                )
            }
            HeaderChip(
                text = stringResource(R.string.written_for_chip, whistleLabel(tuneWhistle)),
                onClick = onWrittenFor,
                container = colors.surfaceContainerLow,
                content = colors.onSurface,
                border = colors.outline,
                modifier = Modifier.testTag("writtenForChip"),
            )
        }
    }
}

/**
 * The player's pen button. A tune the tap editor wrote has two edits, so the
 * pen opens a small menu above it: edit the fingerings (the tap editor) or
 * edit the ABC. Any other tune has only one edit, so the pen goes straight
 * there. Choosing, a tap outside or back closes the menu.
 */
@Composable
internal fun EditTuneMenu(open: Boolean, onOpen: (Boolean) -> Unit, tapEditable: Boolean, onEdit: () -> Unit, onEditTaps: () -> Unit) {
    Box {
        FilledIconButton(
            onClick = { if (tapEditable) onOpen(!open) else onEdit() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
            modifier = Modifier.testTag("edit"),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
        }
        DropdownMenu(
            expanded = open && tapEditable,
            onDismissRequest = { onOpen(false) },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_fingerings)) },
                leadingIcon = { Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpen(false); onEditTaps() },
                modifier = Modifier.testTag("editTaps"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit_abc)) },
                leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpen(false); onEdit() },
                modifier = Modifier.testTag("editAbc"),
            )
        }
    }
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
private fun PreviewBar(preview: KeyChangePreview.Preview, onCopy: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
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

/** The dark player under the page: play, the bar being played, restart, and tempo. */
@Composable
private fun PlaybackBar(
    state: TuneRenderer.RendererState,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onTempo: (Int) -> Unit,
    onDismissError: () -> Unit,
    edit: @Composable () -> Unit = {},
) {
    var tempo by remember(state.tempo) { mutableFloatStateOf(state.tempo.toFloat()) }
    val colors = MaterialTheme.colorScheme
    val extras = LocalWhistleColors.current
    Surface(
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(52.dp).testTag("play"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = extras.play, contentColor = extras.onPlay),
                ) {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
                    )
                }
                val status = when {
                    state.error != null -> stringResource(R.string.page_error, state.error)
                    state.bar != null -> stringResource(R.string.bar_n, state.bar + 1)
                    state.warnings.isNotEmpty() -> pluralStringResource(R.plurals.warnings_count, state.warnings.size, state.warnings.size)
                    else -> ""
                }
                Text(
                    status,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (state.error != null) colors.error else colors.inverseOnSurface,
                    modifier = Modifier.weight(1f).testTag("status"),
                )
                if (state.error != null) {
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.cancel), color = colors.inverseOnSurface) }
                }
                IconButton(onClick = onRestart, modifier = Modifier.testTag("restart")) {
                    Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.restart))
                }
                edit()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.tempo_percent, tempo.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inverseOnSurface.copy(alpha = 0.75f),
                )
                Slider(
                    value = tempo,
                    onValueChange = { tempo = it },
                    onValueChangeFinished = { onTempo(tempo.roundToInt()) },
                    valueRange = 40f..150f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.inverseOnSurface,
                        activeTrackColor = colors.inverseOnSurface,
                        inactiveTrackColor = colors.inverseOnSurface.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.weight(1f).testTag("tempo"),
                )
            }
        }
    }
}

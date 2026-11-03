package io.github.conorryan.whistletabber.ui.tune

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.conorryan.whistletabber.web.BridgeCommand
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
    var keyDialog by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.export(uri)
    }

    val short = TuneLayout.isShort(LocalConfiguration.current.screenHeightDp)
    var scrollY by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    DisposableEffect(controller) {
        vm.bindPage(controller)
        controller.onScroll = { scrollY = it }
        onDispose {
            controller.onScroll = null
            controller.destroy()
        }
    }
    // The page leaves room at its top for the header that lies over it.
    LaunchedEffect(headerHeight, density) {
        if (headerHeight > 0) controller.send(BridgeCommand.TopInset(headerHeight / density.density.toDouble()))
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
    val appBar: @Composable (WindowInsets) -> Unit = { insets ->
        TopAppBar(
            // The title is the big one under the bar.
            title = {},
            windowInsets = insets,
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
    }
    Scaffold(
        // On a short screen the bar scrolls away with the header instead.
        topBar = { if (!short) appBar(TopAppBarDefaults.windowInsets) },
        bottomBar = {
            if (tune != null) {
                PlaybackBar(
                    state = renderState,
                    compact = short,
                    onTogglePlay = vm::togglePlay,
                    onRestart = vm::restart,
                    onTempo = vm::setTempo,
                    onDismissError = vm::dismissError,
                    edit = {
                        EditTuneMenu(
                            open = editOpen,
                            onOpen = { editOpen = it },
                            tapEdit = state.tapEdit,
                            onEdit = onEdit,
                            onEditTaps = onEditTaps,
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Clipped: the header leaves through the top edge, not over the status bar.
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds()) {
            if (state.missing) {
                if (short) appBar(WindowInsets(0))
                Text(stringResource(R.string.tune_missing), modifier = Modifier.padding(16.dp).testTag("missing"))
                return@Box
            }
            // The page fills the screen; its card starts under the header and
            // rises with it as the page scrolls, so the music gets the whole height.
            val corner = MaterialTheme.shapes.large.topStart
            val outline = MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .drawWithContent {
                        drawContent()
                        val top = TuneLayout.pageTop(scrollY, headerHeight).toFloat()
                        val stroke = 1.dp.toPx()
                        val radius = corner.toPx(size, this)
                        drawRoundRect(
                            color = outline,
                            topLeft = Offset(stroke / 2, top + stroke / 2),
                            size = Size(size.width - stroke, (size.height - top - stroke).coerceAtLeast(0f)),
                            cornerRadius = CornerRadius(radius - stroke / 2),
                            style = Stroke(stroke),
                        )
                    }
                    .graphicsLayer {
                        clip = true
                        shape = CardBelow(TuneLayout.pageTop(scrollY, headerHeight).toFloat(), corner)
                    },
            ) {
                TuneWebViewHost(controller, Modifier.fillMaxSize().testTag("tuneWebView"))
            }
            // Dragging the header scrolls the page under it.
            var dragRest by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, -TuneLayout.headerShift(scrollY, headerHeight)) }
                    .onSizeChanged { headerHeight = it.height }
                    .draggable(
                        rememberDraggableState { delta ->
                            val dy = dragRest - delta
                            val whole = dy.roundToInt()
                            dragRest = dy - whole
                            if (whole != 0) controller.webView.scrollBy(0, whole)
                        },
                        Orientation.Vertical,
                    )
                    .padding(bottom = 12.dp)
                    .testTag("tuneHeader"),
            ) {
                if (short) appBar(WindowInsets(0))
                if (tune != null) {
                    TuneHeader(
                        tune = tune,
                        tuneWhistle = state.tuneWhistle,
                        onKey = { keyDialog = true },
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
 * changes it) and the whistle it is written for (shown, chosen in the
 * editor). My whistle stays in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuneHeader(
    tune: Tune,
    tuneWhistle: WhistleKey,
    onKey: () -> Unit,
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
            // Not a button: the whistle is chosen in the editor.
            HeaderChip(
                text = stringResource(R.string.written_for_chip, whistleLabel(tuneWhistle)),
                onClick = null,
                container = colors.surfaceContainerLow,
                content = colors.onSurface,
                border = colors.outline,
                modifier = Modifier.testTag("writtenForChip"),
            )
        }
    }
}

/**
 * The player's pen button. A tune the tap editor can read has two edits, so
 * the pen opens a small menu above it: edit the fingerings (the tap editor;
 * as a copy when it can't keep everything) or edit the ABC. Any other tune
 * has only one edit, so the pen goes straight there. Choosing, a tap outside
 * or back closes the menu.
 */
@Composable
internal fun EditTuneMenu(open: Boolean, onOpen: (Boolean) -> Unit, tapEdit: TapEdit, onEdit: () -> Unit, onEditTaps: () -> Unit) {
    val tapEditable = tapEdit != TapEdit.NONE
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
                text = {
                    Text(stringResource(if (tapEdit == TapEdit.AS_COPY) R.string.action_edit_fingerings_copy else R.string.action_edit_fingerings))
                },
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

/** A rounded card from [top] (px) to the bottom: the page's visible frame. */
private class CardBelow(private val top: Float, private val corner: CornerSize) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(0f, top.coerceAtMost(size.height), size.width, size.height, CornerRadius(corner.toPx(size, density))),
        )
}

/**
 * The dark player under the page: play, the bar being played, restart, and
 * tempo. [compact] (a phone on its side) puts it all on one row.
 */
@Composable
private fun PlaybackBar(
    state: TuneRenderer.RendererState,
    compact: Boolean,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onTempo: (Int) -> Unit,
    onDismissError: () -> Unit,
    edit: @Composable () -> Unit = {},
) {
    var tempo by remember(state.tempo) { mutableFloatStateOf(state.tempo.toFloat()) }
    val colors = MaterialTheme.colorScheme
    val extras = LocalWhistleColors.current
    val play: @Composable () -> Unit = {
        FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(if (compact) 44.dp else 52.dp).testTag("play"),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = extras.play, contentColor = extras.onPlay),
        ) {
            Icon(
                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
            )
        }
    }
    val status: @Composable (Modifier) -> Unit = { modifier ->
        val text = when {
            state.error != null -> stringResource(R.string.page_error, state.error)
            state.bar != null -> stringResource(R.string.bar_n, state.bar + 1)
            state.warnings.isNotEmpty() -> pluralStringResource(R.plurals.warnings_count, state.warnings.size, state.warnings.size)
            else -> ""
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (state.error != null) colors.error else colors.inverseOnSurface,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.testTag("status"),
        )
        if (state.error != null) {
            TextButton(onClick = onDismissError) { Text(stringResource(R.string.cancel), color = colors.inverseOnSurface) }
        }
    }
    val restart: @Composable () -> Unit = {
        IconButton(onClick = onRestart, modifier = Modifier.testTag("restart")) {
            Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.restart))
        }
    }
    val tempoLabel: @Composable () -> Unit = {
        Text(
            stringResource(R.string.tempo_percent, tempo.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inverseOnSurface.copy(alpha = 0.75f),
        )
    }
    val tempoSlider: @Composable (Modifier) -> Unit = { modifier ->
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
            modifier = modifier.testTag("tempo"),
        )
    }
    Surface(
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp),
    ) {
        if (compact) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                play()
                status(Modifier.widthIn(max = 200.dp))
                tempoLabel()
                tempoSlider(Modifier.weight(1f))
                restart()
                edit()
            }
            return@Surface
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                play()
                status(Modifier.weight(1f))
                restart()
                edit()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tempoLabel()
                tempoSlider(Modifier.weight(1f))
            }
        }
    }
}

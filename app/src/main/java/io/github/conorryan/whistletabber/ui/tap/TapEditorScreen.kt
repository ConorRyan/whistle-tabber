package io.github.conorryan.whistletabber.ui.tap

import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.audio.TapAudio
import io.github.conorryan.whistletabber.music.RhythmSetup
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.TuneRenderer
import io.github.conorryan.whistletabber.ui.components.SectionCard
import io.github.conorryan.whistletabber.ui.settings.WhistleChips
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost
import kotlinx.coroutines.launch

@Composable
fun TapEditorScreen(vm: TapEditorViewModel, isNew: Boolean, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val renderState by vm.renderState.collectAsStateWithLifecycle()
    val controller = remember { TuneWebController(context) }
    val audio = remember { TapAudio(context) }

    DisposableEffect(controller) {
        vm.bindPage(controller)
        onDispose { controller.destroy() }
    }
    // The sound engine runs while the screen shows; leaving it ends a pass of tapping.
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(audio) {
        audio.resume()
        scope.launch { audio.open() }
        onPauseOrDispose {
            vm.stopTapping()
            audio.close()
        }
    }
    val whistle = state.draft?.whistle
    LaunchedEffect(whistle) {
        val w = whistle ?: return@LaunchedEffect
        audio.open()
        audio.prepareTaps(PALETTE_OFFSETS.map { w.writtenTonic + w.soundingShift + it }.toSet())
    }
    val panel = state.rhythm
    val tapping = panel?.phase == RhythmPhase.TAPPING
    // While tapping, touches reach the pad as they happen instead of once per frame.
    val view = LocalView.current
    DisposableEffect(tapping) {
        val unbuffered = tapping && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (unbuffered) view.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)
        onDispose { if (unbuffered) view.requestUnbufferedDispatch(0) }
    }
    var beat by remember { mutableStateOf<Int?>(null) }
    var latency by remember { mutableStateOf<Int?>(null) }
    var audioInfo by remember { mutableStateOf<TapAudio.Info?>(null) }
    LaunchedEffect(tapping) {
        if (!tapping || panel == null) {
            audio.stopMetronome()
            beat = null
            latency = null
            audioInfo = null
            return@LaunchedEffect
        }
        val draft = state.draft ?: return@LaunchedEffect
        val sounding = draft.notes.filterNotNull().map { draft.whistle.writtenTonic + draft.whistle.soundingShift + it }.toSet()
        // The metronome first, then the samples: rendering them can take a
        // second or two on a phone, and the count-in covers it.
        audio.open()
        audio.startMetronome(panel.setup.bpm, panel.setup.meter.beatsPerBar)
        audio.prepare(sounding, panel.padSound)
        var lastReopen = 0L
        while (true) {
            withFrameMillis { }
            // The output can die under us (an exclusive stream is disconnected
            // when anything else plays, the preview's WebView included):
            // reopen it and start the clicks again on the fresh stream.
            if (!audio.alive && SystemClock.uptimeMillis() - lastReopen >= 500) {
                lastReopen = SystemClock.uptimeMillis()
                audio.open()
                if (audio.alive) audio.startMetronome(panel.setup.bpm, panel.setup.meter.beatsPerBar)
            }
            val ms = audio.relative(SystemClock.uptimeMillis())
            beat = Math.floorDiv(ms, audio.beatMs.coerceAtLeast(1)).toInt()
            latency = audio.latencyMs()
            audioInfo = audio.info()
        }
    }
    // The pad's samples render while the setup sheet is up (the user is
    // picking a tempo for seconds), so Start doesn't wait for them.
    LaunchedEffect(state.rhythm?.phase, state.rhythm?.padSound, state.draft?.notes, state.draft?.whistle) {
        val sheet = state.rhythm?.takeIf { it.phase == RhythmPhase.SETUP } ?: return@LaunchedEffect
        val draft = state.draft ?: return@LaunchedEffect
        val sounding = draft.notes.filterNotNull().map { draft.whistle.writtenTonic + draft.whistle.soundingShift + it }.toSet()
        audio.prepare(sounding, sheet.padSound)
    }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is TapEditorEffect.Saved -> onSaved(effect.id)
            }
        }
    }

    TapEditorContent(
        state = state,
        renderState = renderState,
        isNew = isNew,
        onTitle = vm::setTitle,
        onWhistle = vm::setWhistle,
        onTap = { offset ->
            vm.tap(offset)
            state.draft?.whistle?.let { audio.tap(it.writtenTonic + it.soundingShift + offset) }
        },
        onRest = vm::rest,
        onDelete = vm::deleteBack,
        onCursor = vm::moveCursor,
        onOctave = vm::setOctave,
        onTogglePlay = vm::togglePlay,
        onRestart = vm::restart,
        onSave = vm::save,
        onClose = onClose,
        beat = beat,
        latencyMs = latency,
        audioInfo = audioInfo,
        onOpenRhythm = vm::openRhythm,
        onRemoveRhythm = vm::removeRhythm,
        onRhythmSetup = vm::setRhythmSetup,
        onPadSound = vm::setPadSound,
        onStartTapping = vm::startTapping,
        onPress = { uptime ->
            val offset = vm.press(audio.relative(uptime))
            val whistle = state.draft?.whistle
            if (offset != null && whistle != null) audio.noteOn(whistle.writtenTonic + whistle.soundingShift + offset, uptime)
        },
        onRelease = { uptime ->
            vm.release(audio.relative(uptime))
            audio.noteOff(uptime)
        },
        onStopTapping = vm::stopTapping,
        onRetryRhythm = vm::retryRhythm,
        onKeepRhythm = vm::keepRhythm,
        onCloseRhythm = vm::closeRhythm,
        preview = { modifier -> TuneWebViewHost(controller, modifier) },
    )
}

/** Stateless tap editor; [preview] draws the rendered tune (a WebView in the app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapEditorContent(
    state: TapEditorUiState,
    renderState: TuneRenderer.RendererState = TuneRenderer.RendererState(),
    isNew: Boolean,
    onTitle: (String) -> Unit = {},
    onWhistle: (WhistleKey) -> Unit = {},
    onTap: (Int) -> Unit = {},
    onRest: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCursor: (Int) -> Unit = {},
    onOctave: (Int) -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onRestart: () -> Unit = {},
    onSave: () -> Unit = {},
    onClose: () -> Unit = {},
    /** While tapping, the metronome's beat: negative in the count-in, 0 the first downbeat. */
    beat: Int? = null,
    /** While tapping, the audio output's delay, if known. */
    latencyMs: Int? = null,
    /** While tapping, what the audio output granted. */
    audioInfo: TapAudio.Info? = null,
    onOpenRhythm: () -> Unit = {},
    onRemoveRhythm: () -> Unit = {},
    onRhythmSetup: (RhythmSetup) -> Unit = {},
    onPadSound: (PadSound) -> Unit = {},
    onStartTapping: () -> Unit = {},
    /** A press on the rhythm pad, at the touch event's `uptimeMillis`. */
    onPress: (Long) -> Unit = {},
    onRelease: (Long) -> Unit = {},
    onStopTapping: () -> Unit = {},
    onRetryRhythm: () -> Unit = {},
    onKeepRhythm: () -> Unit = {},
    onCloseRhythm: () -> Unit = {},
    preview: @Composable (Modifier) -> Unit = {},
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = close)
    BackHandler(enabled = state.rhythm != null, onBack = onCloseRhythm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.editor_new_title else R.string.editor_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(end = 8.dp).testTag("save"),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.missing -> {
                    Text(stringResource(R.string.tune_missing), modifier = Modifier.padding(16.dp).testTag("missing"))
                    return@Column
                }
                state.unsupported -> {
                    Text(stringResource(R.string.tap_unsupported), modifier = Modifier.padding(16.dp).testTag("unsupported"))
                    return@Column
                }
            }
            val draft = state.draft ?: return@Column
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitle,
                    label = { Text(stringResource(R.string.scan_title)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("title"),
                )
                val highlight = state.rhythm?.takeIf { it.phase == RhythmPhase.TAPPING }?.let { panel ->
                    draft.notes.indices.filter { draft.notes[it] != null }.getOrNull(panel.next)
                }
                SectionCard(Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(12.dp)) {
                        NoteStrip(draft.notes, state.cursor, state.settings.cNatural, onCursor, highlight, draft.timing)
                    }
                }
                val previewShape = MaterialTheme.shapes.large
                preview(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(previewShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, previewShape)
                        .testTag("previewWebView"),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(onClick = onTogglePlay, enabled = draft.notes.isNotEmpty(), modifier = Modifier.testTag("play")) {
                        Icon(
                            if (renderState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (renderState.playing) R.string.pause else R.string.play),
                        )
                    }
                    IconButton(onClick = onRestart, modifier = Modifier.testTag("restart")) {
                        Icon(Icons.Filled.Replay, contentDescription = stringResource(R.string.restart))
                    }
                    FilledTonalButton(
                        onClick = onOpenRhythm,
                        enabled = state.rhythm == null && draft.notes.any { it != null },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.testTag("openRhythm"),
                    ) {
                        Text(stringResource(R.string.tap_rhythm))
                    }
                    if (state.timed) {
                        OutlinedButton(
                            onClick = onRemoveRhythm,
                            enabled = state.rhythm == null,
                            modifier = Modifier.testTag("removeRhythm"),
                        ) {
                            Text(stringResource(R.string.tap_remove_rhythm))
                        }
                    }
                    renderState.error?.let {
                        Text(stringResource(R.string.page_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.tap_whistle), style = MaterialTheme.typography.labelLarge)
                WhistleChips(selected = draft.whistle, onSelect = onWhistle)
            }
            val panel = state.rhythm
            // The palette or the rhythm pad sits in a sheet-like panel at the bottom.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.navigationBarsPadding().padding(top = 8.dp)) {
                    if (panel != null && panel.phase != RhythmPhase.SETUP) {
                        RhythmPanelContent(
                            panel = panel,
                            beat = beat,
                            latencyMs = latencyMs,
                            audioInfo = audioInfo,
                            onPress = onPress,
                            onRelease = onRelease,
                            onStop = onStopTapping,
                            onRetry = onRetryRhythm,
                            onKeep = onKeepRhythm,
                        )
                    } else {
                        Palette(
                            whistle = draft.whistle,
                            octave = state.octave,
                            cNatural = state.settings.cNatural,
                            canDelete = state.cursor > 0,
                            canLeft = state.cursor > 0,
                            canRight = state.cursor < draft.notes.size,
                            onOctave = onOctave,
                            onTap = onTap,
                            onRest = onRest,
                            onDelete = onDelete,
                            onLeft = { onCursor(state.cursor - 1) },
                            onRight = { onCursor(state.cursor + 1) },
                        )
                    }
                }
            }
        }
        // The setup opens in a bottom sheet, so the preview keeps its room.
        val sheet = state.rhythm?.takeIf { it.phase == RhythmPhase.SETUP }
        if (sheet != null) {
            ModalBottomSheet(
                onDismissRequest = onCloseRhythm,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                RhythmSetupSheet(
                    panel = sheet,
                    onSetup = onRhythmSetup,
                    onPadSound = onPadSound,
                    onStart = onStartTapping,
                    onClose = onCloseRhythm,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }, modifier = Modifier.testTag("discard")) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

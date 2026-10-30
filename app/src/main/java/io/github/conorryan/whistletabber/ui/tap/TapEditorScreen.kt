package io.github.conorryan.whistletabber.ui.tap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.AbcNotes
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.TuneRenderer
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames
import io.github.conorryan.whistletabber.ui.settings.WhistleChips
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost

@Composable
fun TapEditorScreen(vm: TapEditorViewModel, isNew: Boolean, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val renderState by vm.renderState.collectAsStateWithLifecycle()
    val controller = remember { TuneWebController(context) }
    val player = remember { NotePlayer() }

    DisposableEffect(controller) {
        vm.bindPage(controller)
        onDispose {
            controller.destroy()
            player.release()
        }
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
            state.draft?.whistle?.let { player.play(it.writtenTonic + it.soundingShift + offset) }
        },
        onRest = vm::rest,
        onDelete = vm::deleteBack,
        onCursor = vm::moveCursor,
        onOctave = vm::setOctave,
        onTogglePlay = vm::togglePlay,
        onRestart = vm::restart,
        onSave = vm::save,
        onClose = onClose,
        preview = { modifier -> TuneWebViewHost(controller, modifier) },
    )
}

/** The fingerings of the palette's main row, as offsets in the bottom octave: the major scale, then C natural. */
private val SCALE = listOf(0, 2, 4, 5, 7, 9, 11, 10)

/** Half-holed notes, behind "More fingerings". */
private val CHROMATIC = listOf(1, 3, 6, 8)

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
    preview: @Composable (Modifier) -> Unit = {},
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = close)

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
                    TextButton(onClick = onSave, enabled = state.canSave, modifier = Modifier.testTag("save")) {
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
                    modifier = Modifier.fillMaxWidth().testTag("title"),
                )
                NoteStrip(draft.notes, state.cursor, state.settings.cNatural, onCursor)
                preview(Modifier.fillMaxWidth().height(220.dp).testTag("previewWebView"))
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
                    renderState.error?.let {
                        Text(stringResource(R.string.page_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.tap_whistle), style = MaterialTheme.typography.labelLarge)
                WhistleChips(selected = draft.whistle, onSelect = onWhistle)
            }
            HorizontalDivider()
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

/** The tune so far, a diagram per note with a gap at each bar and the cursor; tap a note to put the cursor after it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteStrip(notes: List<Int?>, cursor: Int, cNatural: CNatural, onCursor: (Int) -> Unit) {
    if (notes.isEmpty()) {
        Text(
            stringResource(R.string.tap_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tapEmpty"),
        )
        return
    }
    val restLabel = stringResource(R.string.tap_rest)
    FlowRow(Modifier.fillMaxWidth().testTag("noteStrip"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..notes.size) {
            if (i == cursor) Cursor()
            if (i == notes.size) break
            if (i > 0 && i % TapTune.PER_BAR == 0) Box(Modifier.width(12.dp))
            val fingering = notes[i]?.let { FingeringEngine.fingeringAt(it, cNatural) }
            Column(
                Modifier
                    .clickable { onCursor(i + 1) }
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .testTag("note-$i"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (fingering != null) {
                    FingeringDiagram(fingering, holeSize = 7.dp)
                    OctaveMark(fingering)
                } else {
                    Box(Modifier.height(64.dp).semantics { contentDescription = restLabel }, contentAlignment = Alignment.Center) {
                        Text("–", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cursor() {
    Box(Modifier.padding(vertical = 2.dp).width(2.dp).height(64.dp).border(1.dp, MaterialTheme.colorScheme.primary).testTag("cursor"))
}

@Composable
private fun OctaveMark(fingering: Fingering) {
    Text("+".repeat(fingering.octave).ifEmpty { " " }, style = MaterialTheme.typography.labelSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Palette(
    whistle: WhistleKey,
    octave: Int,
    cNatural: CNatural,
    canDelete: Boolean,
    canLeft: Boolean,
    canRight: Boolean,
    onOctave: (Int) -> Unit,
    onTap: (Int) -> Unit,
    onRest: () -> Unit,
    onDelete: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    var more by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.tap_octave_low, R.string.tap_octave_high, R.string.tap_octave_highest).forEachIndexed { i, label ->
                FilterChip(
                    selected = octave == i,
                    onClick = { onOctave(i) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("octave-$i"),
                )
            }
        }
        KeyRow(SCALE, whistle, octave, cNatural, onTap)
        if (more) KeyRow(CHROMATIC, whistle, octave, cNatural, onTap)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onRest, modifier = Modifier.testTag("rest")) { Text(stringResource(R.string.tap_rest)) }
            IconButton(onClick = onLeft, enabled = canLeft, modifier = Modifier.testTag("left")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tap_cursor_left))
            }
            IconButton(onClick = onRight, enabled = canRight, modifier = Modifier.testTag("right")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tap_cursor_right))
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.testTag("delete")) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.tap_delete))
            }
            TextButton(onClick = { more = !more }, modifier = Modifier.testTag("more")) {
                Text(stringResource(if (more) R.string.tap_fewer else R.string.tap_more))
            }
        }
    }
}

/**
 * One button per fingering in [offsets] (bottom-octave offsets) moved to
 * [octave]; unplayable ones are left out. Every row has [SCALE]'s number of
 * equal slots, so keys line up and a short row is centred.
 */
@Composable
private fun KeyRow(offsets: List<Int>, whistle: WhistleKey, octave: Int, cNatural: CNatural, onTap: (Int) -> Unit) {
    val names = LocalNoteNames.current
    val keys = offsets.map { it + 12 * octave }.mapNotNull { offset -> FingeringEngine.fingeringAt(offset, cNatural)?.let { offset to it } }
    val empty = SCALE.size - keys.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (empty > 0) Box(Modifier.weight(empty / 2f))
        for ((offset, fingering) in keys) {
            Surface(
                onClick = { onTap(offset) },
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.weight(1f).testTag("key-$offset"),
            ) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FingeringDiagram(fingering, holeSize = 10.dp)
                    Text(
                        AbcNotes.name(whistle.writtenTonic + offset, whistle.letter, names) + "+".repeat(fingering.octave),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (fingering.hard) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
        if (empty > 0) Box(Modifier.weight(empty / 2f))
    }
}

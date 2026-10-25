package io.github.conorryan.whistletabber.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.music.AbcProblem
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.web.TuneWebController
import io.github.conorryan.whistletabber.web.TuneWebViewHost

@Composable
fun EditorScreen(vm: EditorViewModel, isNew: Boolean, onClose: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val renderState by vm.renderState.collectAsStateWithLifecycle()
    val controller = remember { TuneWebController(context) }

    DisposableEffect(controller) {
        vm.bindPage(controller)
        onDispose { controller.destroy() }
    }
    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is EditorEffect.Saved -> onSaved(effect.id)
            }
        }
    }

    EditorContent(
        state = state,
        warnings = renderState.warnings,
        isNew = isNew,
        onText = vm::setText,
        onSave = vm::save,
        onClose = onClose,
        preview = { modifier -> TuneWebViewHost(controller, modifier) },
    )
}

/** Stateless editor UI; [preview] draws the live preview (a WebView in the app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorUiState,
    warnings: List<String> = emptyList(),
    isNew: Boolean,
    onText: (String) -> Unit = {},
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
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.missing) {
                Text(stringResource(R.string.tune_missing), modifier = Modifier.padding(16.dp).testTag("missing"))
                return@Column
            }
            val text = state.text ?: return@Column
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    label = { Text(stringResource(R.string.editor_abc_label)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("abcText"),
                )
                for (problem in state.problems) {
                    Text(
                        stringResource(
                            when (problem) {
                                AbcProblem.NO_TITLE -> R.string.problem_no_title
                                AbcProblem.NO_KEY -> R.string.problem_no_key
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("problem-${problem.name}"),
                    )
                }
                for ((i, warning) in warnings.withIndex()) {
                    Text(
                        warning,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("warning-$i"),
                    )
                }
            }
            Text(
                stringResource(R.string.editor_preview),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            preview(Modifier.fillMaxWidth().weight(1f).testTag("previewWebView"))
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

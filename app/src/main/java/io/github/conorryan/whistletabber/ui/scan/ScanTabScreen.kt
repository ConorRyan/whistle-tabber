package io.github.conorryan.whistletabber.ui.scan

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.scan.GrayImage
import io.github.conorryan.whistletabber.scan.TabReading
import io.github.conorryan.whistletabber.scan.TabTune
import io.github.conorryan.whistletabber.ui.settings.WhistleChips

/** Picks an image of whistle tab, reads it, and hands the tune to the editor as [onOpenInEditor]'s ABC. */
@Composable
fun ScanTabScreen(vm: ScanTabViewModel, onBack: () -> Unit, onOpenInEditor: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pick(uri)
    }
    val choose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Open the picker straight away the first time; back from it leaves the explanation.
    var offered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!offered) {
            offered = true
            choose()
        }
    }
    ScanTabContent(
        state = state,
        onBack = onBack,
        onChoose = choose,
        onWhistle = vm::setWhistle,
        onTitle = vm::setTitle,
        onOpen = { state.abc()?.let(onOpenInEditor) },
    )
}

/** Stateless scan UI, separate from [ScanTabScreen] so Compose tests can drive it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanTabContent(
    state: ScanTabUiState,
    onBack: () -> Unit = {},
    onChoose: () -> Unit = {},
    onWhistle: (WhistleKey) -> Unit = {},
    onTitle: (String) -> Unit = {},
    onOpen: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_tab)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val read = state.reading != null && state.found > 0
            if (!read) Text(stringResource(R.string.scan_howto))
            OutlinedButton(onClick = onChoose, enabled = !state.busy, modifier = Modifier.testTag("choose")) {
                Text(stringResource(if (state.image == null) R.string.scan_choose else R.string.scan_other))
            }
            when {
                state.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.scan_reading))
                }
                state.unreadable -> ErrorText(stringResource(R.string.scan_unreadable), "unreadable")
                state.reading != null && !read -> ErrorText(stringResource(R.string.scan_none), "none")
            }
            if (read) {
                Text(
                    pluralStringResource(R.plurals.scan_found, state.found, state.found),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("found"),
                )
                if (state.unknown > 0) ErrorText(pluralStringResource(R.plurals.scan_unknown, state.unknown, state.unknown), "unknown")
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitle,
                    label = { Text(stringResource(R.string.scan_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("title"),
                )
                Text(stringResource(R.string.scan_whistle), style = MaterialTheme.typography.titleSmall)
                WhistleChips(state.whistle, onWhistle, tagPrefix = "scanWhistle")
                Button(onClick = onOpen, enabled = state.canOpen, modifier = Modifier.testTag("open")) {
                    Text(stringResource(R.string.scan_open_editor))
                }
            }
            state.image?.let { image -> ReadImage(image, state.reading, state.cNatural) }
        }
    }
}

@Composable
private fun ErrorText(text: String, tag: String) =
    Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag))

/** The picked image with each diagram outlined: recognised in the primary colour, unknown in the error colour. */
@Composable
private fun ReadImage(image: GrayImage, reading: TabReading?, cNatural: CNatural) {
    val bitmap = remember(image) {
        val argb = IntArray(image.luma.size) { val l = image.luma[it]; (0xFF shl 24) or (l shl 16) or (l shl 8) or l }
        Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val known = MaterialTheme.colorScheme.primary
    val unknown = MaterialTheme.colorScheme.error
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.scan_image_description),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(image.width.toFloat() / image.height)
            .testTag("image")
            .drawWithContent {
                drawContent()
                val scale = size.width / image.width
                val stroke = Stroke(width = 2.dp.toPx())
                for (column in reading?.columns.orEmpty()) {
                    val b = column.box
                    drawRect(
                        color = if (TabTune.offsetOf(column, cNatural) == null) unknown else known,
                        topLeft = Offset(b.left * scale - 2, b.top * scale - 2),
                        size = Size((b.right - b.left + 1) * scale + 4, (b.bottom - b.top + 1) * scale + 4),
                        style = stroke,
                    )
                }
            },
    )
}

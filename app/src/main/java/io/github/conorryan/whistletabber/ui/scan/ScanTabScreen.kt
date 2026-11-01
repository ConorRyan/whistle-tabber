package io.github.conorryan.whistletabber.ui.scan

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.data.tunes.PastedTunes
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.scan.GrayImage
import io.github.conorryan.whistletabber.scan.TabReading
import io.github.conorryan.whistletabber.scan.TabTune
import io.github.conorryan.whistletabber.ui.components.SectionCard
import io.github.conorryan.whistletabber.ui.settings.WhistleChips

/**
 * Picks images of whistle tab, reads them, and hands the tune to the editor as
 * [onOpenInEditor]'s ABC. [image], a clipboard URI from the library's Paste,
 * is read on entry instead of opening the picker; a paste can add more.
 */
@Composable
fun ScanTabScreen(vm: ScanTabViewModel, onBack: () -> Unit, onOpenInEditor: (String) -> Unit, image: String? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.add(uris)
    }
    val choose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val paste = {
        val pasted = PastedTunes.from(context.getSystemService(ClipboardManager::class.java)?.primaryClip)?.image
        if (pasted != null) vm.add(listOf(pasted)) else vm.pasteFailed()
    }
    // Open the picker straight away the first time; back from it leaves the explanation.
    var offered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!offered) {
            offered = true
            image?.let { vm.add(listOf(Uri.parse(it))) } ?: choose()
        }
    }
    ScanTabContent(
        state = state,
        onBack = onBack,
        onChoose = choose,
        onPaste = paste,
        onStartOver = vm::startOver,
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
    onPaste: () -> Unit = {},
    onStartOver: () -> Unit = {},
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
            val read = state.found > 0
            if (read) {
                Text(
                    pluralStringResource(R.plurals.scan_found, state.found, state.found),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("found"),
                )
                if (state.unknown > 0) ErrorText(pluralStringResource(R.plurals.scan_unknown, state.unknown, state.unknown), "unknown")
            }
            if (state.images.isEmpty()) Text(stringResource(R.string.scan_howto), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onChoose,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("choose"),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (state.images.isEmpty()) R.string.scan_choose else R.string.scan_add), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onPaste, enabled = !state.busy, shape = RoundedCornerShape(50), modifier = Modifier.testTag("paste")) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.scan_paste), Modifier.padding(start = 8.dp))
                }
            }
            when {
                state.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.scan_reading))
                }
                state.unreadable -> ErrorText(stringResource(R.string.scan_unreadable), "unreadable")
                state.pasteEmpty -> ErrorText(stringResource(R.string.paste_no_image), "pasteEmpty")
                state.images.isNotEmpty() && !read -> ErrorText(stringResource(R.string.scan_none), "none")
            }
            if (state.images.isNotEmpty()) TextButton(
                onClick = onStartOver,
                enabled = !state.busy,
                modifier = Modifier.testTag("startOver"),
            ) {
                Text(stringResource(R.string.scan_start_over))
            }
            if (read) {
                SectionCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = onTitle,
                            label = { Text(stringResource(R.string.scan_title)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().testTag("title"),
                        )
                        Text(stringResource(R.string.scan_whistle), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WhistleChips(state.whistle, onWhistle, tagPrefix = "scanWhistle")
                    }
                }
                Button(
                    onClick = onOpen,
                    enabled = state.canOpen,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("open"),
                ) {
                    Text(stringResource(R.string.scan_open_editor), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(20.dp))
                }
            }
            state.images.forEach { scanned -> ReadImage(scanned.image, scanned.reading, state.cNatural) }
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
    val shape = MaterialTheme.shapes.large
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.scan_image_description),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
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

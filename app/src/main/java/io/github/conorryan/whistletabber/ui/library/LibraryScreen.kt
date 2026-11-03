package io.github.conorryan.whistletabber.ui.library

import android.content.ClipboardManager
import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.data.tunes.ExportNames
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.data.tunes.PastedTunes
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.ui.components.PillChip
import io.github.conorryan.whistletabber.ui.components.whistleLabel
import io.github.conorryan.whistletabber.ui.theme.LocalWhistleColors
import io.github.conorryan.whistletabber.ui.theme.SectionLabel
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

/** MIME types offered when picking `.abc` files; many file managers report them as octet-stream. */
val ABC_MIME_TYPES = arrayOf("text/*", "application/octet-stream", "*/*")

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenTune: (Long) -> Unit,
    onNewTune: () -> Unit,
    onSettings: () -> Unit,
    onFindTunes: () -> Unit,
    onScanTab: () -> Unit,
    onScanTabImage: (Uri) -> Unit,
    onWriteTaps: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportAll(uri)
    }
    val exportSelectedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportSelected(uri)
    }
    val paste: () -> Unit = {
        when (val pasted = PastedTunes.from(context.getSystemService(ClipboardManager::class.java)?.primaryClip)) {
            null -> scope.launch { snackbar.showSnackbar(context.getString(R.string.paste_empty)) }
            else -> pasted.image?.let(onScanTabImage) ?: vm.paste(pasted.files, pasted.text)
        }
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val res = context.resources
            when (message) {
                is LibraryMessage.Imported -> snackbar.showSnackbar(importSummary(res, message.result))
                is LibraryMessage.Exported -> snackbar.showSnackbar(res.getQuantityString(R.plurals.exported, message.count, message.count))
                LibraryMessage.ExportFailed -> snackbar.showSnackbar(res.getString(R.string.export_failed))
                is LibraryMessage.Deleted -> {
                    val tunes = message.tunes
                    val result = snackbar.showSnackbar(
                        tunes.singleOrNull()?.let { res.getString(R.string.deleted, it.title) }
                            ?: res.getQuantityString(R.plurals.deleted_many, tunes.size, tunes.size),
                        actionLabel = res.getString(R.string.undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(tunes) else vm.forgetDeleted(tunes)
                }
            }
        }
    }

    LibraryContent(
        state = state,
        snackbar = snackbar,
        onQuery = vm::setQuery,
        onRhythm = vm::toggleRhythm,
        onStarredOnly = vm::toggleStarredOnly,
        onClearFilters = vm::clearFilters,
        onStar = vm::setStarred,
        onToggleSelected = vm::toggleSelected,
        onSelectAll = vm::selectAll,
        onClearSelection = vm::clearSelection,
        onDeleteSelected = vm::deleteSelected,
        onExportSelected = {
            val single = state.tunes.filter { it.id in state.selected }.singleOrNull()
            exportSelectedLauncher.launch(single?.let { ExportNames.tune(it.title) } ?: ExportNames.library(LocalDate.now()))
        },
        onOpenTune = onOpenTune,
        onNewTune = onNewTune,
        onSettings = onSettings,
        onFindTunes = onFindTunes,
        onScanTab = onScanTab,
        onWriteTaps = onWriteTaps,
        onAddStarterSet = vm::addStarterSet,
        onImport = { importLauncher.launch(ABC_MIME_TYPES) },
        onPaste = paste,
        onExport = { exportLauncher.launch(ExportNames.library(LocalDate.now())) },
    )
}

/** Stateless library UI, separate from [LibraryScreen] so Compose tests can drive it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onQuery: (String) -> Unit = {},
    onRhythm: (String) -> Unit = {},
    onStarredOnly: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onStar: (Tune, Boolean) -> Unit = { _, _ -> },
    onToggleSelected: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onExportSelected: () -> Unit = {},
    onOpenTune: (Long) -> Unit = {},
    onNewTune: () -> Unit = {},
    onSettings: () -> Unit = {},
    onFindTunes: () -> Unit = {},
    onScanTab: () -> Unit = {},
    onWriteTaps: () -> Unit = {},
    onAddStarterSet: () -> Unit = {},
    onImport: () -> Unit = {},
    onPaste: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    var addOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = state.selecting || addOpen) { if (addOpen) addOpen = false else onClearSelection() }
    LaunchedEffect(state.selecting) { if (state.selecting) addOpen = false }
    Scaffold(
        topBar = {
            if (state.selecting) {
                SelectionBar(state.selected.size, onClearSelection, onSelectAll, onExportSelected, onDeleteSelected)
            } else Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.app_name).uppercase(),
                        style = SectionLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge)
                        if (state.loaded && !state.libraryEmpty) {
                            Text(
                                state.tunes.size.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 4.dp).testTag("tuneCount"),
                            )
                        }
                    }
                }
                IconButton(onClick = onExport, enabled = !state.libraryEmpty, modifier = Modifier.testTag("export")) {
                    Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.action_export_all))
                }
                IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                }
            }
        },
        floatingActionButton = {
            if (!state.selecting) {
                AddTuneMenu(
                    open = addOpen,
                    onOpen = { addOpen = it },
                    actions = listOf(
                        AddTuneAction(R.string.write_abc_by_hand, Icons.Filled.EditNote, "addWriteText", onNewTune),
                        AddTuneAction(R.string.tap_write, Icons.Filled.TouchApp, "addWriteTaps", onWriteTaps),
                        AddTuneAction(R.string.scan_tab, Icons.Filled.DocumentScanner, "addScanTab", onScanTab),
                        AddTuneAction(R.string.action_import, Icons.Filled.UploadFile, "addImport", onImport),
                        AddTuneAction(R.string.action_paste, Icons.Filled.ContentPaste, "addPaste", onPaste),
                        AddTuneAction(R.string.find_tunes, Icons.Filled.TravelExplore, "addFindTunes", onFindTunes),
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("busy"))
            when {
                !state.loaded -> Unit
                state.libraryEmpty -> EmptyLibrary(state.busy, onAddStarterSet, onImport, onFindTunes)
                else -> {
                    Filters(state, onQuery, onRhythm, onStarredOnly)
                    if (state.tunes.isEmpty()) {
                        NoMatches(onClearFilters)
                    } else {
                        Box {
                            LazyColumn(
                                Modifier.fillMaxSize().testTag("tuneList"),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(state.tunes, key = { it.id }, contentType = { "tune" }) { tune ->
                                    TuneRow(
                                        tune,
                                        selected = if (state.selecting) tune.id in state.selected else null,
                                        onClick = { if (state.selecting) onToggleSelected(tune.id) else onOpenTune(tune.id) },
                                        onLongClick = { onToggleSelected(tune.id) },
                                        onStar = { onStar(tune, !tune.starred) },
                                    )
                                }
                            }
                            // Tapping the list outside the expanded dial dismisses it.
                            if (addOpen) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { addOpen = false })
                                        .testTag("addScrim"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One way to add a tune: its label, icon, test tag and destination. */
private data class AddTuneAction(
    val label: Int,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * The "+" button, expanded into a stack of labelled mini buttons above it (a
 * speed dial): write ABC, write fingerings, read tab from an image, import
 * files, paste from the clipboard, find tunes online. The button itself rotates to ×; a tap on the scrim
 * or back closes it again. Every action closes it first, so navigating away
 * never leaves it open.
 */
@Composable
private fun AddTuneMenu(open: Boolean, onOpen: (Boolean) -> Unit, actions: List<AddTuneAction>) {
    val newTune = stringResource(R.string.action_new_tune)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        actions.forEach { action ->
            AnimatedVisibility(visible = open, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                        Text(stringResource(action.label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    SmallFloatingActionButton(
                        onClick = { onOpen(false); action.onClick() },
                        modifier = Modifier.testTag(action.tag),
                        shape = RoundedCornerShape(50),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(action.icon, contentDescription = stringResource(action.label))
                    }
                }
            }
        }
        val rotation by animateFloatAsState(if (open) 45f else 0f)
        ExtendedFloatingActionButton(
            onClick = { onOpen(!open) },
            expanded = !open,
            icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.rotate(rotation)) },
            text = { Text(stringResource(R.string.action_new_tune)) },
            shape = RoundedCornerShape(50),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("newTune").semantics { contentDescription = newTune },
        )
    }
}

@Composable
private fun Filters(
    state: LibraryUiState,
    onQuery: (String) -> Unit,
    onRhythm: (String) -> Unit,
    onStarredOnly: () -> Unit,
) {
    OutlinedTextField(
        value = state.filters.query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (state.filters.query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_filters))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).testTag("search"),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item {
            PillChip(
                selected = state.filters.starredOnly,
                onClick = onStarredOnly,
                label = { Text(stringResource(R.string.filter_starred)) },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = if (state.filters.starredOnly) MaterialTheme.colorScheme.onPrimary else LocalWhistleColors.current.star, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("chip-starred"),
            )
        }
        items(state.rhythms) { rhythm ->
            PillChip(
                selected = state.filters.rhythm == rhythm,
                onClick = { onRhythm(rhythm) },
                label = { Text(rhythm.capitalized()) },
                modifier = Modifier.testTag("chip-$rhythm"),
            )
        }
    }
}

/** The top bar while tunes are selected: cancel, how many, select all, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count), Modifier.testTag("selectedCount")) },
        navigationIcon = {
            IconButton(onClick = onClear, modifier = Modifier.testTag("clearSelection")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll, modifier = Modifier.testTag("selectAll")) {
                Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
            }
            IconButton(onClick = onExport, modifier = Modifier.testTag("exportSelected")) {
                Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.action_export_selected))
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("deleteSelected")) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

/**
 * A tune in the list: a card with the title, a rhythm badge and the key and
 * whistle. Deliberately plain: no Material3 `ListItem` or `Card`, just a
 * background, a border and one ripple, so composing a row while scrolling
 * stays cheap (this list is the main screen; debug builds feel every
 * composable). [selected] is null outside selection mode; in it, a checkbox
 * replaces the star.
 */
@Composable
private fun TuneRow(tune: Tune, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit, onStar: () -> Unit) {
    val details = listOfNotNull(
        tune.key,
        tune.whistle?.let { stringResource(R.string.tune_whistle, whistleLabel(it)) },
    ).joinToString(" · ")
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val selection = when (selected) {
        null -> Modifier
        true -> Modifier.semantics { toggleableState = ToggleableState.On }
        false -> Modifier.semantics { toggleableState = ToggleableState.Off }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected == true) colors.primaryContainer else colors.surfaceContainerLow)
            .border(1.dp, if (selected == true) colors.primary else colors.outlineVariant, shape)
            .then(selection)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .testTag("tune-${tune.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                tune.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tune.rhythm?.let { rhythm ->
                    Text(
                        rhythm.capitalized(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.background(colors.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (selected != null) {
            // The row's own click toggles and carries the state; the checkbox only shows it.
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("select-${tune.id}"),
            )
        } else {
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onStar, role = Role.Button)
                    .testTag("star-${tune.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (tune.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(if (tune.starred) R.string.unstar else R.string.star),
                    tint = if (tune.starred) LocalWhistleColors.current.star else colors.outline,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(busy: Boolean, onAddStarterSet: () -> Unit, onImport: () -> Unit, onFindTunes: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("emptyLibrary"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onAddStarterSet, enabled = !busy, modifier = Modifier.testTag("addStarterSet")) {
            Text(stringResource(R.string.add_starter_set))
        }
        OutlinedButton(onClick = onImport, enabled = !busy) {
            Text(stringResource(R.string.action_import))
        }
        TextButton(onClick = onFindTunes, modifier = Modifier.testTag("findTunes")) {
            Text(stringResource(R.string.find_tunes))
        }
    }
}

@Composable
private fun NoMatches(onClearFilters: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp).testTag("noMatches"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.no_matches), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClearFilters) { Text(stringResource(R.string.clear_filters)) }
    }
}

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "3 tunes added, 1 duplicate skipped", or "No tunes found". */
fun importSummary(res: Resources, result: ImportResult): String {
    val parts = buildList {
        if (result.added > 0) add(res.getQuantityString(R.plurals.import_added, result.added, result.added))
        if (result.skipped > 0) add(res.getQuantityString(R.plurals.import_skipped, result.skipped, result.skipped))
        if (result.errors.isNotEmpty()) add(res.getQuantityString(R.plurals.import_errors, result.errors.size, result.errors.size))
        if (result.unreadable > 0) add(res.getQuantityString(R.plurals.import_unreadable, result.unreadable, result.unreadable))
    }
    return if (parts.isEmpty()) res.getString(R.string.import_nothing) else parts.joinToString(", ")
}

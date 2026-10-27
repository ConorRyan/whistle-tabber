package io.github.conorryan.whistletabber.ui.library

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.data.tunes.ExportNames
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.ui.components.whistleLabel
import java.time.LocalDate
import java.util.Locale

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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportAll(uri)
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
        onOpenTune = onOpenTune,
        onNewTune = onNewTune,
        onSettings = onSettings,
        onFindTunes = onFindTunes,
        onScanTab = onScanTab,
        onAddStarterSet = vm::addStarterSet,
        onImport = { importLauncher.launch(ABC_MIME_TYPES) },
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
    onOpenTune: (Long) -> Unit = {},
    onNewTune: () -> Unit = {},
    onSettings: () -> Unit = {},
    onFindTunes: () -> Unit = {},
    onScanTab: () -> Unit = {},
    onAddStarterSet: () -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = state.selecting, onBack = onClearSelection)
    Scaffold(
        topBar = {
            if (state.selecting) {
                SelectionBar(state.selected.size, onClearSelection, onSelectAll, onDeleteSelected)
            } else TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("menu")) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_import)) },
                                onClick = { menuOpen = false; onImport() },
                                modifier = Modifier.testTag("menuImport"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.find_tunes)) },
                                onClick = { menuOpen = false; onFindTunes() },
                                modifier = Modifier.testTag("menuFindTunes"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scan_tab)) },
                                onClick = { menuOpen = false; onScanTab() },
                                modifier = Modifier.testTag("menuScanTab"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_export_all)) },
                                onClick = { menuOpen = false; onExport() },
                                enabled = !state.libraryEmpty,
                                modifier = Modifier.testTag("menuExport"),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.selecting) {
                FloatingActionButton(onClick = onNewTune, modifier = Modifier.testTag("newTune")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_tune))
                }
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
                        LazyColumn(Modifier.fillMaxSize().testTag("tuneList"), contentPadding = PaddingValues(bottom = 88.dp)) {
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
                    }
                }
            }
        }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search"),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filters.starredOnly,
                onClick = onStarredOnly,
                label = { Text(stringResource(R.string.filter_starred)) },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                modifier = Modifier.testTag("chip-starred"),
            )
        }
        items(state.rhythms) { rhythm ->
            FilterChip(
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
private fun SelectionBar(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
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
            IconButton(onClick = onDelete, modifier = Modifier.testTag("deleteSelected")) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

/**
 * A single line of the tune list. Deliberately plain: no Material3 `ListItem`,
 * no shape or container work, one ripple, so composing a row while scrolling
 * stays cheap (this list is the main screen; debug builds feel every
 * composable). The divider belongs to the row so the list holds one item per
 * tune. [selected] is null outside selection mode; in it, a checkbox replaces
 * the star.
 */
@Composable
private fun TuneRow(tune: Tune, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit, onStar: () -> Unit) {
    val details = listOfNotNull(
        tune.rhythm?.capitalized(),
        tune.key,
        tune.whistle?.let { stringResource(R.string.tune_whistle, whistleLabel(it)) },
    ).joinToString(" · ")
    val selection = when (selected) {
        null -> Modifier
        true -> Modifier.background(MaterialTheme.colorScheme.secondaryContainer).semantics { toggleableState = ToggleableState.On }
        false -> Modifier.semantics { toggleableState = ToggleableState.Off }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(selection)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .testTag("tune-${tune.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Text(tune.title, style = MaterialTheme.typography.bodyLarge)
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        tint = if (tune.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun EmptyLibrary(busy: Boolean, onAddStarterSet: () -> Unit, onImport: () -> Unit, onFindTunes: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("emptyLibrary"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
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

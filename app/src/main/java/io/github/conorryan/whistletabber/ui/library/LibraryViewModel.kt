package io.github.conorryan.whistletabber.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.data.tunes.TuneImporter
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryFilters(
    val query: String = "",
    val rhythm: String? = null,
    val starredOnly: Boolean = false,
) {
    val active: Boolean get() = query.isNotBlank() || rhythm != null || starredOnly
}

data class LibraryUiState(
    val filters: LibraryFilters = LibraryFilters(),
    val tunes: List<Tune> = emptyList(),
    val rhythms: List<String> = emptyList(),
    /** False until the first database read, so the empty state doesn't flash. */
    val loaded: Boolean = false,
    /** The library has no tunes at all (not just none matching the filters). */
    val libraryEmpty: Boolean = false,
    val busy: Boolean = false,
)

sealed interface LibraryMessage {
    data class Imported(val result: ImportResult) : LibraryMessage
    data class Exported(val count: Int) : LibraryMessage
    data object ExportFailed : LibraryMessage
    data class Deleted(val tune: Tune) : LibraryMessage
}

/**
 * The tune list with search and filters, the starter-set offer, and import
 * and export of whole files. Imports run in [appScope] so leaving the screen
 * doesn't cancel them half way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: TuneRepository,
    private val importer: TuneImporter,
    private val files: AbcFiles,
    private val recentlyDeleted: RecentlyDeleted,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())

    private val _messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val messages: Flow<LibraryMessage> = _messages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        filters,
        filters.flatMapLatest { repository.search(it.query, it.rhythm, it.starredOnly) },
        repository.rhythms,
        repository.count,
        importer.busy,
    ) { filters, tunes, rhythms, count, busy ->
        LibraryUiState(
            filters = filters,
            tunes = tunes,
            rhythms = rhythms,
            loaded = true,
            libraryEmpty = count == 0,
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            recentlyDeleted.offer.filterNotNull().collect { tune ->
                _messages.send(LibraryMessage.Deleted(tune))
            }
        }
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    /** Selecting the selected rhythm again clears it. */
    fun toggleRhythm(rhythm: String) = filters.update { it.copy(rhythm = if (it.rhythm == rhythm) null else rhythm) }

    fun toggleStarredOnly() = filters.update { it.copy(starredOnly = !it.starredOnly) }

    fun clearFilters() = filters.update { LibraryFilters() }

    fun setStarred(tune: Tune, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(tune.id, starred) }
    }

    fun addStarterSet() = runImport { importer.addStarterSet() }

    fun importFiles(uris: List<Uri>) {
        if (uris.isNotEmpty()) runImport { importer.importFiles(uris) }
    }

    fun exportAll(uri: Uri) {
        appScope.launch {
            val message = runCatching {
                files.write(uri, repository.exportAll())
                LibraryMessage.Exported(repository.count.first())
            }.getOrElse { LibraryMessage.ExportFailed }
            _messages.send(message)
        }
    }

    /** Puts back a tune deleted on the tune screen. */
    fun undoDelete(tune: Tune) {
        recentlyDeleted.undo(tune)
    }

    /** The Undo offer for [tune] was dismissed. */
    fun forgetDeleted(tune: Tune) {
        recentlyDeleted.dismiss(tune)
    }

    private fun runImport(block: suspend () -> ImportResult) {
        appScope.launch { _messages.send(LibraryMessage.Imported(block())) }
    }
}

package io.github.conorryan.whistletabber.data.tunes

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Brings tunes into the library: `.abc` files picked by the user or shared
 * by another app, shared ABC text, and the bundled O'Neill's starter set
 * (built by scripts/build-starter-set.sh).
 * Duplicates are skipped (see [TuneRepository.importBook]), so adding the
 * same file or the starter set twice is harmless. [busy] is true while any
 * import runs, on any screen. Call from a scope that outlives the screen.
 */
class TuneImporter(
    private val repository: TuneRepository,
    private val files: AbcFiles,
    private val loadStarterSet: () -> String,
) {
    private var running = 0

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Every tune in [uris], as one result; a file that can't be read counts as unreadable. */
    suspend fun importFiles(uris: List<Uri>): ImportResult = tracked {
        uris.fold(ImportResult.NONE) { total, uri ->
            val text = runCatching { files.read(uri) }.getOrNull()
            total + (text?.let { repository.importBook(it) } ?: ImportResult.UNREADABLE_FILE)
        }
    }

    /** Every tune in [text], such as ABC shared from a browser. */
    suspend fun importText(text: String): ImportResult = tracked { repository.importBook(text) }

    suspend fun addStarterSet(): ImportResult = tracked {
        val text = withContext(Dispatchers.IO) { loadStarterSet() }
        repository.importBook(text, TuneEntity.SOURCE_STARTER)
    }

    private suspend fun <T> tracked(block: suspend () -> T): T {
        adjustRunning(+1)
        try {
            return block()
        } finally {
            adjustRunning(-1)
        }
    }

    private fun adjustRunning(delta: Int) = synchronized(this) {
        running += delta
        _busy.value = running > 0
    }

    companion object {
        const val STARTER_ASSET = "tunes/oneills.abc"

        fun readStarterSet(context: Context): String =
            context.assets.open(STARTER_ASSET).bufferedReader().use { it.readText() }
    }
}

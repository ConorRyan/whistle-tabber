package io.github.conorryan.whistletabber.data.tunes

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Files and text another app handed us through "Open with" or "Share". */
data class SharedTunes(val uris: List<Uri>, val text: String?) {
    companion object {
        /**
         * What [intent] carries: VIEW's data, SEND's stream (else its text), or
         * SEND_MULTIPLE's streams. Only `content:` URIs are kept; null when
         * there is nothing to import, as for a launcher intent.
         */
        fun from(intent: Intent): SharedTunes? {
            val uris: List<Uri>
            var text: String? = null
            when (intent.action) {
                Intent.ACTION_VIEW -> uris = listOfNotNull(intent.data)
                Intent.ACTION_SEND -> {
                    uris = listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                    if (uris.isEmpty()) text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                }
                Intent.ACTION_SEND_MULTIPLE ->
                    uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else -> return null
            }
            val content = uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT }
            return SharedTunes(content, text?.takeIf { it.isNotBlank() })
                .takeIf { content.isNotEmpty() || it.text != null }
        }
    }
}

/**
 * Imports what other apps share with us. [receive] signals [arrivals] (the
 * app goes to the library), runs the import on [scope] (the URI grants last
 * while the activity lives, so it starts at once) and puts the outcome on
 * [result]; the library shows it and calls [consume]. A newer result
 * replaces an unshown one.
 */
class IncomingTunes(
    private val importer: TuneImporter,
    private val scope: CoroutineScope,
) {
    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when something arrives; lost when nobody listens, as before the UI is up. */
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    private val _result = MutableStateFlow<ImportResult?>(null)

    /** The outcome of the latest incoming import, until the library has shown it. */
    val result: StateFlow<ImportResult?> = _result.asStateFlow()

    fun receive(shared: SharedTunes) {
        _arrivals.tryEmit(Unit)
        scope.launch {
            val files = if (shared.uris.isEmpty()) ImportResult.NONE else importer.importFiles(shared.uris)
            _result.value = files + (shared.text?.let { importer.importText(it) } ?: ImportResult.NONE)
        }
    }

    /** [result] has been shown. */
    fun consume(result: ImportResult) {
        _result.compareAndSet(result, null)
    }
}

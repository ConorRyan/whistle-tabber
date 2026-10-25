package io.github.conorryan.whistletabber.ui.tune

import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.KeyChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The key-change preview: which tonic is being previewed, and the rewritten
 * ABC once the page has answered. The rewriting itself comes from
 * [transpose] (the tune renderer's round trip to the page); this module owns
 * the bookkeeping and the copy's title.
 */
class KeyChangePreview(private val scope: CoroutineScope) {

    /** A key change being previewed; [abc] is null until the rewrite arrives. */
    data class Preview(val tonic: String, val keyName: String, val steps: Int, val abc: String? = null)

    private val _preview = MutableStateFlow<Preview?>(null)
    val preview: StateFlow<Preview?> = _preview.asStateFlow()

    /**
     * Starts previewing [tune] rewritten into [tonic]. Picking the tune's own
     * key cancels; a key that can't be reached does nothing. The answer from
     * [transpose] fills [Preview.abc], unless a newer preview superseded it.
     */
    fun start(tune: Tune, tonic: String, transpose: suspend (abc: String, steps: Int) -> String?) {
        val steps = KeyChange.steps(tune.key, tonic) ?: return
        if (steps == 0) {
            _preview.value = null
            return
        }
        _preview.value = Preview(tonic, KeyChange.keyName(tune.key, tonic), steps)
        scope.launch {
            val abc = transpose(tune.abc, steps)
            _preview.update { p ->
                if (p != null && p.steps == steps && p.abc == null && abc != null) p.copy(abc = abc) else p
            }
        }
    }

    fun cancel() {
        _preview.value = null
    }

    /** The copy to save: the rewritten ABC retitled "Title (Key)", or null while the rewrite is pending. */
    fun copyAbc(tune: Tune, preview: Preview): String? =
        preview.abc?.let { AbcBook.withTitle(it, KeyChange.copyTitle(tune.title, preview.keyName)) }
}

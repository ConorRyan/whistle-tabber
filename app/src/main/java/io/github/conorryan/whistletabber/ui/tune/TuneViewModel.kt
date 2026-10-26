package io.github.conorryan.whistletabber.ui.tune

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.TunePage
import io.github.conorryan.whistletabber.render.TuneRenderer
import io.github.conorryan.whistletabber.ui.settings.TabSettingsEditor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TuneUiState(
    val tune: Tune? = null,
    /** The tune is gone (deleted, or a stale id). */
    val missing: Boolean = false,
    val settings: TabSettings = TabSettings(),
    val preview: KeyChangePreview.Preview? = null,
) {
    /** The whistle the tune is written for: its directive, else its key's suggestion, else D. */
    val tuneWhistle: WhistleKey get() = tune?.whistle ?: WhistleKey.DEFAULT
}

sealed interface TuneEffect {
    data class OpenTune(val id: Long) : TuneEffect
    data object Close : TuneEffect
    data object Exported : TuneEffect
    data object ExportFailed : TuneEffect
}

/**
 * Drives one stored tune. The tune and its settings live here; the render
 * cycle belongs to the [TuneRenderer] this ViewModel owns, and the key-change
 * preview to its [KeyChangePreview]. The screen attaches its WebView with
 * [bindPage] and reads [renderState] for the playback bar.
 */
class TuneViewModel(
    private val id: Long,
    private val repository: TuneRepository,
    /** Writes from the tune screen's settings sheet; also the settings it shows. */
    val settingsEditor: TabSettingsEditor,
    private val files: AbcFiles,
    private val recentlyDeleted: RecentlyDeleted,
) : ViewModel() {

    private val renderer = TuneRenderer(viewModelScope)

    /** Page state for the playback bar: warnings, playing, bar, tempo, error. */
    val renderState: StateFlow<TuneRenderer.RendererState> = renderer.state

    fun bindPage(page: TunePage) = renderer.attach(page)

    private val _state = MutableStateFlow(TuneUiState())
    val state: StateFlow<TuneUiState> = _state.asStateFlow()

    private val _effects = Channel<TuneEffect>(Channel.BUFFERED)
    val effects: Flow<TuneEffect> = _effects.receiveAsFlow()

    private val keyChange = KeyChangePreview(viewModelScope)
    private var deleting = false

    init {
        viewModelScope.launch {
            combine(repository.observe(id), settingsEditor.settings, keyChange.preview, ::Triple).collect { (tune, settings, preview) ->
                if (tune == null) {
                    if (!deleting) _state.update { it.copy(missing = true) }
                    return@collect
                }
                _state.update { it.copy(tune = tune, settings = settings, preview = preview, missing = false) }
                val whistle = _state.value.tuneWhistle
                renderer.show(
                    TuneRenderer.RenderRequest(
                        abc = preview?.abc ?: tune.abc,
                        midiTranspose = settings.midiTranspose(whistle),
                        fingering = TuneRenderer.FingeringInputs(settings, whistle),
                    ),
                )
            }
        }
    }

    fun togglePlay() = renderer.togglePlay()

    fun restart() = renderer.restart()

    fun setTempo(percent: Int) = renderer.setTempo(percent)

    fun dismissError() = renderer.dismissError()

    /** Writes (or with null, removes) the tune's own whistle directive. */
    fun setTuneWhistle(whistle: WhistleKey?) = viewModelScope.launch { repository.setWhistle(id, whistle) }

    fun setStarred(starred: Boolean) = viewModelScope.launch { repository.setStarred(id, starred) }

    /** Previews the tune rewritten with [tonic] as its key note; the page does the rewriting. */
    fun previewKey(tonic: String) {
        val tune = _state.value.tune ?: return
        keyChange.start(tune, tonic, renderer::transpose)
    }

    fun cancelPreview() = keyChange.cancel()

    /** Saves the previewed key as a new tune titled e.g. "Banish Misfortune (G)" and opens it. */
    fun savePreviewAsCopy() {
        val tune = _state.value.tune ?: return
        val preview = keyChange.preview.value ?: return
        val abc = keyChange.copyAbc(tune, preview) ?: return
        viewModelScope.launch {
            val newId = repository.save(abc)
            keyChange.cancel()
            _effects.send(TuneEffect.OpenTune(newId))
        }
    }

    /** Replaces this tune's ABC with the previewed key. */
    fun replaceWithPreview() {
        val abc = keyChange.preview.value?.abc ?: return
        viewModelScope.launch {
            repository.save(abc, id)
            keyChange.cancel()
        }
    }

    /** Deletes the tune and closes the screen; the library offers Undo. */
    fun delete() {
        deleting = true
        viewModelScope.launch {
            recentlyDeleted.delete(id)
            _effects.send(TuneEffect.Close)
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                files.write(uri, repository.export(id) ?: error("Tune $id is gone"))
            }.isSuccess
            _effects.send(if (ok) TuneEffect.Exported else TuneEffect.ExportFailed)
        }
    }
}

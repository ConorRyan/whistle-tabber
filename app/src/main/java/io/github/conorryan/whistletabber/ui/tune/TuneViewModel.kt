package io.github.conorryan.whistletabber.ui.tune

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.AbcFiles
import io.github.conorryan.whistletabber.data.tunes.RecentlyDeleted
import io.github.conorryan.whistletabber.data.tunes.Tune
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.AbcBook
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.KeyChange
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.DiagramSpec
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A key change being previewed. [abc] is null until the page has rewritten
 * the text.
 */
data class KeyPreview(val tonic: String, val keyName: String, val steps: Int, val abc: String? = null)

data class TuneUiState(
    val tune: Tune? = null,
    /** The tune is gone (deleted, or a stale id). */
    val missing: Boolean = false,
    val settings: TabSettings = TabSettings(),
    val warnings: List<String> = emptyList(),
    val playing: Boolean = false,
    /** Zero-based bar being played, if any. */
    val bar: Int? = null,
    val tempo: Int = 100,
    val preview: KeyPreview? = null,
    val error: String? = null,
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
 * Drives one stored tune in the WebView. Renders with the playback transpose
 * the settings imply and turns each `rendered` event into fingering diagrams.
 * A settings change only re-sends diagrams, unless the text or the playback
 * transpose changed, which needs a fresh render (and synth).
 *
 * The screen forwards [commands] to the WebView and page events to [onEvent].
 */
class TuneViewModel(
    private val id: Long,
    private val repository: TuneRepository,
    private val store: TabSettingsStore,
    private val files: AbcFiles,
    private val recentlyDeleted: RecentlyDeleted,
) : ViewModel() {

    private val _state = MutableStateFlow(TuneUiState())
    val state: StateFlow<TuneUiState> = _state.asStateFlow()

    private val _commands = Channel<BridgeCommand>(Channel.UNLIMITED)
    val commands: Flow<BridgeCommand> = _commands.receiveAsFlow()

    private val _effects = Channel<TuneEffect>(Channel.BUFFERED)
    val effects: Flow<TuneEffect> = _effects.receiveAsFlow()

    private val preview = MutableStateFlow<KeyPreview?>(null)
    private var notes: List<NoteEvent>? = null
    private var rendered: BridgeCommand.Render? = null
    private var deleting = false

    init {
        viewModelScope.launch {
            combine(repository.observe(id), store.settings, preview, ::Triple).collect { (tune, settings, preview) ->
                if (tune == null) {
                    if (!deleting) _state.update { it.copy(missing = true) }
                    return@collect
                }
                _state.update { it.copy(tune = tune, settings = settings, preview = preview, missing = false) }
                val abc = preview?.abc ?: tune.abc
                val render = BridgeCommand.Render(abc, midiTranspose = settings.midiTranspose(_state.value.tuneWhistle))
                if (render != rendered) {
                    rendered = render
                    notes = null
                    _state.update { it.copy(playing = false, bar = null) }
                    _commands.send(render)
                } else {
                    sendDiagrams()
                }
            }
        }
    }

    fun onEvent(event: BridgeEvent) {
        when (event) {
            is BridgeEvent.Rendered -> {
                notes = event.notes
                _state.update { it.copy(warnings = event.warnings) }
                sendDiagrams()
            }
            is BridgeEvent.Playing -> _state.update { it.copy(playing = true, bar = event.measure) }
            BridgeEvent.Finished -> _state.update { it.copy(playing = false, bar = null) }
            is BridgeEvent.TransposedText -> preview.update { p ->
                if (p != null && p.steps == event.steps && p.abc == null) p.copy(abc = event.abc) else p
            }
            is BridgeEvent.Error -> _state.update { it.copy(error = event.message) }
            is BridgeEvent.Ready, is BridgeEvent.AudioLoaded -> Unit
        }
    }

    fun togglePlay() {
        if (_state.value.playing) {
            send(BridgeCommand.Pause)
            _state.update { it.copy(playing = false) }
        } else {
            send(BridgeCommand.Play)
            _state.update { it.copy(playing = true) }
        }
    }

    fun restart() = send(BridgeCommand.Seek(0.0))

    fun setTempo(percent: Int) {
        _state.update { it.copy(tempo = percent) }
        send(BridgeCommand.Tempo(percent))
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Writes (or with null, removes) the tune's own whistle directive. */
    fun setTuneWhistle(whistle: WhistleKey?) = viewModelScope.launch { repository.setWhistle(id, whistle) }

    fun setStarred(starred: Boolean) = viewModelScope.launch { repository.setStarred(id, starred) }

    fun setMyWhistle(whistle: WhistleKey) = viewModelScope.launch { store.setWhistle(whistle) }
    fun setCNatural(cNatural: CNatural) = viewModelScope.launch { store.setCNatural(cNatural) }
    fun setFoldOutOfRange(fold: Boolean) = viewModelScope.launch { store.setFoldOutOfRange(fold) }
    fun setMode(mode: TransposeMode) = viewModelScope.launch { store.setMode(mode) }

    /** Previews the tune rewritten with [tonic] as its key note; the page does the rewriting. */
    fun previewKey(tonic: String) {
        val tune = _state.value.tune ?: return
        val steps = KeyChange.steps(tune.key, tonic) ?: return
        if (steps == 0) {
            preview.value = null
            return
        }
        preview.value = KeyPreview(tonic, KeyChange.keyName(tune.key, tonic), steps)
        send(BridgeCommand.TransposeText(tune.abc, steps))
    }

    fun cancelPreview() {
        preview.value = null
    }

    /** Saves the previewed key as a new tune titled e.g. "Banish Misfortune (G)" and opens it. */
    fun savePreviewAsCopy() {
        val tune = _state.value.tune ?: return
        val p = preview.value ?: return
        val abc = p.abc ?: return
        viewModelScope.launch {
            val newId = repository.save(AbcBook.withTitle(abc, KeyChange.copyTitle(tune.title, p.keyName)))
            preview.value = null
            _effects.send(TuneEffect.OpenTune(newId))
        }
    }

    /** Replaces this tune's ABC with the previewed key. */
    fun replaceWithPreview() {
        val abc = preview.value?.abc ?: return
        viewModelScope.launch {
            repository.save(abc, id)
            preview.value = null
        }
    }

    /** Deletes the tune and closes the screen; the library offers Undo. */
    fun delete() {
        deleting = true
        viewModelScope.launch {
            val tune = repository.delete(id)
            if (tune != null) recentlyDeleted.put(tune)
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

    private fun send(command: BridgeCommand) {
        _commands.trySend(command)
    }

    private fun sendDiagrams() {
        val current = notes ?: return
        val s = _state.value
        val fingerings = FingeringEngine.forNotes(current, s.settings, s.tuneWhistle)
        send(BridgeCommand.Diagrams(fingerings.map(DiagramSpec::from)))
    }
}

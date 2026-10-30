package io.github.conorryan.whistletabber.ui.tap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.render.TunePage
import io.github.conorryan.whistletabber.render.TuneRenderer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TapEditorUiState(
    /** Null while loading. */
    val draft: TapDraft? = null,
    /** Where the next tap goes: 0 before the first note, `notes.size` at the end. */
    val cursor: Int = 0,
    /** The palette's octave: 0 bottom, 1 "+", 2 "++". */
    val octave: Int = 0,
    val settings: TabSettings = TabSettings(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The tune no longer exists. */
    val missing: Boolean = false,
    /** The tune isn't one [TapTune] can read (it was changed as text). */
    val unsupported: Boolean = false,
) {
    val canSave: Boolean get() = draft != null && draft.title.isNotBlank() && draft.notes.any { it != null } && !saving
}

sealed interface TapEditorEffect {
    data class Saved(val id: Long) : TapEditorEffect
}

/**
 * Writes a tune by tapping fingerings ([id] null: a new tune for my whistle;
 * else a stored tune [TapTune] can read). The draft lives in
 * [SavedStateHandle]; the preview, with its diagrams, is rendered by the
 * [TuneRenderer] this ViewModel owns. The screen attaches its WebView with
 * [bindPage].
 */
class TapEditorViewModel(
    private val id: Long?,
    private val savedState: SavedStateHandle,
    private val repository: TuneRepository,
    private val settings: Flow<TabSettings>,
    debounceMs: Long = 300,
) : ViewModel() {

    private val renderer = TuneRenderer(viewModelScope, debounceMs)

    /** Playback and page errors for the preview. */
    val renderState: StateFlow<TuneRenderer.RendererState> = renderer.state

    fun bindPage(page: TunePage) = renderer.attach(page)

    private val _state = MutableStateFlow(TapEditorUiState())
    val state: StateFlow<TapEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<TapEditorEffect>(Channel.BUFFERED)
    val effects: Flow<TapEditorEffect> = _effects.receiveAsFlow()

    private var original: TapDraft? = null

    init {
        viewModelScope.launch {
            val s = settings.first()
            val loaded = if (id == null) {
                TapDraft("", s.whistle, emptyList())
            } else {
                val abc = repository.get(id)?.abc
                if (abc == null) {
                    _state.update { it.copy(missing = true) }
                    return@launch
                }
                TapTune.parse(abc) ?: run {
                    _state.update { it.copy(unsupported = true) }
                    return@launch
                }
            }
            original = loaded
            val draft = restore(loaded)
            val cursor = (savedState.get<Int>(KEY_CURSOR) ?: draft.notes.size).coerceIn(0, draft.notes.size)
            _state.update { it.copy(settings = s, cursor = cursor) }
            edit(draft, cursor)
        }
    }

    /** Inserts the fingering [offset] semitones above the whistle's bottom note at the cursor. */
    fun tap(offset: Int) {
        // Which offsets have a fingering doesn't depend on the C natural choice.
        if (FingeringEngine.fingeringAt(offset, CNatural.DEFAULT) == null) return
        insert(offset)
    }

    fun rest() = insert(null)

    /** Removes the note before the cursor. */
    fun deleteBack() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.cursor == 0) return
        edit(draft.copy(notes = draft.notes.toMutableList().apply { removeAt(s.cursor - 1) }), s.cursor - 1)
    }

    fun moveCursor(position: Int) {
        val draft = _state.value.draft ?: return
        val cursor = position.coerceIn(0, draft.notes.size)
        savedState[KEY_CURSOR] = cursor
        _state.update { it.copy(cursor = cursor) }
    }

    fun setOctave(octave: Int) = _state.update { it.copy(octave = octave.coerceIn(0, 2)) }

    fun setTitle(title: String) {
        val s = _state.value
        edit(s.draft?.copy(title = title) ?: return, s.cursor)
    }

    /** Keeps the fingerings; the tune's key and pitches follow the whistle. */
    fun setWhistle(whistle: WhistleKey) {
        val s = _state.value
        edit(s.draft?.copy(whistle = whistle) ?: return, s.cursor)
    }

    fun togglePlay() = renderer.togglePlay()

    fun restart() = renderer.restart()

    fun dismissError() = renderer.dismissError()

    fun save() {
        val s = _state.value
        val draft = s.draft ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val savedId = repository.save(TapTune.toAbc(draft), id)
            original = draft
            listOf(KEY_NOTES, KEY_TITLE, KEY_WHISTLE, KEY_CURSOR).forEach { savedState.remove<Any>(it) }
            _state.update { it.copy(saving = false, dirty = false) }
            _effects.send(TapEditorEffect.Saved(savedId))
        }
    }

    private fun insert(offset: Int?) {
        val s = _state.value
        val draft = s.draft ?: return
        edit(draft.copy(notes = draft.notes.toMutableList().apply { add(s.cursor, offset) }), s.cursor + 1)
    }

    private fun edit(draft: TapDraft, cursor: Int) {
        savedState[KEY_NOTES] = draft.notes.map { it ?: REST }.toIntArray()
        savedState[KEY_TITLE] = draft.title
        savedState[KEY_WHISTLE] = draft.whistle.name
        savedState[KEY_CURSOR] = cursor
        _state.update { it.copy(draft = draft, cursor = cursor, dirty = draft != original) }
        // Diagrams show exactly what was tapped: the tune's own whistle, its own pitches.
        val shown = _state.value.settings.copy(whistle = draft.whistle, mode = TransposeMode.SAME_PITCHES, foldOutOfRange = false)
        renderer.show(
            TuneRenderer.RenderRequest(
                abc = TapTune.toAbc(draft),
                midiTranspose = shown.midiTranspose(draft.whistle),
                fingering = TuneRenderer.FingeringInputs(shown, draft.whistle),
            ),
        )
    }

    private fun restore(loaded: TapDraft): TapDraft {
        val notes = savedState.get<IntArray>(KEY_NOTES) ?: return loaded
        return loaded.copy(
            title = savedState.get<String>(KEY_TITLE) ?: loaded.title,
            whistle = savedState.get<String>(KEY_WHISTLE)?.let { name -> WhistleKey.entries.firstOrNull { it.name == name } } ?: loaded.whistle,
            notes = notes.map { it.takeIf { n -> n != REST } },
        )
    }

    companion object {
        private const val KEY_NOTES = "tapNotes"
        private const val KEY_TITLE = "tapTitle"
        private const val KEY_WHISTLE = "tapWhistle"
        private const val KEY_CURSOR = "tapCursor"
        private const val REST = -1
    }
}

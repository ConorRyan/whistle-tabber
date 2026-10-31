package io.github.conorryan.whistletabber.ui.tap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.RhythmGrid
import io.github.conorryan.whistletabber.music.RhythmSetup
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTiming
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
    /** The rhythm tapper, shown in place of the palette; null when closed. */
    val rhythm: RhythmPanel? = null,
) {
    /** The notes have a tapped rhythm: the preview and a save use it. */
    val timed: Boolean get() = draft?.timing != null

    val canSave: Boolean get() = draft != null && draft.title.isNotBlank() && draft.notes.any { it != null } && !saving
}

enum class RhythmPhase { SETUP, TAPPING, DONE }

/** The rhythm tapper: its [setup] and [padSound], and while tapping the [next] of [total] sounding notes. */
data class RhythmPanel(
    val setup: RhythmSetup,
    val padSound: PadSound = PadSound.NOTE,
    val phase: RhythmPhase = RhythmPhase.SETUP,
    val next: Int = 0,
    val total: Int = 0,
)

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

    /** The draft as it was when the rhythm tapper opened; Cancel and Try again go back to it. */
    private var draftBeforeRhythm: TapDraft? = null
    private var session: RhythmSession? = null

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

    /** Removes the note before the cursor, with its length. */
    fun deleteBack() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.cursor == 0) return
        val (removed, cursor) = draft.removed(s.cursor - 1)
        edit(removed, cursor)
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

    /** Opens the rhythm tapper, if there are notes to tap. */
    fun openRhythm() {
        val draft = _state.value.draft ?: return
        val total = draft.notes.count { it != null }
        if (total == 0) return
        draftBeforeRhythm = draft
        val pad = savedState.get<String>(KEY_PAD)?.let { n -> PadSound.entries.firstOrNull { it.name == n } } ?: PadSound.NOTE
        _state.update { it.copy(rhythm = RhythmPanel(setup(), pad, total = total)) }
    }

    fun setRhythmSetup(setup: RhythmSetup) {
        val panel = _state.value.rhythm?.takeIf { it.phase == RhythmPhase.SETUP } ?: return
        val clamped = setup.copy(bpm = setup.bpm.coerceIn(RhythmSetup.MIN_BPM, RhythmSetup.MAX_BPM))
        savedState[KEY_BPM] = clamped.bpm
        savedState[KEY_METER] = clamped.meter.name
        savedState[KEY_GRID] = clamped.grid.name
        _state.update { it.copy(rhythm = panel.copy(setup = clamped)) }
    }

    fun setPadSound(sound: PadSound) {
        val panel = _state.value.rhythm?.takeIf { it.phase == RhythmPhase.SETUP } ?: return
        savedState[KEY_PAD] = sound.name
        _state.update { it.copy(rhythm = panel.copy(padSound = sound)) }
    }

    /** The screen starts the metronome; presses are in ms from its first downbeat after the count-in. */
    fun startTapping() {
        val panel = _state.value.rhythm ?: return
        session = RhythmSession(panel.setup, panel.total)
        _state.update { it.copy(rhythm = panel.copy(phase = RhythmPhase.TAPPING, next = 0)) }
    }

    /**
     * A press on the pad: returns the offset of the note it plays, or null.
     * A press while another is held (the next finger landing first) ends
     * that one here.
     */
    fun press(ms: Long): Int? {
        val s = tappingSession() ?: return null
        val index = s.press(ms)
        afterTap(s)
        return index?.let { i -> _state.value.draft?.notes?.filterNotNull()?.getOrNull(i) }
    }

    fun release(ms: Long) {
        val s = tappingSession() ?: return
        s.release(ms)
        afterTap(s)
    }

    private fun tappingSession(): RhythmSession? = session.takeIf { _state.value.rhythm?.phase == RhythmPhase.TAPPING }

    /** Shows the session's progress, and finishes it once every note is tapped. */
    private fun afterTap(s: RhythmSession) {
        _state.update { it.copy(rhythm = it.rhythm?.copy(next = s.next)) }
        if (s.finished) finishTapping()
    }

    /** Ends tapping early: the notes not reached get a beat each. With nothing tapped, back to the setup. */
    fun stopTapping() {
        val panel = _state.value.rhythm?.takeIf { it.phase == RhythmPhase.TAPPING } ?: return
        val s = session ?: return
        s.cancelHeld()
        if (s.empty) {
            _state.update { it.copy(rhythm = panel.copy(phase = RhythmPhase.SETUP)) }
        } else {
            finishTapping()
        }
    }

    /** Back to the setup, with the draft as it was before the tapper opened. */
    fun retryRhythm() {
        val panel = _state.value.rhythm ?: return
        session = null
        val draft = draftBeforeRhythm
        if (draft != null) edit(draft, _state.value.cursor.coerceIn(0, draft.notes.size))
        _state.update { it.copy(rhythm = panel.copy(phase = RhythmPhase.SETUP, next = 0)) }
    }

    /** Closes the tapper, keeping the rhythm just tapped (now part of the draft). */
    fun keepRhythm() {
        if (_state.value.rhythm?.phase != RhythmPhase.DONE) return
        draftBeforeRhythm = null
        session = null
        _state.update { it.copy(rhythm = null) }
    }

    /** Closes the tapper as it was before it opened. */
    fun closeRhythm() {
        session = null
        val draft = draftBeforeRhythm
        draftBeforeRhythm = null
        _state.update { it.copy(rhythm = null) }
        if (draft != null) edit(draft, _state.value.cursor.coerceIn(0, draft.notes.size))
    }

    /** Drops the tapped rhythm: every note is an equal quarter again. */
    fun removeRhythm() {
        val s = _state.value
        val draft = s.draft ?: return
        if (draft.timing == null) return
        edit(draft.copy(timing = null), s.cursor)
    }

    private fun finishTapping() {
        val panel = _state.value.rhythm ?: return
        val s = session ?: return
        val draft = _state.value.draft ?: return
        val applied = draft.withRhythm(s.rhythm(), panel.setup)
        _state.update { it.copy(rhythm = panel.copy(phase = RhythmPhase.DONE)) }
        edit(applied, _state.value.cursor.coerceIn(0, applied.notes.size))
    }

    private fun setup(): RhythmSetup {
        val d = RhythmSetup()
        return RhythmSetup(
            bpm = savedState.get<Int>(KEY_BPM) ?: d.bpm,
            meter = savedState.get<String>(KEY_METER)?.let { n -> Meter.entries.firstOrNull { it.name == n } } ?: d.meter,
            grid = savedState.get<String>(KEY_GRID)?.let { n -> RhythmGrid.entries.firstOrNull { it.name == n } } ?: d.grid,
        )
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
            listOf(KEY_NOTES, KEY_TITLE, KEY_WHISTLE, KEY_CURSOR, KEY_LENGTHS, KEY_FIRST_BAR).forEach { savedState.remove<Any>(it) }
            _state.update { it.copy(saving = false, dirty = false) }
            _effects.send(TapEditorEffect.Saved(savedId))
        }
    }

    private fun insert(offset: Int?) {
        val s = _state.value
        val draft = s.draft ?: return
        val (inserted, cursor) = draft.inserted(s.cursor, offset)
        edit(inserted, cursor)
    }

    /** Shows [draft]; it carries its own rhythm, saved to [savedState] with the notes. */
    private fun edit(draft: TapDraft, cursor: Int) {
        savedState[KEY_NOTES] = draft.notes.map { it ?: REST }.toIntArray()
        savedState[KEY_TITLE] = draft.title
        savedState[KEY_WHISTLE] = draft.whistle.name
        savedState[KEY_CURSOR] = cursor
        val timing = draft.timing
        if (timing != null && timing.lengths.size == draft.notes.size && timing.lengths.all { it > 0 }) {
            savedState[KEY_LENGTHS] = timing.lengths.toIntArray()
            savedState[KEY_FIRST_BAR] = timing.firstBarUnits
        } else {
            savedState.remove<IntArray>(KEY_LENGTHS)
            savedState.remove<Int>(KEY_FIRST_BAR)
        }
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
        val draft = loaded.copy(
            title = savedState.get<String>(KEY_TITLE) ?: loaded.title,
            whistle = savedState.get<String>(KEY_WHISTLE)?.let { name -> WhistleKey.entries.firstOrNull { it.name == name } } ?: loaded.whistle,
            notes = notes.map { it.takeIf { n -> n != REST } },
        )
        val lengths = savedState.get<IntArray>(KEY_LENGTHS)
        val firstBar = savedState.get<Int>(KEY_FIRST_BAR)
        val timing = if (lengths != null && firstBar != null && lengths.size == draft.notes.size && lengths.all { it > 0 }) {
            TapTiming(setup(), lengths.toList(), firstBar)
        } else {
            null
        }
        return draft.copy(timing = timing).normalized()
    }

    companion object {
        private const val KEY_NOTES = "tapNotes"
        private const val KEY_TITLE = "tapTitle"
        private const val KEY_WHISTLE = "tapWhistle"
        private const val KEY_CURSOR = "tapCursor"
        private const val KEY_LENGTHS = "tapTimingLengths"
        private const val KEY_FIRST_BAR = "tapTimingFirstBar"
        private const val KEY_BPM = "rhythmBpm"
        private const val KEY_METER = "rhythmMeter"
        private const val KEY_GRID = "rhythmGrid"
        private const val KEY_PAD = "rhythmPadSound"
        private const val REST = -1
    }
}

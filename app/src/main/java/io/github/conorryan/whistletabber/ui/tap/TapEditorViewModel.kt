package io.github.conorryan.whistletabber.ui.tap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.data.tunes.TuneRepository
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.Loss
import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.RhythmGrid
import io.github.conorryan.whistletabber.music.RhythmSetup
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.music.WhistleKey
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
    /** Where the next tap goes, a slot of the draft ([TapDraft.slotOf]): 0 before everything. */
    val cursor: Int = 0,
    /** The palette's octave: 0 bottom, 1 "+", 2 "++". */
    val octave: Int = 0,
    val settings: TabSettings = TabSettings(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The tune no longer exists. */
    val missing: Boolean = false,
    /** The tune has no key or whistle [TapTune] can read. */
    val unsupported: Boolean = false,
    /**
     * What reading the stored tune couldn't keep; when there is anything,
     * the draft is a converted copy and saving adds a new tune.
     */
    val losses: Set<Loss> = emptySet(),
    /** The rhythm tapper, shown in place of the palette; null when closed. */
    val rhythm: RhythmPanel? = null,
) {
    /** The notes have a tapped rhythm: a save writes it. */
    val timed: Boolean get() = draft?.timing != null

    val canSave: Boolean get() = draft != null && draft.title.isNotBlank() && draft.notes.any { it != null } && !saving

    /** Saving adds a new tune instead of replacing the one opened. */
    val asCopy: Boolean get() = losses.isNotEmpty()
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
 * else a stored tune, read by [TapTune.read]: one read without loss is
 * edited in place, any other is a converted copy, saved as a new tune). The
 * draft lives in [SavedStateHandle] as ABC; the strip of tapped diagrams is
 * its whole display.
 */
class TapEditorViewModel(
    private val id: Long?,
    private val savedState: SavedStateHandle,
    private val repository: TuneRepository,
    private val settings: Flow<TabSettings>,
) : ViewModel() {

    private val _state = MutableStateFlow(TapEditorUiState())
    val state: StateFlow<TapEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<TapEditorEffect>(Channel.BUFFERED)
    val effects: Flow<TapEditorEffect> = _effects.receiveAsFlow()

    private var original: TapDraft? = null
    private var losses: Set<Loss> = emptySet()

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
                val read = TapTune.read(abc) ?: run {
                    _state.update { it.copy(unsupported = true) }
                    return@launch
                }
                losses = read.losses
                read.draft
            }
            original = loaded.takeIf { losses.isEmpty() }
            val draft = restore(loaded)
            val cursor = (savedState.get<Int>(KEY_CURSOR) ?: draft.endSlot).coerceIn(0, draft.lastSlot)
            _state.update { it.copy(settings = s, cursor = cursor, losses = losses) }
            edit(draft, cursor)
        }
    }

    /** Inserts the fingering [offset] semitones above the whistle's bottom note at the cursor. */
    fun tap(offset: Int) {
        // Which offsets have a fingering doesn't depend on the C natural choice.
        if (FingeringEngine.fingeringAt(offset) == null) return
        insert(offset)
    }

    fun rest() = insert(null)

    /** Removes the note before the cursor, with its length; bar lines and repeats stay. */
    fun deleteBack() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.cursor == 0) return
        val (removed, cursor) = draft.removedBefore(s.cursor)
        edit(removed, cursor)
    }

    fun moveCursor(position: Int) {
        val draft = _state.value.draft ?: return
        val cursor = position.coerceIn(0, draft.lastSlot)
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
        edit(s.draft?.withWhistle(whistle) ?: return, s.cursor)
    }

    /** Opens the rhythm tapper, if there are notes to tap. */
    fun openRhythm() {
        val draft = _state.value.draft ?: return
        val total = draft.notes.count { it != null }
        if (total == 0) return
        draftBeforeRhythm = draft
        val pad = savedState.get<String>(KEY_PAD)?.let { n -> PadSound.entries.firstOrNull { it.name == n } } ?: PadSound.NOTE
        _state.update { it.copy(rhythm = RhythmPanel(setup(draft), pad, total = total)) }
    }

    fun setRhythmSetup(setup: RhythmSetup) {
        val panel = _state.value.rhythm?.takeIf { it.phase == RhythmPhase.SETUP } ?: return
        val clamped = setup.copy(bpm = setup.bpm.coerceIn(RhythmSetup.MIN_BPM, RhythmSetup.MAX_BPM))
        savedState[KEY_BPM] = clamped.bpm
        savedState[KEY_METER] = clamped.meter.name
        savedState[KEY_GRID] = clamped.grid.name
        savedState[KEY_TRIPLETS] = clamped.triplets
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
        session = RhythmSession(panel.setup, panel.total, _state.value.draft?.let(TapTune::breaks).orEmpty())
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
        if (draft != null) edit(draft, _state.value.cursor.coerceIn(0, draft.lastSlot))
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
        if (draft != null) edit(draft, _state.value.cursor.coerceIn(0, draft.lastSlot))
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
        edit(applied, _state.value.cursor.coerceIn(0, applied.lastSlot))
    }

    /** The tapper's setup: as last chosen on this screen, else the tune's own meter, tempo and grid. */
    private fun setup(draft: TapDraft?): RhythmSetup {
        val d = draft?.timing?.setup ?: RhythmSetup()
        return RhythmSetup(
            bpm = savedState.get<Int>(KEY_BPM) ?: d.bpm,
            meter = savedState.get<String>(KEY_METER)?.let { n -> Meter.entries.firstOrNull { it.name == n } } ?: d.meter,
            grid = savedState.get<String>(KEY_GRID)?.let { n -> RhythmGrid.entries.firstOrNull { it.name == n } } ?: d.grid,
            triplets = savedState.get<Boolean>(KEY_TRIPLETS) ?: d.triplets,
        )
    }

    fun save() {
        val s = _state.value
        val draft = s.draft ?: return
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val savedId = repository.save(TapTune.toAbc(draft), id.takeIf { !s.asCopy })
            original = draft
            listOf(KEY_ABC, KEY_EMPTY, KEY_TITLE, KEY_CURSOR).forEach { savedState.remove<Any>(it) }
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

    /** Shows [draft]; it is kept in [savedState] as its ABC, which reads back as the same draft. */
    private fun edit(draft: TapDraft, cursor: Int) {
        savedState[KEY_ABC] = TapTune.toAbc(draft)
        savedState[KEY_EMPTY] = draft.notes.isEmpty()
        savedState[KEY_TITLE] = draft.title
        savedState[KEY_CURSOR] = cursor
        _state.update { it.copy(draft = draft, cursor = cursor, dirty = draft != original) }
    }

    private fun restore(loaded: TapDraft): TapDraft {
        val abc = savedState.get<String>(KEY_ABC) ?: return loaded
        val read = TapTune.read(abc)?.draft ?: return loaded
        val draft = read.copy(title = savedState.get<String>(KEY_TITLE) ?: read.title)
        if (savedState.get<Boolean>(KEY_EMPTY) != true) return draft
        return draft.copy(notes = emptyList(), timing = null, marks = emptyList(), anchors = draft.anchors.map { it.copy(gap = 0) })
    }

    companion object {
        private const val KEY_ABC = "tapAbc"
        private const val KEY_EMPTY = "tapEmpty"
        private const val KEY_TITLE = "tapTitle"
        private const val KEY_CURSOR = "tapCursor"
        private const val KEY_BPM = "rhythmBpm"
        private const val KEY_METER = "rhythmMeter"
        private const val KEY_GRID = "rhythmGrid"
        private const val KEY_TRIPLETS = "rhythmTriplets"
        private const val KEY_PAD = "rhythmPadSound"
    }
}

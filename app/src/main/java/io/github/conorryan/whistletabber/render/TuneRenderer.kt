package io.github.conorryan.whistletabber.render

import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.web.BridgeCommand
import io.github.conorryan.whistletabber.web.BridgeEvent
import io.github.conorryan.whistletabber.web.DiagramSpec
import io.github.conorryan.whistletabber.web.NoteEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns one page's render cycle: "the page shows this ABC with these
 * fingerings". Callers state what should be on the page ([show]) and read
 * [state]; render dedupe, the notes-to-diagrams pipeline, transpose
 * request/reply matching, debouncing, playback mirroring, warnings and errors
 * all stay in here, and the BridgeCommand/BridgeEvent vocabulary with them.
 *
 * The page is attached with [attach]; it is safe to [show] before that, and a
 * page attached later is shown the last request again (a fresh WebView starts
 * blank, and the renderer outlives it across configuration changes). The
 * renderer runs on [scope]; the screen, not the renderer, owns the WebView's
 * lifetime.
 */
@OptIn(FlowPreview::class)
class TuneRenderer(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 0,
) {

    /** What a render's fingerings come from: the caller's settings and the tune's own whistle. */
    data class FingeringInputs(val settings: TabSettings, val tuneWhistle: WhistleKey)

    /** What the page should show; [fingering] null draws no diagrams (the editor). */
    data class RenderRequest(
        val abc: String,
        val midiTranspose: Int = 0,
        val fingering: FingeringInputs? = null,
    )

    data class RendererState(
        val warnings: List<String> = emptyList(),
        val playing: Boolean = false,
        /** Zero-based bar being played, if any. */
        val bar: Int? = null,
        val tempo: Int = 100,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(RendererState())
    val state: StateFlow<RendererState> = _state.asStateFlow()

    private var page: TunePage? = null
    private var eventsJob: Job? = null

    /** The last request, whether or not it has reached a page yet. */
    private var shown: RenderRequest? = null
    private var notes: List<NoteEvent>? = null
    /** The (notes, fingerings) the diagrams on the page were drawn for. */
    private var drawnFor: Pair<List<NoteEvent>, FingeringInputs>? = null

    private val shows = Channel<RenderRequest>(Channel.UNLIMITED)

    /** The outstanding `transposeText` request, if any: its steps and the caller waiting. */
    private var pendingTranspose: Pair<Int, CompletableDeferred<String?>>? = null

    init {
        if (debounceMs > 0) scope.launch { shows.receiveAsFlow().debounce(debounceMs).collect(::apply) }
    }

    /**
     * Shows [request], [debounceMs] after the last show when one is set.
     * Same text and playback as before only re-draws diagrams (and only when
     * the fingerings changed); new text or transpose re-renders and stops
     * playback.
     */
    fun show(request: RenderRequest) {
        if (debounceMs == 0L) apply(request) else shows.trySend(request)
    }

    /**
     * Binds the page the renderer talks to. Re-callable: a fresh page (a new
     * WebView) replaces the old one and is shown the last request again. The
     * page's events are collected from here on, so no caller can miss them.
     */
    fun attach(page: TunePage) {
        eventsJob?.cancel()
        this.page = page
        eventsJob = scope.launch { page.events.collect(::onEvent) }
        val request = shown ?: return
        notes = null
        drawnFor = null
        _state.update { it.copy(playing = false, bar = null) }
        page.send(BridgeCommand.Render(request.abc, midiTranspose = request.midiTranspose))
    }

    /**
     * Asks the page to rewrite [abc] by [steps] semitones and suspends until
     * the reply. Null when a newer call superseded this one, the page errored,
     * or no page is attached. The reply is not rendered; the caller decides.
     */
    suspend fun transpose(abc: String, steps: Int): String? {
        val page = page ?: return null
        pendingTranspose?.second?.complete(null)
        val reply = CompletableDeferred<String?>()
        pendingTranspose = steps to reply
        page.send(BridgeCommand.TransposeText(abc, steps))
        return reply.await()
    }

    fun togglePlay() {
        if (_state.value.playing) {
            _state.update { it.copy(playing = false) }
            page?.send(BridgeCommand.Pause)
        } else {
            _state.update { it.copy(playing = true) }
            page?.send(BridgeCommand.Play)
        }
    }

    fun restart() {
        page?.send(BridgeCommand.Seek(0.0))
    }

    fun setTempo(percent: Int) {
        _state.update { it.copy(tempo = percent) }
        page?.send(BridgeCommand.Tempo(percent))
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun apply(request: RenderRequest) {
        val last = shown
        shown = request
        val page = page ?: return // held until a page is attached
        if (last != null && last.abc == request.abc && last.midiTranspose == request.midiTranspose) {
            sendDiagrams(request.fingering)
            return
        }
        notes = null
        drawnFor = null
        _state.update { it.copy(playing = false, bar = null) }
        page.send(BridgeCommand.Render(request.abc, midiTranspose = request.midiTranspose))
    }

    private fun onEvent(event: BridgeEvent) {
        when (event) {
            is BridgeEvent.Rendered -> {
                notes = event.notes
                _state.update { it.copy(warnings = event.warnings) }
                sendDiagrams(shown?.fingering)
            }
            is BridgeEvent.Playing -> _state.update { it.copy(playing = true, bar = event.measure) }
            BridgeEvent.Finished -> _state.update { it.copy(playing = false, bar = null) }
            is BridgeEvent.TransposedText -> {
                val pending = pendingTranspose ?: return
                if (pending.first != event.steps) return // a stale reply from an older request
                pendingTranspose = null
                pending.second.complete(event.abc)
            }
            is BridgeEvent.Error -> {
                _state.update { it.copy(error = event.message) }
                pendingTranspose?.second?.complete(null)
                pendingTranspose = null
            }
            is BridgeEvent.Ready, is BridgeEvent.AudioLoaded -> Unit
        }
    }

    private fun sendDiagrams(fingering: FingeringInputs?) {
        val current = notes ?: return
        if (fingering == null) return
        if (drawnFor != null && drawnFor == (current to fingering)) return
        drawnFor = current to fingering
        page?.send(
            BridgeCommand.Diagrams(
                FingeringEngine.forNotes(current, fingering.settings, fingering.tuneWhistle)
                    .map(DiagramSpec::from),
            ),
        )
    }
}

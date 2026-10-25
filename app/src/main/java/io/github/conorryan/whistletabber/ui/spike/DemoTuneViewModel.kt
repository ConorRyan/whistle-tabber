package io.github.conorryan.whistletabber.ui.spike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.FingeringEngine
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DemoTuneUiState(
    val settings: TabSettings = TabSettings(),
    val status: String = "",
)

/**
 * Drives one tune in the WebView: renders it with the playback transpose the
 * settings imply, and turns each `rendered` event into fingering diagrams.
 * A settings change only re-sends diagrams, unless the playback transpose
 * changed, which needs a fresh render (and synth).
 *
 * The screen forwards [commands] to the WebView and page events to [onEvent].
 */
class DemoTuneViewModel(
    private val store: TabSettingsStore,
    private val abc: String,
    private val tuneWhistle: WhistleKey = WhistleKey.D,
) : ViewModel() {

    private val _state = MutableStateFlow(DemoTuneUiState())
    val state: StateFlow<DemoTuneUiState> = _state.asStateFlow()

    private val _commands = Channel<BridgeCommand>(Channel.UNLIMITED)
    val commands: Flow<BridgeCommand> = _commands.receiveAsFlow()

    private var notes: List<NoteEvent>? = null
    private var renderedTranspose: Int? = null

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                val transpose = settings.midiTranspose(tuneWhistle)
                if (transpose != renderedTranspose) {
                    renderedTranspose = transpose
                    notes = null
                    _commands.send(BridgeCommand.Render(abc, midiTranspose = transpose))
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
                sendDiagrams()
            }
            else -> Unit
        }
        _state.update { it.copy(status = statusFor(event) ?: it.status) }
    }

    fun play() = send(BridgeCommand.Play)
    fun pause() = send(BridgeCommand.Pause)
    fun restart() = send(BridgeCommand.Seek(0.0))
    fun setTempo(percent: Int) = send(BridgeCommand.Tempo(percent))

    fun setWhistle(whistle: WhistleKey) = viewModelScope.launch { store.setWhistle(whistle) }
    fun setCNatural(cNatural: CNatural) = viewModelScope.launch { store.setCNatural(cNatural) }
    fun setFoldOutOfRange(fold: Boolean) = viewModelScope.launch { store.setFoldOutOfRange(fold) }
    fun setMode(mode: TransposeMode) = viewModelScope.launch { store.setMode(mode) }

    private fun send(command: BridgeCommand) {
        _commands.trySend(command)
    }

    private fun sendDiagrams() {
        val current = notes ?: return
        val fingerings = FingeringEngine.forNotes(current, _state.value.settings, tuneWhistle)
        send(BridgeCommand.Diagrams(fingerings.map(DiagramSpec::from)))
    }

    private fun statusFor(event: BridgeEvent): String? = when (event) {
        is BridgeEvent.Ready -> null
        is BridgeEvent.Rendered -> "${event.notes.size} notes, ${event.warnings.size} warnings"
        is BridgeEvent.AudioLoaded -> if (event.errors.isEmpty()) null else "Sound errors: ${event.errors.size}"
        is BridgeEvent.Playing -> "Bar ${event.measure?.plus(1) ?: "?"}"
        BridgeEvent.Finished -> "Finished"
        is BridgeEvent.Error -> "Error: ${event.message}"
    }
}

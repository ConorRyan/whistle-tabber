package io.github.conorryan.whistletabber.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.scan.GrayImage
import io.github.conorryan.whistletabber.scan.TabReader
import io.github.conorryan.whistletabber.scan.TabReading
import io.github.conorryan.whistletabber.scan.TabTune
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanTabUiState(
    val image: GrayImage? = null,
    /** Null until an image has been read. */
    val reading: TabReading? = null,
    val busy: Boolean = false,
    /** The picked image could not be decoded. */
    val unreadable: Boolean = false,
    /** The whistle the tab is written for; starts as my whistle. */
    val whistle: WhistleKey = WhistleKey.DEFAULT,
    val title: String = "",
    val cNatural: CNatural = CNatural.DEFAULT,
) {
    val found: Int get() = reading?.columns?.size ?: 0
    val unknown: Int get() = reading?.columns?.count { TabTune.offsetOf(it, cNatural) == null } ?: 0
    val canOpen: Boolean get() = !busy && found > 0 && title.isNotBlank()

    /** The tune to open in the editor, or null before anything was read. */
    fun abc(): String? = reading?.takeIf { found > 0 }?.let { TabTune.toAbc(it, whistle, title.trim(), cNatural) }
}

/**
 * Reads whistle tab from a picked image ([TabReader]) and turns it into ABC
 * ([TabTune]) for the editor; nothing is saved here.
 */
class ScanTabViewModel(
    private val loadImage: suspend (Uri) -> GrayImage?,
    settings: Flow<TabSettings>,
    defaultTitle: String,
    private val readDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanTabUiState(title = defaultTitle))
    val state: StateFlow<ScanTabUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            val s = settings.first()
            _state.update { it.copy(whistle = s.whistle, cNatural = s.cNatural) }
        }
    }

    fun pick(uri: Uri) {
        job?.cancel()
        _state.update { it.copy(busy = true, unreadable = false, image = null, reading = null) }
        job = viewModelScope.launch {
            val image = loadImage(uri)
            if (image == null) {
                _state.update { it.copy(busy = false, unreadable = true) }
                return@launch
            }
            _state.update { it.copy(image = image) }
            val reading = withContext(readDispatcher) { TabReader.read(image) }
            _state.update { it.copy(busy = false, reading = reading) }
        }
    }

    fun setWhistle(whistle: WhistleKey) = _state.update { it.copy(whistle = whistle) }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }
}

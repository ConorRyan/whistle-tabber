package io.github.conorryan.whistletabber.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.music.FingeringChoices
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

/** One picked image and what was read from it, in pick order. */
data class ScannedImage(val image: GrayImage, val reading: TabReading)

data class ScanTabUiState(
    /** Every image picked so far, in order; their notes append. */
    val images: List<ScannedImage> = emptyList(),
    val busy: Boolean = false,
    /** At least one of the images just picked could not be decoded. */
    val unreadable: Boolean = false,
    /** The last paste found no image on the clipboard. */
    val pasteEmpty: Boolean = false,
    /** The whistle the tab is written for; starts as my whistle. */
    val whistle: WhistleKey = WhistleKey.DEFAULT,
    val title: String = "",
    val fingerings: FingeringChoices = FingeringChoices.DEFAULT,
) {
    val found: Int get() = images.sumOf { it.reading.columns.size }
    val unknown: Int get() = images.sumOf { r -> r.reading.columns.count { TabTune.offsetOf(it, fingerings) == null } }
    val canOpen: Boolean get() = !busy && found > 0 && title.isNotBlank()

    /** The tune to open in the editor, or null before anything was read. */
    fun abc(): String? = images.takeIf { found > 0 }?.let {
        TabTune.toAbc(TabTune.combine(it.map { s -> s.reading }), whistle, title.trim(), fingerings)
    }
}

/**
 * Reads whistle tab from picked images ([TabReader]) and turns it into ABC
 * ([TabTune]) for the editor; nothing is saved here. Notes from several
 * screenshots of one tab append, in pick order.
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
            _state.update { it.copy(whistle = s.whistle, fingerings = s.fingerings) }
        }
    }

    /** Appends the picked images' notes to what is already there, in order. */
    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        job?.cancel()
        _state.update { it.copy(busy = true, unreadable = false, pasteEmpty = false) }
        job = viewModelScope.launch {
            val scanned = uris.mapNotNull { uri ->
                loadImage(uri)?.let { image -> ScannedImage(image, withContext(readDispatcher) { TabReader.read(image) }) }
            }
            _state.update { s ->
                s.copy(
                    busy = false,
                    unreadable = scanned.size < uris.size,
                    images = s.images + scanned,
                )
            }
        }
    }

    /** A paste found no image on the clipboard. */
    fun pasteFailed() = _state.update { it.copy(busy = false, unreadable = false, pasteEmpty = true) }

    fun startOver() {
        job?.cancel()
        _state.update { it.copy(busy = false, unreadable = false, pasteEmpty = false, images = emptyList()) }
    }

    fun setWhistle(whistle: WhistleKey) = _state.update { it.copy(whistle = whistle) }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }
}

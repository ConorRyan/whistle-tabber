package io.github.conorryan.whistletabber.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.data.tunes.TuneImporter
import io.github.conorryan.whistletabber.music.TabSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tab settings (my whistle, C natural, folding, transpose mode) and the starter set. */
class SettingsViewModel(
    /** Writes from the settings screen; also the settings it shows. */
    val settingsEditor: TabSettingsEditor,
    private val importer: TuneImporter,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val settings: StateFlow<TabSettings> =
        settingsEditor.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabSettings())

    /** Any import is running, here or in the library. */
    val addingStarter: StateFlow<Boolean> = importer.busy

    private val _starterAdded = Channel<ImportResult>(Channel.BUFFERED)
    val starterAdded: Flow<ImportResult> = _starterAdded.receiveAsFlow()

    fun addStarterSet() {
        appScope.launch { _starterAdded.send(importer.addStarterSet()) }
    }
}

package io.github.conorryan.whistletabber.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.data.tunes.ImportResult
import io.github.conorryan.whistletabber.data.tunes.StarterTunes
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tab settings (my whistle, C natural, folding, transpose mode) and the starter set. */
class SettingsViewModel(
    private val store: TabSettingsStore,
    private val starterTunes: StarterTunes,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val settings: StateFlow<TabSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabSettings())

    private val _addingStarter = MutableStateFlow(false)
    val addingStarter: StateFlow<Boolean> = _addingStarter.asStateFlow()

    private val _starterAdded = Channel<ImportResult>(Channel.BUFFERED)
    val starterAdded: Flow<ImportResult> = _starterAdded.receiveAsFlow()

    fun setWhistle(whistle: WhistleKey) = viewModelScope.launch { store.setWhistle(whistle) }
    fun setCNatural(cNatural: CNatural) = viewModelScope.launch { store.setCNatural(cNatural) }
    fun setFoldOutOfRange(fold: Boolean) = viewModelScope.launch { store.setFoldOutOfRange(fold) }
    fun setMode(mode: TransposeMode) = viewModelScope.launch { store.setMode(mode) }

    fun addStarterSet() {
        if (_addingStarter.value) return
        _addingStarter.value = true
        appScope.launch {
            try {
                _starterAdded.send(starterTunes.add())
            } finally {
                _addingStarter.value = false
            }
        }
    }
}

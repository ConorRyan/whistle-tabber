package io.github.conorryan.whistletabber.ui.settings

import io.github.conorryan.whistletabber.data.settings.TabSettingsStore
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * One owner for tab-settings writes from the UI. Both settings surfaces (the
 * settings screen and the tune screen's sheet) are backed by one of these;
 * each write launches on the given scope.
 */
class TabSettingsEditor(
    private val store: TabSettingsStore,
    private val scope: CoroutineScope,
) {
    /** The current settings; the same flow the store exposes. */
    val settings: Flow<TabSettings> = store.settings

    fun setWhistle(whistle: WhistleKey) = scope.launch { store.setWhistle(whistle) }
    fun setCNatural(cNatural: CNatural) = scope.launch { store.setCNatural(cNatural) }
    fun setFoldOutOfRange(fold: Boolean) = scope.launch { store.setFoldOutOfRange(fold) }
    fun setMode(mode: TransposeMode) = scope.launch { store.setMode(mode) }
    fun setNoteNames(noteNames: NoteNames) = scope.launch { store.setNoteNames(noteNames) }
}

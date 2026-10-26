package io.github.conorryan.whistletabber.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.noteName

/** The note-name setting, provided once at the top of the app (`MainActivity`). */
val LocalNoteNames = staticCompositionLocalOf { NoteNames.DEFAULT }

/**
 * "Low D", "Ré grave": the note name follows [LocalNoteNames], not the app's
 * language; only the register words ("High", "Low") are translated.
 */
@Composable
fun whistleLabel(key: WhistleKey): String {
    val name = key.noteName(LocalNoteNames.current)
    return when (key.register) {
        WhistleKey.Register.HIGH -> stringResource(R.string.whistle_high, name)
        WhistleKey.Register.NORMAL -> name
        WhistleKey.Register.LOW -> stringResource(R.string.whistle_low, name)
    }
}

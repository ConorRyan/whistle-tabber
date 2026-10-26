package io.github.conorryan.whistletabber.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.label

/** The note-name setting, provided once at the top of the app (`MainActivity`). */
val LocalNoteNames = staticCompositionLocalOf { NoteNames.DEFAULT }

/** "Low D" or "Ré grave", by [LocalNoteNames]; never by the app's language. */
@Composable
fun whistleLabel(key: WhistleKey): String = key.label(LocalNoteNames.current)

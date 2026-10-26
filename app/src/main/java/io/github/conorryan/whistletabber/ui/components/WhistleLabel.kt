package io.github.conorryan.whistletabber.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.booleanResource
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.music.label

/** The resolved note-name setting, provided once at the top of the app (`MainActivity`). */
val LocalNoteNames = staticCompositionLocalOf { NoteNames.LETTERS }

/** [setting] with AUTO resolved by the app's language (`R.bool.language_uses_solfege`). */
@Composable
fun resolvedNoteNames(setting: NoteNames): NoteNames =
    setting.resolve(booleanResource(R.bool.language_uses_solfege))

/** "Low D" or "Ré grave", by [LocalNoteNames]; never by the app's language. */
@Composable
fun whistleLabel(key: WhistleKey): String = key.label(LocalNoteNames.current)

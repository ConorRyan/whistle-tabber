package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.NoteNames
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.PillChip
import io.github.conorryan.whistletabber.ui.components.whistleLabel

/** Chips for picking a whistle; [selected] null selects none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhistleChips(selected: WhistleKey?, onSelect: (WhistleKey) -> Unit, tagPrefix: String = "whistle") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhistleKey.entries.forEach { key ->
            PillChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(whistleLabel(key)) },
                modifier = Modifier.testTag("$tagPrefix-${key.name}"),
            )
        }
    }
}

/**
 * The tab settings that apply to every tune: my whistle, transpose mode,
 * fingerings, folding and note names. Shown in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabSettingsContent(
    settings: TabSettings,
    onWhistle: (WhistleKey) -> Unit,
    onFingering: (offset: Int, index: Int) -> Unit,
    onFold: (Boolean) -> Unit,
    onMode: (TransposeMode) -> Unit,
    onNoteNames: (NoteNames) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_whistle), style = MaterialTheme.typography.titleMedium)
        WhistleChips(settings.whistle, onWhistle)

        Text(stringResource(R.string.settings_mode), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TransposeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, TransposeMode.entries.size),
                    label = {
                        Text(stringResource(if (mode == TransposeMode.SAME_PITCHES) R.string.mode_same_pitches else R.string.mode_same_fingerings))
                    },
                    modifier = Modifier.testTag("mode-${mode.name}"),
                )
            }
        }
        Text(
            stringResource(if (settings.mode == TransposeMode.SAME_PITCHES) R.string.mode_same_pitches_help else R.string.mode_same_fingerings_help),
            style = MaterialTheme.typography.bodySmall,
        )

        FingeringsSection(settings.whistle, settings.fingerings, onFingering)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_fold), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_fold_help), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.foldOutOfRange, onCheckedChange = onFold, modifier = Modifier.testTag("fold"))
        }

        Text(stringResource(R.string.settings_note_names), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            NoteNames.entries.forEachIndexed { i, names ->
                SegmentedButton(
                    selected = settings.noteNames == names,
                    onClick = { onNoteNames(names) },
                    shape = SegmentedButtonDefaults.itemShape(i, NoteNames.entries.size),
                    // The fixed schemes show examples, not words: the same in every language.
                    label = {
                        Text(
                            when (names) {
                                NoteNames.AUTO -> stringResource(R.string.note_names_auto)
                                NoteNames.LETTERS -> "C D E"
                                NoteNames.SOLFEGE -> "Do Ré Mi"
                            },
                        )
                    },
                    modifier = Modifier.testTag("notes-${names.name}"),
                )
            }
        }
        Text(stringResource(R.string.settings_note_names_help), style = MaterialTheme.typography.bodySmall)
    }
}

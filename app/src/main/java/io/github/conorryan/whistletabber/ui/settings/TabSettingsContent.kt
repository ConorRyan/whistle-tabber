package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
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
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.TabSettings
import io.github.conorryan.whistletabber.music.TransposeMode
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.components.whistleLabel

/** Chips for picking a whistle; [selected] null selects none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhistleChips(selected: WhistleKey?, onSelect: (WhistleKey) -> Unit, tagPrefix: String = "whistle") {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WhistleKey.entries.forEach { key ->
            FilterChip(
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
 * C natural and folding. Shown in Settings and in the tune screen's sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabSettingsContent(
    settings: TabSettings,
    onWhistle: (WhistleKey) -> Unit,
    onCNatural: (CNatural) -> Unit,
    onFold: (Boolean) -> Unit,
    onMode: (TransposeMode) -> Unit,
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

        Text(stringResource(R.string.settings_c_natural), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CNatural.entries.forEach { c ->
                FilterChip(
                    selected = settings.cNatural == c,
                    onClick = { onCNatural(c) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
                            FingeringDiagram(c.fingering, holeSize = 8.dp)
                            Text(c.fingering.notation(), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.testTag("cnat-${c.name}"),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_fold), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_fold_help), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.foldOutOfRange, onCheckedChange = onFold, modifier = Modifier.testTag("fold"))
        }
    }
}

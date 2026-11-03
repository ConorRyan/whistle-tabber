package io.github.conorryan.whistletabber.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.AbcNotes
import io.github.conorryan.whistletabber.music.FingeringChoices
import io.github.conorryan.whistletabber.music.FingeringOptions
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames

private val OCTAVE_LABELS = listOf(R.string.tap_octave_low, R.string.tap_octave_high, R.string.tap_octave_highest)

/** "F♯+" for the note [offset] semitones above [whistle]'s bottom note. */
@Composable
private fun noteLabel(whistle: WhistleKey, offset: Int): String =
    AbcNotes.name(whistle.writtenTonic + offset, whistle.letter, LocalNoteNames.current) + "+".repeat(offset / 12)

/**
 * The "Fingerings" heading, collapsed by default (the grid is long); a tap on
 * the heading shows [FingeringChoicesGrid] and hides it again.
 */
@Composable
fun FingeringsSection(whistle: WhistleKey, choices: FingeringChoices, onFingering: (offset: Int, index: Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("fingeringsToggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_fingerings), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (expanded) R.string.settings_fingerings_help else R.string.settings_fingerings_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.fingerings_hide else R.string.fingerings_show),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) FingeringChoicesGrid(whistle, choices, onFingering)
    }
}

/**
 * Every note the whistle plays, by octave, each with the fingering it is
 * drawn with; a dot marks notes with other fingerings, and tapping one opens
 * a sheet to choose. Names are in [whistle]'s key.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FingeringChoicesGrid(whistle: WhistleKey, choices: FingeringChoices, onFingering: (offset: Int, index: Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FingeringOptions.offsets.groupBy { it / 12 }.forEach { (octave, offsets) ->
            Text(stringResource(OCTAVE_LABELS[octave]), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                offsets.forEach { offset -> NoteTile(whistle, offset, choices) { open = offset } }
            }
        }
    }
    if (FingeringOptions.offsets.any { o -> FingeringOptions.at(o).any { it.fife } }) {
        Text(
            "* " + stringResource(R.string.fingering_fife),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    open?.let { offset ->
        ModalBottomSheet(onDismissRequest = { open = null }) {
            FingeringSheet(whistle, offset, choices) { index ->
                onFingering(offset, index)
                open = null
            }
        }
    }
}

@Composable
private fun NoteTile(whistle: WhistleKey, offset: Int, choices: FingeringChoices, onOpen: () -> Unit) {
    val options = FingeringOptions.at(offset)
    val fingering = choices.fingeringAt(offset) ?: return
    // A note drawn with a fife fingering says so, even when it is the only one.
    val drawn = options.getOrNull(choices.index(offset))?.fife == true
    val choosable = options.size > 1
    // A single fife option still opens the sheet, which explains the "*".
    val opens = choosable || options.any { it.fife }
    val content: @Composable () -> Unit = {
        Box(Modifier.width(44.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(noteLabel(whistle, offset), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                FingeringDiagram(fingering, holeSize = 6.dp)
            }
            if (drawn) {
                Text(
                    "*",
                    Modifier.align(Alignment.TopStart).padding(4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (choosable) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).testTag("fingeringDot-$offset")) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(6.dp)) {}
                }
            }
        }
    }
    val shape = MaterialTheme.shapes.small
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (opens) {
        Surface(onClick = onOpen, shape = shape, border = border, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.testTag("fingering-$offset"), content = content)
    } else {
        Surface(shape = shape, border = border, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("fingering-$offset"), content = content)
    }
}

/** The note's fingerings as chips; a "*" marks the ones charted for fifes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FingeringSheet(whistle: WhistleKey, offset: Int, choices: FingeringChoices, onPick: (Int) -> Unit) {
    val options = FingeringOptions.at(offset)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.fingering_sheet_title, noteLabel(whistle, offset)), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, option ->
                FilterChip(
                    selected = choices.index(offset) == i,
                    onClick = { onPick(i) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
                            FingeringDiagram(option.fingering, holeSize = 8.dp)
                            Text(option.fingering.notation() + if (option.fife) " *" else "", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.testTag("fingering-$offset-$i"),
                )
            }
        }
        if (options.any { it.fife }) {
            Text("* " + stringResource(R.string.fingering_fife), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

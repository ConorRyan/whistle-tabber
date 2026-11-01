package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.AbcNotes
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.WhistleKey
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.components.PillChip
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames

/** The fingerings of the palette's main row, as offsets in the bottom octave: the major scale, then C natural. */
private val SCALE = listOf(0, 2, 4, 5, 7, 9, 11, 10)

/** Half-holed notes, behind "More fingerings". */
private val CHROMATIC = listOf(1, 3, 6, 8)

/** Every offset the palette can tap, in all three octaves (for rendering their sounds ahead). */
internal val PALETTE_OFFSETS: List<Int> = (0 until 3).flatMap { octave -> (SCALE + CHROMATIC).map { it + 12 * octave } }
@Composable
internal fun Palette(
    whistle: WhistleKey,
    octave: Int,
    cNatural: CNatural,
    canDelete: Boolean,
    canLeft: Boolean,
    canRight: Boolean,
    onOctave: (Int) -> Unit,
    onTap: (Int) -> Unit,
    onRest: () -> Unit,
    onDelete: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    var more by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(R.string.tap_octave_low, R.string.tap_octave_high, R.string.tap_octave_highest).forEachIndexed { i, label ->
                PillChip(
                    selected = octave == i,
                    onClick = { onOctave(i) },
                    label = { Text(stringResource(label), maxLines = 1) },
                    modifier = Modifier.testTag("octave-$i"),
                )
            }
            Box(Modifier.weight(1f))
            // An icon, not words, so the octaves keep one line on a narrow phone.
            IconToggleButton(checked = more, onCheckedChange = { more = it }, modifier = Modifier.testTag("more")) {
                Icon(
                    if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (more) R.string.tap_fewer else R.string.tap_more),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        KeyRow(SCALE, whistle, octave, cNatural, onTap)
        if (more) KeyRow(CHROMATIC, whistle, octave, cNatural, onTap)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val keyShape = MaterialTheme.shapes.medium
            OutlinedIconButton(onClick = onLeft, enabled = canLeft, shape = keyShape, modifier = Modifier.size(52.dp).testTag("left")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tap_cursor_left))
            }
            OutlinedIconButton(onClick = onRight, enabled = canRight, shape = keyShape, modifier = Modifier.size(52.dp).testTag("right")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tap_cursor_right))
            }
            OutlinedButton(onClick = onRest, shape = keyShape, modifier = Modifier.weight(1f).height(52.dp).testTag("rest")) { Text(stringResource(R.string.tap_rest)) }
            FilledIconButton(
                onClick = onDelete,
                enabled = canDelete,
                shape = keyShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
                modifier = Modifier.width(72.dp).height(52.dp).testTag("delete"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.tap_delete))
            }
        }
    }
}

/**
 * One button per fingering in [offsets] (bottom-octave offsets) moved to
 * [octave]; unplayable ones are left out. Every row has [SCALE]'s number of
 * equal slots, so keys line up and a short row is centred.
 */
@Composable
private fun KeyRow(offsets: List<Int>, whistle: WhistleKey, octave: Int, cNatural: CNatural, onTap: (Int) -> Unit) {
    val names = LocalNoteNames.current
    val keys = offsets.map { it + 12 * octave }.mapNotNull { offset -> FingeringEngine.fingeringAt(offset, cNatural)?.let { offset to it } }
    val empty = SCALE.size - keys.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (empty > 0) Box(Modifier.weight(empty / 2f))
        for ((offset, fingering) in keys) {
            Surface(
                onClick = { onTap(offset) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).testTag("key-$offset"),
            ) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FingeringDiagram(fingering, holeSize = 10.dp)
                    Text(
                        AbcNotes.name(whistle.writtenTonic + offset, whistle.letter, names) + "+".repeat(fingering.octave),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (fingering.hard) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
        if (empty > 0) Box(Modifier.weight(empty / 2f))
    }
}

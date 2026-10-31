package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.conorryan.whistletabber.ui.components.LocalNoteNames

/** The fingerings of the palette's main row, as offsets in the bottom octave: the major scale, then C natural. */
private val SCALE = listOf(0, 2, 4, 5, 7, 9, 11, 10)

/** Half-holed notes, behind "More fingerings". */
private val CHROMATIC = listOf(1, 3, 6, 8)

/** Every offset the palette can tap, in all three octaves (for rendering their sounds ahead). */
internal val PALETTE_OFFSETS: List<Int> = (0 until 3).flatMap { octave -> (SCALE + CHROMATIC).map { it + 12 * octave } }
@OptIn(ExperimentalLayoutApi::class)
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.tap_octave_low, R.string.tap_octave_high, R.string.tap_octave_highest).forEachIndexed { i, label ->
                FilterChip(
                    selected = octave == i,
                    onClick = { onOctave(i) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("octave-$i"),
                )
            }
        }
        KeyRow(SCALE, whistle, octave, cNatural, onTap)
        if (more) KeyRow(CHROMATIC, whistle, octave, cNatural, onTap)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onRest, modifier = Modifier.testTag("rest")) { Text(stringResource(R.string.tap_rest)) }
            IconButton(onClick = onLeft, enabled = canLeft, modifier = Modifier.testTag("left")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tap_cursor_left))
            }
            IconButton(onClick = onRight, enabled = canRight, modifier = Modifier.testTag("right")) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tap_cursor_right))
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.testTag("delete")) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.tap_delete))
            }
            TextButton(onClick = { more = !more }, modifier = Modifier.testTag("more")) {
                Text(stringResource(if (more) R.string.tap_fewer else R.string.tap_more))
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (empty > 0) Box(Modifier.weight(empty / 2f))
        for ((offset, fingering) in keys) {
            Surface(
                onClick = { onTap(offset) },
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.weight(1f).testTag("key-$offset"),
            ) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

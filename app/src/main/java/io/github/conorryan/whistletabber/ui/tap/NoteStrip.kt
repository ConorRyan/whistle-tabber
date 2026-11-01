package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.music.CNatural
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.FingeringEngine
import io.github.conorryan.whistletabber.music.TapTiming
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.theme.LocalWhistleColors

/**
 * The tune so far, a diagram per note and the cursor; tap a note to put the
 * cursor after it. Without [timing] every fourth note starts a bar; with it
 * the bar gaps come from the note lengths and each note carries a mark of its
 * length (wider is longer).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteStrip(
    notes: List<Int?>,
    cursor: Int,
    cNatural: CNatural,
    onCursor: (Int) -> Unit,
    highlight: Int? = null,
    timing: TapTiming? = null,
) {
    if (notes.isEmpty()) {
        Text(
            stringResource(R.string.tap_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tapEmpty"),
        )
        return
    }
    val restLabel = stringResource(R.string.tap_rest)
    val bar = timing?.setup?.unitsPerBar ?: TapTune.PER_BAR
    // Positions count from the start of the first (maybe partial) bar's full length, as in TapTune.
    var pos = if (timing != null) bar - timing.firstBarUnits else 0
    FlowRow(Modifier.fillMaxWidth().testTag("noteStrip"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..notes.size) {
            if (i == cursor) Cursor()
            if (i == notes.size) break
            val atBarLine = if (timing != null) i > 0 && Math.floorMod(pos, bar) == 0 else i > 0 && i % TapTune.PER_BAR == 0
            if (atBarLine) Box(Modifier.width(12.dp).testTag("barGap-$i"))
            val units = timing?.lengths?.getOrElse(i) { timing.setup.unitsPerBeat }
            val fingering = notes[i]?.let { FingeringEngine.fingeringAt(it, cNatural) }
            Column(
                Modifier
                    .then(
                        if (i == highlight) {
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)).semantics { selected = true }
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onCursor(i + 1) }
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .testTag("note-$i"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (fingering != null) {
                    FingeringDiagram(fingering, holeSize = 7.dp)
                    OctaveMark(fingering)
                } else {
                    Box(Modifier.height(64.dp).semantics { contentDescription = restLabel }, contentAlignment = Alignment.Center) {
                        Text("–", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (timing != null && units != null) LengthMark(units, i)
            }
            if (timing != null) pos += units ?: timing.setup.unitsPerBeat
        }
    }
}

@Composable
private fun Cursor() {
    Box(Modifier.padding(horizontal = 2.dp, vertical = 2.dp).width(3.dp).height(64.dp).background(LocalWhistleColors.current.star, RoundedCornerShape(2.dp)).testTag("cursor"))
}

@Composable
private fun OctaveMark(fingering: Fingering) {
    Text("+".repeat(fingering.octave).ifEmpty { " " }, style = MaterialTheme.typography.labelSmall)
}

/** A bar under the note, as wide as its length in grid units (capped). */
@Composable
private fun LengthMark(units: Int, index: Int) {
    Box(
        Modifier.padding(top = 1.dp)
            .height(3.dp)
            .width((units * 4).dp.coerceAtMost(28.dp))
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
            .testTag("length-$index"),
    )
}

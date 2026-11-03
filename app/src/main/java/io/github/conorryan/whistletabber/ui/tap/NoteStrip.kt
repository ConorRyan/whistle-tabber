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
import io.github.conorryan.whistletabber.music.TICKS_PER_EIGHTH
import io.github.conorryan.whistletabber.music.TapDraft
import io.github.conorryan.whistletabber.music.TapTune
import io.github.conorryan.whistletabber.ui.components.FingeringDiagram
import io.github.conorryan.whistletabber.ui.theme.LocalWhistleColors

/**
 * The tune so far, a diagram per note and the cursor; tap a note to put the
 * cursor after it. Bar gaps come from the note lengths ([TapTune.barStarts])
 * and the tune's own bar lines; a bar line that isn't plain (`|:`, `:|`,
 * `[2`) shows as a label, which a tap puts the cursor after. With a rhythm,
 * each note carries a mark of its length (wider is longer).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteStrip(
    draft: TapDraft,
    cursor: Int,
    cNatural: CNatural,
    onCursor: (Int) -> Unit,
    highlight: Int? = null,
) {
    val notes = draft.notes
    if (notes.isEmpty()) {
        Text(
            stringResource(R.string.tap_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("tapEmpty"),
        )
        return
    }
    val timing = draft.timing
    val restLabel = stringResource(R.string.tap_rest)
    val barStarts = TapTune.barStarts(draft)
    val byGap = draft.anchors.filter { it.visible }.groupBy { it.gap }
    FlowRow(Modifier.fillMaxWidth().testTag("noteStrip"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0..notes.size) {
            val bars = byGap[i].orEmpty()
            if (cursor == draft.slotOf(i)) Cursor()
            if (barStarts.getOrElse(i) { false } && bars.isEmpty()) Box(Modifier.width(12.dp).testTag("barGap-$i"))
            bars.forEachIndexed { k, bar ->
                BarMark(bar.text, Modifier.clickable { onCursor(draft.slotOf(i, k + 1)) }.testTag("bar-$i-$k"))
                if (cursor == draft.slotOf(i, k + 1)) Cursor()
            }
            if (i == notes.size) break
            val units = timing?.lengths?.getOrNull(i)
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
                    .clickable { onCursor(draft.slotAfter(i)) }
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
        }
    }
}

/** A bar line as written, a plain one as a thin rule. */
@Composable
private fun BarMark(text: String, modifier: Modifier) {
    Box(modifier.height(64.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        if (text == "|") {
            Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

/** A bar under the note, as wide as its length (4 dp an eighth, capped). */
@Composable
private fun LengthMark(ticks: Int, index: Int) {
    Box(
        Modifier.padding(top = 1.dp)
            .height(3.dp)
            .width((ticks * 4f / TICKS_PER_EIGHTH).dp.coerceAtMost(28.dp))
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
            .testTag("length-$index"),
    )
}

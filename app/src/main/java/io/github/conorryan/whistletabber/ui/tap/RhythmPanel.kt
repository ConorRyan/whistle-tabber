package io.github.conorryan.whistletabber.ui.tap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.BuildConfig
import io.github.conorryan.whistletabber.R
import io.github.conorryan.whistletabber.audio.OutputPath
import io.github.conorryan.whistletabber.audio.PadSound
import io.github.conorryan.whistletabber.audio.TapAudio
import io.github.conorryan.whistletabber.music.Meter
import io.github.conorryan.whistletabber.music.RhythmGrid
import io.github.conorryan.whistletabber.music.RhythmSetup

/** In release builds the audio delay only shows as a warning above this, in ms. */
internal const val LATENCY_WARNING_MS = 60

/**
 * The rhythm tapper's setup, in a bottom sheet over the editor: tempo, time
 * signature, grid, triplets (simple meters only), pad sound, then start or
 * cancel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RhythmSetupSheet(
    panel: RhythmPanel,
    onSetup: (RhythmSetup) -> Unit,
    onPadSound: (PadSound) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    val setup = panel.setup
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("rhythmPanel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.rhythm_tempo, setup.bpm), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = setup.bpm.toFloat(),
            onValueChange = { onSetup(setup.copy(bpm = it.toInt())) },
            valueRange = RhythmSetup.MIN_BPM.toFloat()..RhythmSetup.MAX_BPM.toFloat(),
            modifier = Modifier.testTag("bpm"),
        )
        Text(stringResource(R.string.rhythm_meter), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (meter in Meter.entries) {
                FilterChip(
                    selected = setup.meter == meter,
                    onClick = { onSetup(setup.copy(meter = meter)) },
                    label = { Text(meter.abc) },
                    modifier = Modifier.testTag("meter-${meter.abc}"),
                )
            }
        }
        Text(stringResource(R.string.rhythm_grid), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((grid, label) in listOf(RhythmGrid.EIGHTH to R.string.rhythm_grid_eighth, RhythmGrid.SIXTEENTH to R.string.rhythm_grid_sixteenth)) {
                FilterChip(
                    selected = setup.grid == grid,
                    onClick = { onSetup(setup.copy(grid = grid)) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("grid-${grid.name}"),
                )
            }
        }
        if (!setup.meter.compound) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rhythm_triplets), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = setup.triplets,
                    onCheckedChange = { onSetup(setup.copy(triplets = it)) },
                    modifier = Modifier.testTag("triplets"),
                )
            }
        }
        Text(stringResource(R.string.rhythm_pad_sound), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sound, label) in listOf(PadSound.NOTE to R.string.pad_note, PadSound.TICK to R.string.pad_tick, PadSound.SILENT to R.string.pad_silent)) {
                FilterChip(
                    selected = panel.padSound == sound,
                    onClick = { onPadSound(sound) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("pad-${sound.name}"),
                )
            }
        }
        Text(stringResource(R.string.rhythm_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("rhythmCancel")) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onStart, modifier = Modifier.testTag("rhythmStart")) {
                Text(stringResource(R.string.rhythm_start))
            }
        }
    }
}

/**
 * The rhythm tapper while tapping, and once done, in place of the palette.
 * The audio output's diagnostics show in debug builds; a release build only
 * warns when the delay is over [LATENCY_WARNING_MS].
 */
@Composable
internal fun RhythmPanelContent(
    panel: RhythmPanel,
    beat: Int?,
    latencyMs: Int?,
    audioInfo: TapAudio.Info?,
    onPress: (Long) -> Unit,
    onRelease: (Long) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onKeep: () -> Unit,
) {
    val setup = panel.setup
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("rhythmPanel"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (panel.phase) {
            RhythmPhase.SETUP -> {}
            RhythmPhase.TAPPING -> {
                val bar = setup.meter.beatsPerBar
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        when {
                            beat == null -> " "
                            beat < 0 -> stringResource(R.string.rhythm_count_in, beat + bar + 1)
                            else -> stringResource(R.string.rhythm_beat, Math.floorMod(beat, bar) + 1)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("beat"),
                    )
                    Text(
                        stringResource(R.string.rhythm_progress, minOf(panel.next + 1, panel.total), panel.total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("rhythmPad")
                        .rhythmPad(onPress, onRelease),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.rhythm_pad), style = MaterialTheme.typography.titleLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onStop, modifier = Modifier.testTag("rhythmStop")) { Text(stringResource(R.string.rhythm_stop)) }
                    if (BuildConfig.DEBUG) {
                        if (latencyMs != null) {
                            Text(
                                stringResource(R.string.rhythm_latency, latencyMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("latency"),
                            )
                        }
                    } else if (latencyMs != null && latencyMs > LATENCY_WARNING_MS) {
                        Text(
                            stringResource(R.string.rhythm_latency_warning, latencyMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("latencyWarning"),
                        )
                    }
                }
                if (BuildConfig.DEBUG && audioInfo != null) {
                    Text(
                        stringResource(
                            R.string.rhythm_audio_info,
                            audioInfo.bufferFrames,
                            audioInfo.burstFrames,
                            audioInfo.rate,
                            stringResource(
                                when (audioInfo.path) {
                                    OutputPath.AAUDIO_EXCLUSIVE -> R.string.rhythm_path_aaudio_exclusive
                                    OutputPath.AAUDIO_SHARED -> R.string.rhythm_path_aaudio_shared
                                    OutputPath.AUDIO_TRACK_FAST -> R.string.rhythm_path_fast
                                    OutputPath.AUDIO_TRACK_NORMAL -> R.string.rhythm_path_normal
                                },
                            ),
                            audioInfo.underruns,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("audioInfo"),
                    )
                }
            }
            RhythmPhase.DONE -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.rhythm_done),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("rhythmRetry")) { Text(stringResource(R.string.rhythm_retry)) }
                    Button(onClick = onKeep, modifier = Modifier.testTag("rhythmKeep")) {
                        Text(stringResource(R.string.rhythm_keep))
                    }
                }
            }
        }
    }
}

/**
 * The rhythm pad's touches, at each event's `uptimeMillis`: every finger
 * that lands is a press, and only the newest finger's lift is a release, so
 * a finger landing before the last one lifts starts the next note.
 */
@Composable
internal fun Modifier.rhythmPad(onPress: (Long) -> Unit, onRelease: (Long) -> Unit): Modifier {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    return pointerInput(Unit) {
        awaitPointerEventScope {
            var current: PointerId? = null
            while (true) {
                val changes = awaitPointerEvent().changes
                for (c in changes) {
                    if (c.changedToUpIgnoreConsumed() && c.id == current) {
                        current = null
                        release(c.uptimeMillis)
                    }
                }
                for (c in changes) {
                    if (c.changedToDownIgnoreConsumed()) {
                        current = c.id
                        press(c.uptimeMillis)
                    }
                }
                changes.forEach { it.consume() }
            }
        }
    }
}

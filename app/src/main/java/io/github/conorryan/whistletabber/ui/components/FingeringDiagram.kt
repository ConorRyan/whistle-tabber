package io.github.conorryan.whistletabber.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.conorryan.whistletabber.music.Fingering
import io.github.conorryan.whistletabber.music.Hole

/**
 * Six holes, mouthpiece at the top, with a gap between the hands; matches
 * the diagrams the page draws under the staff. The octave mark is left to
 * the caller so lists can align it.
 */
@Composable
fun FingeringDiagram(
    fingering: Fingering,
    modifier: Modifier = Modifier,
    holeSize: Dp = 10.dp,
    color: Color = LocalContentColor.current,
) {
    val pitch = holeSize * 1.25f
    val gap = holeSize * 0.4f
    Canvas(
        modifier
            .size(holeSize + 2.dp, pitch * 6 + gap)
            .semantics { contentDescription = fingering.notation() },
    ) {
        val r = holeSize.toPx() / 2
        val stroke = Stroke(width = 1.2.dp.toPx())
        var y = pitch.toPx() / 2
        fingering.holes.forEachIndexed { i, hole ->
            if (i == 3) y += gap.toPx()
            val center = Offset(size.width / 2, y)
            when (hole) {
                Hole.CLOSED -> drawCircle(color, r, center)
                Hole.OPEN -> drawCircle(color, r, center, style = stroke)
                Hole.HALF -> {
                    drawCircle(color, r, center, style = stroke)
                    drawArc(
                        color, startAngle = 0f, sweepAngle = 180f, useCenter = true,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r),
                    )
                }
            }
            y += pitch.toPx()
        }
    }
}

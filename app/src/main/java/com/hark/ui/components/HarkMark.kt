package com.hark.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hark.ui.theme.Hark

/**
 * 64 vertices of the Order-3 Hilbert curve from prototype/hark-mark-11d.svg.
 * Coordinate space: 48x48.
 */
val HilbertPoints = listOf(
    Offset(6.0f, 6.0f),
    Offset(6.0f, 11.4f),
    Offset(11.4f, 11.4f),
    Offset(11.4f, 6.0f),
    Offset(16.8f, 6.0f),
    Offset(22.2f, 6.0f),
    Offset(22.2f, 11.4f),
    Offset(16.8f, 11.4f),
    Offset(16.8f, 16.8f),
    Offset(22.2f, 16.8f),
    Offset(22.2f, 22.2f),
    Offset(16.8f, 22.2f),
    Offset(11.4f, 22.2f),
    Offset(11.4f, 16.8f),
    Offset(6.0f, 16.8f),
    Offset(6.0f, 22.2f),
    Offset(6.0f, 27.6f),
    Offset(11.4f, 27.6f),
    Offset(11.4f, 33.0f),
    Offset(6.0f, 33.0f),
    Offset(6.0f, 38.4f),
    Offset(6.0f, 43.8f),
    Offset(11.4f, 43.8f),
    Offset(11.4f, 38.4f),
    Offset(16.8f, 38.4f),
    Offset(16.8f, 43.8f),
    Offset(22.2f, 43.8f),
    Offset(22.2f, 38.4f),
    Offset(22.2f, 33.0f),
    Offset(16.8f, 33.0f),
    Offset(16.8f, 27.6f),
    Offset(22.2f, 27.6f),
    Offset(27.6f, 27.6f),
    Offset(33.0f, 27.6f),
    Offset(33.0f, 33.0f),
    Offset(27.6f, 33.0f),
    Offset(27.6f, 38.4f),
    Offset(27.6f, 43.8f),
    Offset(33.0f, 43.8f),
    Offset(33.0f, 38.4f),
    Offset(38.4f, 38.4f),
    Offset(38.4f, 43.8f),
    Offset(43.8f, 43.8f),
    Offset(43.8f, 38.4f),
    Offset(43.8f, 33.0f),
    Offset(38.4f, 33.0f),
    Offset(38.4f, 27.6f),
    Offset(43.8f, 27.6f),
    Offset(43.8f, 22.2f),
    Offset(43.8f, 16.8f),
    Offset(38.4f, 16.8f),
    Offset(38.4f, 22.2f),
    Offset(33.0f, 22.2f),
    Offset(27.6f, 22.2f),
    Offset(27.6f, 16.8f),
    Offset(33.0f, 16.8f),
    Offset(33.0f, 11.4f),
    Offset(27.6f, 11.4f),
    Offset(27.6f, 6.0f),
    Offset(33.0f, 6.0f),
    Offset(38.4f, 6.0f),
    Offset(38.4f, 11.4f),
    Offset(43.8f, 11.4f),
    Offset(43.8f, 6.0f),
)

fun createHilbertPath(): Path = Path().apply {
    if (HilbertPoints.isNotEmpty()) {
        moveTo(HilbertPoints[0].x, HilbertPoints[0].y)
        for (i in 1 until HilbertPoints.size) {
            lineTo(HilbertPoints[i].x, HilbertPoints[i].y)
        }
    }
}

/**
 * Hark Mark — Order-3 Hilbert space-filling curve.
 *
 * @param progress 0f..1f for animated stroke reveal.
 */
@Composable
fun HarkMark(
    modifier: Modifier = Modifier.size(96.dp),
    progress: Float = 1f,
    color: Color = Hark.colors.ink,
    strokeWidth: Dp? = null,
) {
    val fullPath = remember { createHilbertPath() }
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress <= 0f) return@Canvas

        val canvasSize = size.minDimension
        val scale = canvasSize / 48f
        val strokePx = strokeWidth?.toPx() ?: (2.4f * scale)

        pathMeasure.setPath(fullPath, false)
        val totalLength = pathMeasure.length
        val animatedPath = Path()
        pathMeasure.getSegment(0f, totalLength * clampedProgress, animatedPath, true)

        scale(scale, Offset.Zero) {
            drawPath(
                path = animatedPath,
                color = color,
                style = Stroke(
                    width = strokePx / scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * Looping Hilbert curve spinner that animates in and out smoothly.
 */
@Composable
fun HilbertSpinner(
    modifier: Modifier = Modifier.size(72.dp),
    color: Color = Hark.colors.ink,
    strokeWidth: Dp? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hilbert_spinner")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1.0f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "progress",
    )
    HarkMark(
        modifier = modifier,
        progress = progress,
        color = color,
        strokeWidth = strokeWidth,
    )
}

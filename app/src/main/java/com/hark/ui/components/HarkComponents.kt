package com.hark.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlin.math.sin

/** Small uppercase mono label — the prototype's section/eyebrow text. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        style = HarkType.label,
        color = color ?: Hark.colors.inkFaint,
        modifier = modifier,
    )
}

/** Smaller mono meta line (times, counts, hints). */
@Composable
fun MetaLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        style = HarkType.meta,
        color = color ?: Hark.colors.inkFaint,
        modifier = modifier,
    )
}

/** A checkable task box; rust fill when done. */
@Composable
fun TaskCheck(done: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hark.colors
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, if (done) c.rust else c.checkboxBorder, RoundedCornerShape(3.dp))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Box(Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(c.rust))
    }
}

/** The little dash that marks a note (vs a task's box). */
@Composable
fun NoteDash(modifier: Modifier = Modifier) {
    Box(modifier.width(16.dp).height(1.dp).background(Hark.colors.ink.copy(alpha = 0.32f)))
}

/** Three phase-shifted bars — the "nib" that stands in for Talk. */
@Composable
fun TalkNib(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        NibBar(color, 0)
        NibBar(color, 180)
        NibBar(color, 360)
    }
}

@Composable
private fun NibBar(color: Color, delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "nib")
    val scale by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550, delayMillis = delayMillis), RepeatMode.Reverse),
        label = "scaleY",
    )
    Box(
        Modifier
            .width(2.dp)
            .height(13.dp)
            .graphicsLayer { scaleY = scale; transformOrigin = TransformOrigin.Center }
            .background(color, RoundedCornerShape(1.dp)),
    )
}

/** Lucide Library / Shelf icon (4 books on a shelf). */
@Composable
fun ShelfIcon(
    modifier: Modifier = Modifier.size(16.dp),
    color: Color = Hark.colors.ink,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokePx = (w / 12f).coerceAtLeast(1.5f)

        val sx = w / 24f
        val sy = h / 24f

        // 1. Book 1: M4 4v16
        drawLine(
            color = color,
            start = Offset(4f * sx, 4f * sy),
            end = Offset(4f * sx, 20f * sy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        // 2. Book 2: M8 8v12
        drawLine(
            color = color,
            start = Offset(8f * sx, 8f * sy),
            end = Offset(8f * sx, 20f * sy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        // 3. Book 3: M12 6v14
        drawLine(
            color = color,
            start = Offset(12f * sx, 6f * sy),
            end = Offset(12f * sx, 20f * sy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        // 4. Book 4 (slanted): m16 6 4 14
        drawLine(
            color = color,
            start = Offset(16f * sx, 6f * sy),
            end = Offset(20f * sx, 20f * sy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )
    }
}

/** Live listening waveform; bar heights react to [level] (0..1) with a travelling wave. */
@Composable
fun ListeningWave(level: Float, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "phase",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val bars = 8
        repeat(bars) { i ->
            val amp = 0.15f + level * 0.85f
            val wave = 0.5f + 0.5f * sin(phase + i.toFloat())
            val h = (10f + (0.25f + amp * wave) * 34f)
            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

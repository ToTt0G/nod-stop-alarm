package app.nod.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** Concentric rings radiating outwards, staggered. Used for live dots and the alarm screen. */
@Composable
fun PulseRings(
    color: Color,
    modifier: Modifier = Modifier,
    rings: Int = 3,
    periodMs: Int = 2400,
    strokeWidth: Dp = 1.5.dp,
    filled: Boolean = false,
) {
    val t = rememberInfiniteTransition(label = "pulse")
    val phase = t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier) {
        val maxR = size.minDimension / 2
        repeat(rings) { i ->
            val p = (phase.value + i / rings.toFloat()) % 1f
            val eased = 1f - (1f - p) * (1f - p)
            val alpha = (1f - p) * 0.8f
            if (filled) {
                drawCircle(color.copy(alpha = alpha * 0.35f), radius = maxR * eased)
            } else {
                drawCircle(color.copy(alpha = alpha), radius = maxR * eased, style = Stroke(strokeWidth.toPx()))
            }
        }
    }
}

/**
 * A "live" indicator with a heartbeat: one soft pulse, then a pause. The pause renders no frames
 * at all, so an on-screen trip costs about a third of a continuously animating dot.
 */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val beat = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            beat.snapTo(0f)
            beat.animateTo(1f, tween(1400, easing = LinearEasing))
            delay(2200)
        }
    }
    Canvas(modifier.size(size * 3)) {
        val maxR = this.size.minDimension / 2
        val p = beat.value
        if (p < 1f) {
            val eased = 1f - (1f - p) * (1f - p)
            drawCircle(color.copy(alpha = (1f - p) * 0.3f), radius = maxR * eased)
        }
        drawCircle(color, radius = size.toPx() / 2)
    }
}

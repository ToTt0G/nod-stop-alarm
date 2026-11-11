package app.nod.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Nod mark: a geofence ring with a traveller dot arriving at the gap, around a dozing moon.
 * Drawn in code so it can animate itself in.
 */
@Composable
fun NodMark(
    modifier: Modifier = Modifier,
    color: Color,
    animate: Boolean = false,
    breathe: Boolean = false,
    /** Breaths before the mark rests. Finite by default: an idle screen should render no frames. */
    breaths: Int = 3,
) {
    val sweep = remember { Animatable(if (animate) 0f else 1f) }
    val moon = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        delay(450)
        moon.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f))
    }
    val haloAnim = remember { Animatable(0f) }
    LaunchedEffect(breathe, breaths) {
        if (!breathe) return@LaunchedEffect
        repeat(breaths) {
            haloAnim.snapTo(0f)
            haloAnim.animateTo(1f, tween(2200, easing = LinearEasing))
        }
        haloAnim.snapTo(0f)
    }

    Canvas(modifier) {
        val u = size.minDimension / 108f
        val c = Offset(size.width / 2, size.height / 2)
        val ringR = 23f * u
        val stroke = 4.6f * u
        val gapCenter = -40f
        val gap = 34f
        val start = gapCenter + gap / 2
        val full = 360f - gap
        val p = sweep.value

        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = full * p,
            useCenter = false,
            topLeft = Offset(c.x - ringR, c.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Traveller dot rides the head of the ring, then settles in the gap.
        val dotAngle = Math.toRadians((start + (full + gap / 2) * p).toDouble())
        val dot = Offset(c.x + ringR * cos(dotAngle).toFloat(), c.y + ringR * sin(dotAngle).toFloat())
        // Read in the draw phase: the halo only redraws the canvas, never recomposes.
        val halo = haloAnim.value
        if (halo > 0f && halo < 1f) {
            drawCircle(color.copy(alpha = (1f - halo) * 0.45f), radius = 4.4f * u * (1f + halo * 1.8f), center = dot)
        }
        drawCircle(color, radius = 4.4f * u, center = dot)

        // Crescent moon = outer disc minus an offset disc, facing the traveller.
        val m = moon.value
        if (m > 0.01f) {
            val mc = Offset(c.x - 1f * u, c.y + 1f * u)
            val a = Math.toRadians(gapCenter.toDouble())
            val cut = Offset(mc.x + 7.2f * u * cos(a).toFloat(), mc.y + 7.2f * u * sin(a).toFloat())
            val outer = Path().apply { addOval(Rect(mc, 12.5f * u)) }
            val inner = Path().apply { addOval(Rect(cut, 10.6f * u)) }
            val crescent = Path.combine(PathOperation.Difference, outer, inner)
            scale(m, pivot = mc) {
                rotate((1f - m) * -40f, pivot = mc) { drawPath(crescent, color, alpha = m.coerceIn(0f, 1f)) }
            }
        }
    }
}

package app.nod.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.nod.ui.theme.NodMotion
import kotlinx.coroutines.delay

/** Fade-and-rise entrance, staggered by [index]. */
@Composable
fun Modifier.enter(index: Int, play: Boolean = true): Modifier {
    val p = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            delay(70L * index)
            p.animateTo(1f, NodMotion.gentle())
        }
    }
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise
    }
}

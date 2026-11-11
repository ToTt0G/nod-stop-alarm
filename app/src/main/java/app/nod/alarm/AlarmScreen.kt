package app.nod.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nod.R
import app.nod.ui.components.NodMark
import app.nod.ui.components.PulseRings
import app.nod.ui.components.SlideToConfirm
import app.nod.ui.components.enter
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodPalette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun AlarmScreen(
    place: String,
    distance: String?,
    silenced: Boolean,
    preview: Boolean,
    onDismiss: () -> Unit,
    earbudsHint: Boolean = false,
) {
    val lime = NodPalette.Lime
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }

    val glow = rememberInfiniteTransition(label = "glow")
    val g by glow.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "g")
    val exit = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(leaving) {
        if (leaving) {
            exit.animateTo(1f, NodMotion.emphasized(380))
            onDismiss()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NodPalette.Ink)
            .graphicsLayer { alpha = 1f - exit.value; scaleX = 1f + exit.value * 0.06f; scaleY = 1f + exit.value * 0.06f },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(lime.copy(alpha = g * 0.35f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.46f),
                    radius = size.width * 0.95f,
                ),
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 64.sp, lineHeight = 66.sp),
                color = Color.White,
                modifier = Modifier.enter(0),
            )
            Text(
                DateFormat.getDateInstance(DateFormat.FULL).format(now),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.enter(1),
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PulseRings(lime, Modifier.size(340.dp), rings = 4, periodMs = if (silenced) 4200 else 2200, strokeWidth = 2.dp)
                Box(Modifier.size(150.dp).enter(2), contentAlignment = Alignment.Center) {
                    NodMark(Modifier.size(150.dp), color = lime, animate = true, breathe = true, breaths = Int.MAX_VALUE)
                }
            }

            Text(
                stringResource(if (preview) R.string.alarm_preview_label else R.string.alarm_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = lime,
                modifier = Modifier.enter(3),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.alarm_title, place),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.enter(4),
            )
            if (distance != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.alarm_distance, distance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.enter(5),
                )
            }
            Spacer(Modifier.height(36.dp))
            SlideToConfirm(
                text = stringResource(R.string.slide_awake),
                onConfirm = { leaving = true },
                track = Color.White.copy(alpha = 0.08f),
                knob = lime,
                onKnob = NodPalette.Ink,
                textColor = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.enter(6),
                actionLabel = stringResource(R.string.action_awake),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer { alpha = 0.5f },
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (silenced) R.string.silenced else R.string.volume_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
            if (earbudsHint) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.graphicsLayer { alpha = 0.5f },
                ) {
                    Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.audio_earbuds_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

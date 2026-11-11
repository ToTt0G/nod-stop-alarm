package app.nod.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import app.nod.data.model.PlaceKind
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---- Haptics -------------------------------------------------------------------------------

class Haptics(private val view: android.view.View) {
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    fun click() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm() = view.performHapticFeedback(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
    fun heavy() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---- Press feedback ------------------------------------------------------------------------

/** Springy shrink while pressed. The single most important "feels premium" detail. */
@Composable
fun Modifier.pressable(
    interaction: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) pressedScale else 1f, NodMotion.snappy(), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun Modifier.bounceClick(enabled: Boolean = true, pressedScale: Float = 0.96f, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    return this
        .pressable(interaction, pressedScale)
        .clickable(interaction, indication = null, enabled = enabled) { haptics.click(); onClick() }
}

// ---- Buttons -------------------------------------------------------------------------------

enum class NodButtonStyle { Primary, Secondary, Ghost }

@Composable
fun NodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NodButtonStyle = NodButtonStyle.Primary,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = NodTheme.colors
    val (bg, fg) = when (style) {
        NodButtonStyle.Primary -> c.cta to c.onCta
        NodButtonStyle.Secondary -> c.cardHigh to MaterialTheme.colorScheme.onSurface
        NodButtonStyle.Ghost -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "a")
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .bounceClick(enabled, onClick = onClick)
            .height(58.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == NodButtonStyle.Ghost) Modifier.border(1.dp, c.hairline, CircleShape) else Modifier)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Icon(trailing, null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

/** Round floating button used over the map. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    container: Color = NodTheme.colors.card,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    elevated: Boolean = true,
) {
    Box(
        modifier
            .bounceClick(onClick = onClick, pressedScale = 0.9f)
            .size(size)
            .then(if (elevated) Modifier.shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f)) else Modifier)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---- Hold to confirm -----------------------------------------------------------------------

/**
 * Long-press button that fills up; prevents ending a trip by accident in a bumpy bus.
 *
 * Accessibility: the hold gesture can't be performed by TalkBack, Switch Access or automation, so the
 * outermost node exposes click and long-click actions that run exactly the same confirm. A plain
 * finger tap never confirms; it only nudges the button and shows [holdingText] as a hint.
 */
@Composable
fun HoldToConfirm(
    text: String,
    holdingText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Int = 1100,
    actionLabel: String = text,
) {
    val c = NodTheme.colors
    val progress = remember { Animatable(0f) }
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var holding by remember { mutableStateOf(false) }
    var hinting by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var hintJob by remember { mutableStateOf<Job?>(null) }
    val scale by animateFloatAsState(if (holding) 0.97f else 1f, NodMotion.snappy(), label = "s")
    val fill = MaterialTheme.colorScheme.error
    val currentOnConfirm by rememberUpdatedState(onConfirm)

    // Single entry point for both the gesture and accessibility actions, so it can only fire once.
    fun confirm() {
        if (confirmed) return
        confirmed = true
        job?.cancel()
        hintJob?.cancel()
        hinting = false
        haptics.confirm()
        scope.launch { progress.snapTo(1f) }
        currentOnConfirm()
    }

    fun hint() {
        if (confirmed) return
        hintJob?.cancel()
        hintJob = scope.launch {
            hinting = true
            launch {
                nudge.snapTo(0f)
                nudge.animateTo(0f, keyframes<Float> {
                    durationMillis = 360
                    6f at 60
                    -5f at 140
                    3f at 220
                    -1f at 300
                })
            }
            delay(1400)
            hinting = false
        }
    }

    Box(
        modifier
            // Outermost, so nothing below can replace or swallow these actions.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = text
                onClick(label = actionLabel) { confirm(); true }
                onLongClick(label = actionLabel) { confirm(); true }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = nudge.value * density }
            .height(58.dp)
            .clip(CircleShape)
            .background(c.cardHigh)
            .drawBehind {
                drawRoundRect(
                    color = fill.copy(alpha = 0.9f),
                    size = Size(size.width * progress.value, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (confirmed) return@detectTapGestures
                        holding = true
                        haptics.click()
                        job = scope.launch {
                            progress.animateTo(1f, tween((durationMs * (1f - progress.value)).toInt(), easing = LinearEasing))
                            job = null // don't let confirm() cancel this very coroutine
                            confirm()
                        }
                        tryAwaitRelease()
                        holding = false
                        if (!confirmed) {
                            job?.cancel()
                            scope.launch { progress.animateTo(0f, NodMotion.snappy()) }
                        }
                    },
                    // A release before the fill completes: explain instead of silently doing nothing.
                    onTap = { if (!confirmed) hint() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(holding || hinting, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "t") { h ->
            Text(
                if (h) holdingText else text,
                style = MaterialTheme.typography.labelLarge,
                color = if (h && progress.value > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---- Slide to confirm ----------------------------------------------------------------------

/**
 * Drag the knob to the end to confirm. Accessibility services can't drag, so the outermost node
 * exposes click and long-click actions that run the same confirm (knob animates across first).
 * A plain tap on the track nudges the knob as a hint and never confirms.
 */
@Composable
fun SlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    track: Color,
    knob: Color,
    onKnob: Color,
    textColor: Color,
    actionLabel: String = text,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    var maxPx by remember { mutableFloatStateOf(0f) }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val knobSize = 64.dp

    // Single entry point for the drag and accessibility actions, so it can only fire once.
    fun confirm() {
        if (done) return
        done = true
        haptics.confirm()
        scope.launch { if (maxPx > 0f) offset.animateTo(maxPx, NodMotion.snappy()) }
        currentOnConfirm()
    }

    BoxWithConstraints(
        modifier
            // Outermost, so the knob's drag handler can't replace or swallow these actions.
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = text
                onClick(label = actionLabel) { confirm(); true }
                onLongClick(label = actionLabel) { confirm(); true }
            }
            .fillMaxWidth()
            .height(76.dp)
            .clip(CircleShape)
            .background(track)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (done || offset.isRunning) return@detectTapGestures
                    haptics.tick()
                    scope.launch {
                        offset.animateTo(maxPx * 0.18f, tween(160))
                        offset.animateTo(0f, NodMotion.bouncy())
                    }
                })
            }
            .padding(6.dp),
    ) {
        val travel = with(LocalDensity.current) { (maxWidth - knobSize).toPx() }.coerceAtLeast(0f)
        SideEffect { maxPx = travel } // read by the outer (semantics/tap) handlers
        val frac = if (travel > 0) offset.value / travel else 0f
        var lastTick by remember { mutableFloatStateOf(0f) }
        Text(
            text,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = 1f - frac * 1.6f },
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
        )
        // Lit trail behind the knob.
        Box(
            Modifier
                .fillMaxHeight()
                .width(knobSize + with(LocalDensity.current) { offset.value.toDp() })
                .clip(CircleShape)
                .background(knob.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knobSize)
                .clip(CircleShape)
                .background(knob)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > travel * 0.82f && !done) {
                                    confirm()
                                } else if (!done) {
                                    offset.animateTo(0f, NodMotion.bouncy())
                                }
                            }
                        },
                    ) { change, drag ->
                        if (done) return@detectHorizontalDragGestures
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, travel)) }
                        val f = if (travel > 0) offset.value / travel else 0f
                        if (kotlin.math.abs(f - lastTick) > 0.12f) { lastTick = f; haptics.tick() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = onKnob, modifier = Modifier.size(26.dp))
        }
    }
}

// ---- Segmented toggle ----------------------------------------------------------------------

@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(c.cardHigh)
            .padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(w * index, NodMotion.snappy(), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(if (c.isDark) c.accent else MaterialTheme.colorScheme.surfaceContainerLowest),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { o ->
                val isSel = o == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (!isSel) { haptics.tick(); onSelect(o) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(o),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            isSel && c.isDark -> c.onAccent
                            isSel -> MaterialTheme.colorScheme.onSurface
                            else -> c.muted
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---- Rolling number ------------------------------------------------------------------------

/**
 * Each character rolls up or down independently, like a split-flap board but smooth.
 *
 * The per-character nodes are hidden from accessibility; the whole value is exposed once as
 * [contentDescription] (pass e.g. "1.6 mi" to include the unit and hide the separate unit label).
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, contentDescription: String = text) {
    var previous by remember { mutableStateOf(text) }
    val goingDown = remember(text) { (text.filter(Char::isDigit).toLongOrNull() ?: 0) < (previous.filter(Char::isDigit).toLongOrNull() ?: 0) }
    LaunchedEffect(text) { previous = text }
    Row(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (goingDown) -1 else 1
                    (slideInVertically(NodMotion.snappy()) { h -> h * dir } + fadeIn()) togetherWith
                        (slideOutVertically(NodMotion.snappy()) { h -> -h * dir } + fadeOut())
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

// ---- Small bits ----------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = NodTheme.colors.muted,
    )
}

fun PlaceKind.icon(): ImageVector = when (this) {
    PlaceKind.TRAIN -> Icons.Rounded.Train
    PlaceKind.METRO -> Icons.Rounded.Subway
    PlaceKind.TRAM -> Icons.Rounded.Tram
    PlaceKind.BUS -> Icons.Rounded.DirectionsBus
    PlaceKind.FERRY -> Icons.Rounded.DirectionsBoat
    PlaceKind.CITY -> Icons.Rounded.LocationCity
    PlaceKind.PLACE -> Icons.Rounded.Place
}

@Composable
fun KindBadge(kind: PlaceKind, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp, highlighted: Boolean = false) {
    val c = NodTheme.colors
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(if (highlighted) c.accent else c.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            kind.icon(), null,
            tint = if (highlighted) c.onAccent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = NodTheme.colors.cardHigh, content: Color = MaterialTheme.colorScheme.onSurface, leading: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = content, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

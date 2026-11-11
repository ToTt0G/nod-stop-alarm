package app.nod.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.nod.R
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SheetDetent { Peek, Expanded }

/**
 * State of a [MapSheet]: which detent it is heading for, and how far between Peek (0) and
 * Expanded (1) it currently is. Heights are measured by the sheet itself.
 */
@Stable
class MapSheetState(initial: SheetDetent) {
    /** The detent the sheet rests on, or is animating towards. */
    var detent by mutableStateOf(initial)
        private set

    /** 0 = Peek, 1 = Expanded. Can overshoot slightly while the spring settles. */
    var progress by mutableFloatStateOf(if (initial == SheetDetent.Expanded) 1f else 0f)
        private set

    var peekPx by mutableIntStateOf(0)
        internal set
    var expandedPx by mutableIntStateOf(0)
        internal set

    val isMeasured: Boolean get() = peekPx > 0 && expandedPx > 0

    /** Current on-screen height in px, following drags and the spring frame by frame. */
    val heightPx: Float get() = (peekPx + (expandedPx - peekPx) * progress).coerceAtLeast(0f)

    /** Height once the sheet settles on [detent]. Use this for camera padding, not [heightPx]. */
    val targetHeightPx: Int get() = if (detent == SheetDetent.Expanded) expandedPx else peekPx

    private val mutex = MutatorMutex()

    suspend fun animateTo(target: SheetDetent, velocity: Float = 0f) {
        detent = target
        val to = if (target == SheetDetent.Expanded) 1f else 0f
        mutex.mutate {
            animate(progress, to, velocity, NodMotion.snappy()) { v, _ -> progress = v }
        }
    }

    suspend fun expand() = animateTo(SheetDetent.Expanded)
    suspend fun collapse() = animateTo(SheetDetent.Peek)

    /** A finger caught the sheet: stop any running spring. */
    internal suspend fun stop() = mutex.mutate(MutatePriority.UserInput) {}

    private val range: Float get() = (expandedPx - peekPx).toFloat()

    internal fun dragBy(deltaPx: Float) {
        if (range <= 0f) return
        progress = (progress - deltaPx / range).coerceIn(0f, 1f)
    }

    internal fun settleTarget(velocityPx: Float, flingPx: Float): SheetDetent = when {
        velocityPx < -flingPx -> SheetDetent.Expanded
        velocityPx > flingPx -> SheetDetent.Peek
        progress >= 0.5f -> SheetDetent.Expanded
        else -> SheetDetent.Peek
    }

    /** Drag velocity (px/s, down is positive) in progress units per second. */
    internal fun progressVelocity(velocityPx: Float): Float = if (range > 0f) -velocityPx / range else 0f

    companion object {
        val Saver: Saver<MapSheetState, String> = Saver(
            save = { it.detent.name },
            restore = { MapSheetState(SheetDetent.valueOf(it)) },
        )
    }
}

@Composable
fun rememberMapSheetState(initial: SheetDetent = SheetDetent.Expanded): MapSheetState =
    rememberSaveable(saver = MapSheetState.Saver) { MapSheetState(initial) }

private val HandleArea = 24.dp

/**
 * Bottom sheet over a map with two detents: a compact [peek] bar and the full [content].
 *
 * The whole surface is draggable between detents (spring settle, haptic tick on a detent change),
 * and it consumes touches so nothing behind it pans the map. Tapping the peek bar expands it; the
 * handle toggles. Both expose expand/collapse accessibility actions. Navigation-bar insets are
 * applied inside, so the card's background runs to the bottom edge behind the gesture bar.
 */
@Composable
fun MapSheet(
    state: MapSheetState,
    peekDescription: String,
    peek: @Composable (expand: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { 500.dp.toPx() }
    val expandLabel = stringResource(R.string.map_expand)
    val collapseLabel = stringResource(R.string.map_collapse)
    val handleLabel = stringResource(R.string.map_sheet_handle)
    val expandedLabel = stringResource(R.string.map_sheet_expanded)
    val collapsedLabel = stringResource(R.string.map_sheet_collapsed)
    val expanded = state.detent == SheetDetent.Expanded

    fun go(target: SheetDetent, velocity: Float = 0f) {
        if (target != state.detent) haptics.tick()
        scope.launch { state.animateTo(target, velocity) }
    }

    val showFull by remember(state) { derivedStateOf { state.progress > 0.01f } }
    val showPeek by remember(state) { derivedStateOf { state.progress < 0.99f } }
    val insets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val peekInteraction = remember { MutableInteractionSource() }
    val handleInteraction = remember { MutableInteractionSource() }
    val dragState = rememberDraggableState { delta -> state.dragBy(delta) }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(0.4f), spotColor = Color.Black.copy(0.4f))
            .clip(shape)
            .background(c.card)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { state.stop() },
                onDragStopped = { velocity -> go(state.settleTarget(velocity, flingPx), state.progressVelocity(velocity)) },
            ),
        content = {
            // 0: the full panel. Hidden from accessibility while collapsed.
            Box(
                Modifier
                    .graphicsLayer { alpha = ((state.progress - 0.3f) / 0.7f).coerceIn(0f, 1f) }
                    .then(if (showFull) Modifier else Modifier.clearAndSetSemantics {})
                    .windowInsetsPadding(insets)
                    .padding(top = HandleArea),
            ) { content() }

            // 1: the compact bar. Tap anywhere on it (outside its own buttons) to expand.
            Box(
                Modifier
                    .graphicsLayer { alpha = (1f - state.progress / 0.6f).coerceIn(0f, 1f) }
                    .then(
                        if (showPeek) {
                            Modifier
                                .clickable(
                                    interactionSource = peekInteraction,
                                    indication = null,
                                    onClickLabel = expandLabel,
                                    role = Role.Button,
                                ) { go(SheetDetent.Expanded) }
                                .semantics {
                                    contentDescription = peekDescription
                                    stateDescription = collapsedLabel
                                    expand(expandLabel) { go(SheetDetent.Expanded); true }
                                }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    )
                    .windowInsetsPadding(insets)
                    .padding(top = HandleArea),
            ) { peek { go(SheetDetent.Expanded) } }

            // 2: the handle, drawn on top of both.
            Box(Modifier.fillMaxWidth().height(HandleArea), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 72.dp, height = HandleArea)
                        .clickable(
                            interactionSource = handleInteraction,
                            indication = null,
                            onClickLabel = if (expanded) collapseLabel else expandLabel,
                            role = Role.Button,
                        ) { go(if (expanded) SheetDetent.Peek else SheetDetent.Expanded) }
                        .semantics {
                            contentDescription = handleLabel
                            stateDescription = if (expanded) expandedLabel else collapsedLabel
                            if (expanded) {
                                collapse(collapseLabel) { go(SheetDetent.Peek); true }
                            } else {
                                expand(expandLabel) { go(SheetDetent.Expanded); true }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(c.hairline))
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val child = Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight)
        val full = measurables[0].measure(child)
        val bar = measurables[1].measure(child)
        val handle = measurables[2].measure(child)
        state.expandedPx = full.height
        state.peekPx = bar.height
        val p = state.progress
        val h = (bar.height + (full.height - bar.height) * p).roundToInt().coerceIn(0, constraints.maxHeight)
        layout(w, h) {
            // Only what can be seen is placed, so only it can be touched.
            if (p > 0.01f) full.place(0, 0)
            if (p < 0.99f) bar.place(0, 0)
            handle.place(0, 0)
        }
    }
}

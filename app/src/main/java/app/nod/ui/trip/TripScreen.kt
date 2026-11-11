package app.nod.ui.trip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nod.R
import app.nod.core.Format
import app.nod.core.Geo
import app.nod.data.model.TriggerMode
import app.nod.data.model.Units
import app.nod.data.settings.SettingsRepository
import app.nod.trip.TripController
import app.nod.trip.TripEngine
import app.nod.trip.TripNotifications
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.HoldToConfirm
import app.nod.ui.components.LiveDot
import app.nod.ui.components.RollingText
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.bounceClick
import app.nod.ui.map.NodMap
import app.nod.ui.map.TripLayers
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import javax.inject.Inject
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntOffset
import app.nod.core.Measure
import app.nod.ui.components.MapSheet
import app.nod.ui.components.SheetDetent
import app.nod.ui.components.rememberMapSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@HiltViewModel
class TripViewModel @Inject constructor(
    holder: TripStateHolder,
    settings: SettingsRepository,
    private val controller: TripController,
) : ViewModel() {
    val state: StateFlow<TripState> = holder.state
    val units: StateFlow<Units> = settings.settings.map { it.units }.stateIn(viewModelScope, SharingStarted.Eagerly, Units.METRIC)
    fun stop() = controller.stop()
}

/** How long auto-follow stays paused after you move the map yourself (unless you tap Recenter). */
private const val FOLLOW_PAUSE_MS = 30_000L

@Composable
fun TripScreen(dark: Boolean, onClose: () -> Unit, vm: TripViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val trip = state as? TripState.Active
    val c = NodTheme.colors
    val density = LocalDensity.current
    val context = LocalContext.current

    LaunchedEffect(trip == null) { if (trip == null) onClose() }
    if (trip == null) return

    val scope = rememberCoroutineScope()
    val sheet = rememberMapSheetState()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var topBarPx by remember { mutableIntStateOf(0) }
    // Auto-follow keeps you and the zone in frame. Moving the map yourself pauses it until you tap
    // Recenter, or FOLLOW_PAUSE_MS after your last touch.
    var following by rememberSaveable { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    val dest = trip.plan.destination
    val live = trip.live

    // Reliability checks refresh when coming back from system settings.
    var fullScreenOk by remember { mutableStateOf(TripNotifications.canUseFullScreen(context)) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        fullScreenOk = TripNotifications.canUseFullScreen(context)
        batteryOk = isIgnoringBattery(context)
    }

    LaunchedEffect(style, dest) {
        val s = style ?: return@LaunchedEffect
        TripLayers.setZone(s, dest.lat, dest.lon, dest.radiusM.toDouble())
    }
    LaunchedEffect(style, live?.lat, live?.lon) {
        val s = style ?: return@LaunchedEffect
        live?.let { TripLayers.setMe(s, it.lat, it.lon, dest.lat, dest.lon) }
    }
    // Each touch restarts the pause; when it runs out, follow resumes on its own.
    LaunchedEffect(following, touchedAt) {
        if (following) return@LaunchedEffect
        delay(FOLLOW_PAUSE_MS)
        following = true
    }
    // Keep both you and the zone in frame, above the sheet's resting height, while following.
    LaunchedEffect(map, live?.lat, live?.lon, sheet.targetHeightPx, topBarPx, following) {
        val m = map ?: return@LaunchedEffect
        if (!following || !sheet.isMeasured) return@LaunchedEffect
        delay(80) // coalesce: a resizing panel or a burst of fixes just restarts this
        val n = Geo.offset(dest.lat, dest.lon, dest.radiusM * 1.15, 0.0)
        val s = Geo.offset(dest.lat, dest.lon, dest.radiusM * 1.15, 180.0)
        val e = Geo.offset(dest.lat, dest.lon, dest.radiusM * 1.15, 90.0)
        val w = Geo.offset(dest.lat, dest.lon, dest.radiusM * 1.15, 270.0)
        val b = LatLngBounds.Builder()
            .include(LatLng(n.lat, n.lon)).include(LatLng(s.lat, s.lon))
            .include(LatLng(e.lat, e.lon)).include(LatLng(w.lat, w.lon))
        live?.let { b.include(LatLng(it.lat, it.lon)) }
        // Room for your dot's halo at the edges, and for the Recenter pill above the sheet.
        val pad = with(density) { 56.dp.toPx() }.toInt()
        val top = (if (topBarPx > 0) topBarPx else with(density) { 104.dp.toPx() }.toInt()) + with(density) { 32.dp.toPx() }.toInt()
        val bottom = sheet.targetHeightPx + with(density) { 96.dp.toPx() }.toInt()
        runCatching {
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), pad, top, pad, bottom), 900)
        }
    }

    val alarmText = when {
        trip.phase == TripPhase.ALARM -> stringResource(R.string.now)
        live?.alarmInSec != null -> "~" + Format.duration(live.alarmInSec)
        trip.plan.triggerMode == TriggerMode.TIME -> stringResource(R.string.min_before, trip.plan.minutesBefore)
        else -> stringResource(R.string.on_entry)
    }
    val distance = live?.let { Format.distance(it.distanceM, units) }
    val peekDescription = stringResource(
        R.string.map_peek_trip,
        distance?.toString() ?: stringResource(R.string.status_locating),
        alarmText,
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NodMap(
            dark = dark,
            background = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
            attributionBottomPx = sheet.targetHeightPx,
            onMap = { m ->
                m.addOnCameraMoveStartedListener { r ->
                    // Only the user's own gestures; our own camera moves don't pause anything.
                    if (r == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        following = false
                        touchedAt = System.currentTimeMillis()
                        if (sheet.detent != SheetDetent.Peek) scope.launch { sheet.collapse() }
                    }
                }
                map = m
            },
            onStyle = { _, s ->
                TripLayers.install(s, c.zone, app.nod.ui.theme.NodPalette.Ink, c.isDark)
                style = s
            },
        )
        // Soft scrim so the top controls read above the map. Draw-only: touches pass through.
        Box(
            Modifier.fillMaxWidth().height(180.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), Color.Transparent))),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topBarPx = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.minimize), onClose)
            Spacer(Modifier.weight(1f))
            StatusPill(trip)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        // Shown only once you've moved the map; rides just above the sheet.
        AnimatedVisibility(
            visible = !following,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -sheet.heightPx.roundToInt()) }
                .padding(bottom = 14.dp),
        ) {
            RecenterPill { following = true }
        }

        MapSheet(
            state = sheet,
            peekDescription = peekDescription,
            peek = { expand -> TripPeek(distance, alarmText, onExpand = expand) },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 16.dp)) {
                SectionLabel(stringResource(R.string.heading_to))
                Spacer(Modifier.height(4.dp))
                Text(dest.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(live == null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "d") { locating ->
                        if (locating || live == null) {
                            Text("—", style = MaterialTheme.typography.displayLarge, color = c.muted)
                        } else {
                            val d = Format.distance(live.distanceM, units)
                            Row(verticalAlignment = Alignment.Bottom) {
                                RollingText(d.value, MaterialTheme.typography.displayLarge, MaterialTheme.colorScheme.onSurface, contentDescription = d.toString())
                                Spacer(Modifier.width(8.dp))
                                Text(d.unit, style = MaterialTheme.typography.headlineMedium, color = c.muted, modifier = Modifier.padding(bottom = 10.dp).clearAndSetSemantics {})
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                JourneyProgress(live?.progress ?: 0f, trip.phase)
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth()) {
                    Stat(stringResource(R.string.stat_alarm), alarmText, Modifier.weight(1.2f))
                    Stat(stringResource(R.string.stat_speed), live?.let { Format.speed(it.speedMps, units).toString() } ?: "—", Modifier.weight(1f))
                    Stat(stringResource(R.string.stat_zone), Format.distance(dest.radiusM.toDouble(), units).toString(), Modifier.weight(1f))
                }

                AnimatedVisibility(!fullScreenOk || !batteryOk, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        if (!fullScreenOk) {
                            Warning(stringResource(R.string.warn_full_screen)) { openFullScreenSettings(context) }
                        } else if (!batteryOk) {
                            Warning(stringResource(R.string.warn_battery)) { openBatterySettings(context) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, null, tint = c.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.trip_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Spacer(Modifier.height(16.dp))
                HoldToConfirm(
                    text = stringResource(R.string.hold_to_end),
                    holdingText = stringResource(R.string.keep_holding),
                    onConfirm = vm::stop,
                    modifier = Modifier.fillMaxWidth(),
                    actionLabel = stringResource(R.string.action_end_trip),
                )
            }
        }
    }
}

/** The collapsed sheet: how far, when the alarm goes, and an obvious way back to the details. */
@Composable
private fun TripPeek(distance: Measure?, alarm: String, onExpand: () -> Unit) {
    val c = NodTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Read out once via the bar's own description; the chevron stays its own button.
        Column(Modifier.weight(1f).clearAndSetSemantics {}) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (distance == null) {
                    Text("—", style = MaterialTheme.typography.headlineLarge, color = c.muted)
                } else {
                    RollingText(distance.value, MaterialTheme.typography.headlineLarge, MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(distance.unit, style = MaterialTheme.typography.titleMedium, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(
                stringResource(R.string.map_alarm_eta, alarm),
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        CircleIconButton(
            Icons.Rounded.KeyboardArrowUp,
            stringResource(R.string.map_trip_details),
            onExpand,
            elevated = false,
            container = c.cardHigh,
        )
    }
}

@Composable
private fun RecenterPill(onClick: () -> Unit) {
    val c = NodTheme.colors
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .height(48.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .clip(CircleShape)
            .background(c.card)
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NearMe, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.map_recenter), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusPill(trip: TripState.Active) {
    val c = NodTheme.colors
    val live = trip.live
    // TripPhase.HEADS_UP is sticky: once the heads-up has fired (a fast stretch, or a GPS speed spike),
    // the service never moves back to TRACKING. So only say "Almost there" while it's still true:
    // the alarm is really close in time, or (after the heads-up) we're within a zone's width of it.
    val alarmIn = live?.alarmInSec
    val shortEta = alarmIn != null &&
        alarmIn <= TripEngine.HEADS_UP_SEC * (if (trip.phase == TripPhase.HEADS_UP) 1.5 else 1.0)
    val nearZone = live != null && live.toZoneM <= trip.plan.destination.radiusM
    val almostThere = when (trip.phase) {
        TripPhase.HEADS_UP -> shortEta || nearZone
        TripPhase.TRACKING -> shortEta
        else -> false
    }
    val (label, color) = when {
        trip.phase == TripPhase.LOCATING -> stringResource(R.string.status_locating) to c.muted
        trip.phase == TripPhase.ALARM -> stringResource(R.string.status_arrived) to MaterialTheme.colorScheme.error
        almostThere -> stringResource(R.string.status_heads_up) to Color(0xFFFFB547)
        else -> stringResource(R.string.status_tracking) to c.zone
    }
    AnimatedContent(label, transitionSpec = { (fadeIn() + slideInVertically { -it / 2 }) togetherWith fadeOut() }, label = "status") { l ->
        Row(
            Modifier
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
                .clip(CircleShape)
                .background(c.card)
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(color, size = 8.dp)
            Text(l, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun JourneyProgress(progress: Float, phase: TripPhase) {
    val c = NodTheme.colors
    val p by animateFloatAsState(if (phase == TripPhase.ALARM) 1f else progress, NodMotion.gentle(), label = "p")
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.cardHigh))
        Box(Modifier.width(w * p.coerceIn(0.015f, 1f)).height(6.dp).clip(CircleShape).background(c.zone))
        Box(
            Modifier
                .offset(x = (w - 22.dp) * p)
                .size(22.dp)
                .clip(CircleShape)
                .background(c.zone),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(8.dp).clip(CircleShape).background(c.card)) }
        Icon(
            Icons.Rounded.Bedtime, null,
            tint = if (p > 0.97f) c.card else c.muted,
            modifier = Modifier.align(Alignment.CenterEnd).size(14.dp).offset(x = (-4).dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NodTheme.colors.muted, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stat") { v ->
            Text(v, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun Warning(text: String, onClick: () -> Unit) {
    val warn = Color(0xFFFFB547)
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(warn.copy(alpha = 0.14f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = warn, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
    }
}

fun isIgnoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@android.annotation.SuppressLint("BatteryLife")
fun openBatterySettings(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(direct) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
}

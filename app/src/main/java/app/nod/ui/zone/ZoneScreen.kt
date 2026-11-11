package app.nod.ui.zone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nod.R
import app.nod.core.Format
import app.nod.core.Geo
import app.nod.core.GeoPoint
import app.nod.core.RadiusScale
import app.nod.data.model.AlertStyle
import app.nod.data.model.TriggerMode
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.KindBadge
import app.nod.ui.components.MapSheet
import app.nod.ui.components.NodButton
import app.nod.ui.components.RollingText
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.SegmentedToggle
import app.nod.ui.components.SheetDetent
import app.nod.ui.components.bounceClick
import app.nod.ui.components.rememberHaptics
import app.nod.ui.components.rememberMapSheetState
import app.nod.ui.map.NodMap
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ZoneScreen(
    dark: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onStarted: () -> Unit,
    vm: ZoneViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = NodTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pinName = stringResource(R.string.dropped_pin)
    val sheet = rememberMapSheetState()

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var centerPx by remember { mutableStateOf(Offset.Unspecified) }
    var metersPerPx by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var topBarPx by remember { mutableIntStateOf(0) }
    var mapWidthPx by remember { mutableIntStateOf(0) }
    var mapHeightPx by remember { mutableIntStateOf(0) }
    var draggingRadius by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // Read in composition only when it flips, not on every camera frame.
    val overlayReady by remember { derivedStateOf { centerPx.isSpecified && metersPerPx > 0f } }

    fun measure(m: MapLibreMap) {
        val t = m.cameraPosition.target ?: return
        val p0 = m.projection.toScreenLocation(t)
        val e = Geo.offset(t.latitude, t.longitude, 1000.0, 90.0)
        val p1 = m.projection.toScreenLocation(LatLng(e.lat, e.lon))
        val px = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble())
        if (px > 0) metersPerPx = (1000.0 / px).toFloat()
        centerPx = Offset(p0.x, p0.y)
    }

    /**
     * Camera padding: the camera target sits in the middle of the map strip between the top bar and
     * the sheet's resting height. The pin is drawn at the target's screen point, so it is centred too.
     */
    fun cameraPadding(): DoubleArray = doubleArrayOf(0.0, topBarPx.toDouble(), 0.0, sheet.targetHeightPx.toDouble())

    fun fitZoom(m: MapLibreMap, center: GeoPoint, radius: Int, animate: Boolean) {
        // Fit whichever is tighter: the width, or the strip of map between the top bar and the sheet.
        val visibleH = mapHeightPx - sheet.targetHeightPx - with(density) { 140.dp.toPx() }
        val widthDp = with(density) { minOf(mapWidthPx.toFloat(), visibleH.coerceAtLeast(1f)).toDp().value.toDouble() }.takeIf { it > 1 } ?: 400.0
        val zoom = Geo.zoomForRadius(radius.toDouble(), center.lat, widthDp)
        val update = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(center.lat, center.lon))
                .zoom(zoom)
                .padding(cameraPadding())
                .build(),
        )
        if (animate) m.animateCamera(update, 650) else m.moveCamera(update)
    }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.locateMe() }
    fun locate() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (ok) vm.locateMe() else locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Keep the pin and ring centred in the visible map as the sheet changes height. Only the padding
    // changes; the camera target (what the pin marks, and what gets saved) stays exactly where it is,
    // so map and pin glide together. Never while a finger is on the map or the radius handle: moving
    // the camera then would fight the gesture (and cancel its fling), so it waits for the camera idle.
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        snapshotFlow { Triple(sheet.targetHeightPx, topBarPx, moving || draggingRadius) }
            .collectLatest { (bottom, top, busy) ->
                if (busy || bottom <= 0) return@collectLatest
                delay(48) // coalesce bursts, e.g. the panel resizing while its content animates
                val cur = m.cameraPosition
                val want = doubleArrayOf(0.0, top.toDouble(), 0.0, bottom.toDouble())
                if (cur.target == null || cur.padding?.contentEquals(want) == true) return@collectLatest
                m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(cur).padding(want).build()), 420)
            }
    }
    // Jump the camera when the view model asks (initial place, "my location"), once the sheet is measured.
    LaunchedEffect(map, ui.cameraTick, sheet.isMeasured) {
        val m = map ?: return@LaunchedEffect
        if (ui.cameraTick == 0 || !sheet.isMeasured) return@LaunchedEffect
        fitZoom(m, ui.center, ui.radiusM, animate = ui.cameraTick > 1)
        measure(m)
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val loc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (loc) { vm.start(pinName); onStarted() } else showPermissionDialog = true
    }
    fun start() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && notif) { vm.start(pinName); onStarted() } else launcher.launch(permissions)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NodMap(
            dark = dark,
            background = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize().onSizeChanged { mapWidthPx = it.width; mapHeightPx = it.height },
            attributionBottomPx = sheet.targetHeightPx,
            onMap = { m ->
                m.addOnCameraMoveStartedListener { reason ->
                    // Only the user's own gestures; programmatic moves (fit, padding glide) are ignored.
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        moving = true
                        // Get out of the way the moment the map is touched.
                        if (sheet.detent != SheetDetent.Peek) scope.launch { sheet.collapse() }
                    }
                }
                m.addOnCameraMoveListener { measure(m) }
                m.addOnCameraIdleListener {
                    measure(m)
                    if (moving) {
                        moving = false
                        haptics.tick()
                        m.cameraPosition.target?.let { vm.onCameraIdle(GeoPoint(it.latitude, it.longitude)) }
                    }
                }
                map = m
            },
        )

        // Draw-only overlay: nothing here takes touches except the small radius handle.
        if (ui.ready && overlayReady) {
            ZoneOverlay(
                center = { centerPx },
                ringPx = { ui.radiusM / metersPerPx },
                moving = moving,
                dragging = draggingRadius,
                radiusLabel = Format.distance(ui.radiusM.toDouble(), ui.units).toString(),
                onDragStart = { draggingRadius = true; haptics.click() },
                onDrag = { px ->
                    val r = RadiusScale.snap((px * metersPerPx).toDouble())
                    if (r != ui.radiusM) {
                        if (Format.distance(r.toDouble(), ui.units) != Format.distance(ui.radiusM.toDouble(), ui.units)) haptics.tick()
                        vm.setRadius(r)
                    }
                },
                onDragEnd = {
                    draggingRadius = false
                    map?.let { fitZoom(it, ui.center, ui.radiusM, animate = true) }
                },
            )
        }

        // Top controls
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topBarPx = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .weight(1f)
                    .bounceClick(pressedScale = 0.97f, onClick = onSearch)
                    .height(48.dp)
                    .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
                    .clip(CircleShape)
                    .background(c.card)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1)
            }
        }

        // Floating map controls, riding just above the sheet.
        AnimatedVisibility(
            visible = ui.ready,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(0, -sheet.heightPx.roundToInt()) }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(end = 16.dp, bottom = 14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CircleIconButton(Icons.Rounded.CenterFocusStrong, stringResource(R.string.map_fit_zone), onClick = {
                    map?.let { fitZoom(it, ui.center, ui.radiusM, animate = true) }
                })
                CircleIconButton(Icons.Rounded.MyLocation, stringResource(R.string.my_location), onClick = { locate() })
            }
        }

        // Bottom sheet
        AnimatedVisibility(
            visible = ui.ready,
            enter = slideInVertically(NodMotion.snappy()) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val placeName = if (ui.resolving) stringResource(R.string.finding_place) else ui.name.ifBlank { pinName }
            val radiusText = Format.distance(ui.radiusM.toDouble(), ui.units).toString()
            val canStart = !ui.resolving || ui.name.isNotBlank()
            MapSheet(
                state = sheet,
                peekDescription = stringResource(R.string.map_peek_zone, placeName, radiusText),
                peek = { _ ->
                    ZonePeek(ui = ui, name = placeName, radius = radiusText, canStart = canStart, onStart = { start() })
                },
            ) {
                ZoneSheet(
                    ui = ui,
                    pinName = pinName,
                    onTrigger = vm::setTrigger,
                    onMinutes = vm::setMinutes,
                    onRadius = { r -> vm.setRadius(r) },
                    onRadiusDone = { map?.let { fitZoom(it, ui.center, ui.radiusM, animate = true) } },
                    onStyle = vm::setAlertStyle,
                    onSave = { haptics.click(); vm.toggleSaved(pinName) },
                    onStart = { start() },
                )
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.perm_location_title)) },
            text = { Text(stringResource(R.string.perm_location_denied)) },
            confirmButton = {
                TextButton({
                    showPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton({ showPermissionDialog = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = c.card,
        )
    }
}

/**
 * The zone ring, the fixed centre pin and the radius handle. Positions are read through lambdas in
 * the draw and layout phases, so following the camera doesn't recompose anything. Only the 40 dp
 * handle has pointer input; it sits above the map, so a drag that starts on it never pans the map.
 */
@Composable
private fun ZoneOverlay(
    center: () -> Offset,
    ringPx: () -> Float,
    moving: Boolean,
    dragging: Boolean,
    radiusLabel: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = NodTheme.colors
    val zone = c.zone
    val density = LocalDensity.current
    val lift by animateDpAsState(if (moving) 14.dp else 0.dp, if (moving) NodMotion.snappy() else NodMotion.bouncy(), label = "lift")
    val ringAlpha by animateFloatAsState(if (moving) 0.55f else 1f, label = "ra")
    val breath = remember { Animatable(1f) }
    LaunchedEffect(moving) {
        if (!moving) {
            breath.snapTo(0f)
            breath.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    val currentCenter by rememberUpdatedState(center)
    val currentRing by rememberUpdatedState(ringPx)
    val currentDragging by rememberUpdatedState(dragging)
    val dragStart by rememberUpdatedState(onDragStart)
    val dragBy by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)

    val handle = 40.dp
    val handlePx = with(density) { handle.toPx() }
    val edgePx = with(density) { 6.dp.toPx() }
    val minRingPx = with(density) { 24.dp.toPx() }
    val showHandle by remember { derivedStateOf { currentRing() > minRingPx || currentDragging } }

    var overlayWidth by remember { mutableIntStateOf(0) }
    fun handleX(): Float {
        val x = currentCenter().x + currentRing()
        return if (overlayWidth > 0) minOf(x, overlayWidth - handlePx / 2 - edgePx) else x
    }

    Box(Modifier.fillMaxSize().onSizeChanged { overlayWidth = it.width }) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = currentCenter()
            val r = currentRing()
            drawCircle(zone.copy(alpha = (if (c.isDark) 0.13f else 0.16f) * ringAlpha), radius = r, center = ctr)
            drawCircle(zone.copy(alpha = ringAlpha), radius = r, center = ctr, style = Stroke(2.dp.toPx()))
            if (breath.value < 1f) {
                drawCircle(
                    zone.copy(alpha = (1f - breath.value) * 0.6f),
                    radius = r * (1f + 0.12f * breath.value),
                    center = ctr,
                    style = Stroke((1f + 3f * (1f - breath.value)) * density.density),
                )
            }
            // Ground shadow under the pin; shrinks as the pin lifts.
            val liftPx = lift.toPx()
            drawOval(
                Color.Black.copy(alpha = 0.25f - liftPx / 200f),
                topLeft = Offset(ctr.x - (7.dp.toPx() - liftPx * 0.15f), ctr.y - 2.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((14.dp.toPx() - liftPx * 0.3f), 5.dp.toPx()),
            )
        }

        // The pin: its tip is exactly the camera target, the point that gets saved.
        val pinW = 30.dp
        val pinH = 46.dp
        Canvas(
            Modifier
                .offset {
                    val ctr = currentCenter()
                    IntOffset((ctr.x - pinW.toPx() / 2).roundToInt(), (ctr.y - pinH.toPx() - lift.toPx()).roundToInt())
                }
                .size(pinW, pinH),
        ) {
            val r = size.width / 2
            drawLine(c.cta, Offset(r, r * 1.6f), Offset(r, size.height), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.18f), radius = r, center = Offset(r, r + 2.dp.toPx()))
            drawCircle(c.cta, radius = r, center = Offset(r, r))
            drawCircle(c.onCta, radius = r * 0.36f, center = Offset(r, r))
        }

        // Radius handle on the ring's right edge.
        if (showHandle) {
            val scale by animateFloatAsState(if (dragging) 1.18f else if (moving) 0.8f else 1f, NodMotion.bouncy(), label = "hs")
            Box(
                Modifier
                    .offset { IntOffset((handleX() - handlePx / 2).roundToInt(), (currentCenter().y - handlePx / 2).roundToInt()) }
                    .size(handle)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.card)
                    .pointerInput(Unit) {
                        var start = 0f
                        var acc = 0f
                        detectDragGestures(
                            onDragStart = { start = currentRing(); acc = 0f; dragStart() },
                            onDragEnd = { dragEnd() },
                            onDragCancel = { dragEnd() },
                        ) { change, amount ->
                            change.consume()
                            acc += amount.x
                            dragBy((start + acc).coerceAtLeast(8f))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.UnfoldMore, stringResource(R.string.resize_zone), tint = zone, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
            }
            AnimatedVisibility(
                visible = dragging,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.offset { IntOffset((handleX() - 36.dp.toPx()).roundToInt(), (currentCenter().y - 62.dp.toPx()).roundToInt()) },
            ) {
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        radiusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onCta,
                        modifier = Modifier.clip(CircleShape).background(c.cta).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** The collapsed sheet: where, how big, and a way to go without expanding. */
@Composable
private fun ZonePeek(ui: ZoneUi, name: String, radius: String, canStart: Boolean, onStart: () -> Unit) {
    val c = NodTheme.colors
    val zoneText = stringResource(R.string.map_zone_radius, radius)
    val timeText = stringResource(R.string.min_before, ui.minutes)
    val detail = if (ui.trigger == TriggerMode.TIME) "$zoneText · $timeText" else zoneText
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Read out once via the bar's own description; the button below stays its own stop.
        Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            KindBadge(ui.kind, size = 40.dp, highlighted = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ui.resolving) c.muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        NodButton(text = stringResource(R.string.start_trip), onClick = onStart, enabled = canStart)
    }
}

@Composable
private fun ZoneSheet(
    ui: ZoneUi,
    pinName: String,
    onTrigger: (TriggerMode) -> Unit,
    onMinutes: (Int) -> Unit,
    onRadius: (Int) -> Unit,
    onRadiusDone: () -> Unit,
    onStyle: (AlertStyle) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(ui.kind, highlighted = true)
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                targetState = Triple(ui.name, ui.subtitle, ui.resolving),
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
                label = "name",
            ) { (name, sub, resolving) ->
                Column {
                    Text(
                        if (resolving) stringResource(R.string.finding_place) else name.ifBlank { pinName },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (resolving) c.muted else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (resolving) " " else sub.ifBlank { stringResource(R.string.move_map_hint) },
                        style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val saved = ui.savedId != null
            val heartScale = remember { Animatable(1f) }
            LaunchedEffect(saved) {
                if (saved) { heartScale.snapTo(0.6f); heartScale.animateTo(1f, NodMotion.bouncy()) }
            }
            CircleIconButton(
                if (saved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (saved) R.string.unsave else R.string.save),
                onSave,
                modifier = Modifier.graphicsLayer { scaleX = heartScale.value; scaleY = heartScale.value },
                elevated = false,
                container = c.cardHigh,
                tint = if (saved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.wake_me))
        Spacer(Modifier.height(10.dp))
        SegmentedToggle(
            options = TriggerMode.entries,
            selected = ui.trigger,
            onSelect = onTrigger,
            label = { stringResource(if (it == TriggerMode.DISTANCE) R.string.trigger_zone else R.string.trigger_time) },
        )
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = ui.trigger,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "trigger",
        ) { t ->
            when (t) {
                TriggerMode.DISTANCE -> Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.zone_radius), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        val d = Format.distance(ui.radiusM.toDouble(), ui.units)
                        RollingText(d.value, MaterialTheme.typography.headlineMedium, MaterialTheme.colorScheme.onSurface, contentDescription = d.toString())
                        Spacer(Modifier.width(4.dp))
                        Text(d.unit, style = MaterialTheme.typography.titleMedium, color = c.muted, modifier = Modifier.padding(bottom = 3.dp).clearAndSetSemantics {})
                    }
                    var lastLabel by remember { mutableStateOf("") }
                    Slider(
                        value = RadiusScale.toFraction(ui.radiusM.toDouble()),
                        onValueChange = {
                            val r = RadiusScale.fromFraction(it)
                            val label = Format.distance(r.toDouble(), ui.units).toString()
                            if (label != lastLabel) { lastLabel = label; haptics.tick() }
                            onRadius(r)
                        },
                        onValueChangeFinished = onRadiusDone,
                        colors = SliderDefaults.colors(
                            thumbColor = c.cta,
                            activeTrackColor = c.cta,
                            inactiveTrackColor = c.cardHigh,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                }
                TriggerMode.TIME -> Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(1, 2, 3, 5, 10, 15).forEach { m ->
                            val sel = m == ui.minutes
                            val bg by androidx.compose.animation.animateColorAsState(if (sel) c.cta else c.cardHigh, label = "m")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .bounceClick(pressedScale = 0.9f) { haptics.tick(); onMinutes(m) }
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$m", style = MaterialTheme.typography.titleMedium, color = if (sel) c.onCta else MaterialTheme.colorScheme.onSurface)
                                    Text("min", style = MaterialTheme.typography.bodySmall, color = if (sel) c.onCta.copy(0.7f) else c.muted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.trigger_time_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleOption(Icons.Rounded.NotificationsActive, stringResource(R.string.style_alarm), ui.alertStyle == AlertStyle.ALARM, Modifier.weight(1f)) { onStyle(AlertStyle.ALARM) }
            StyleOption(Icons.Rounded.Vibration, stringResource(R.string.style_vibrate), ui.alertStyle == AlertStyle.VIBRATE, Modifier.weight(1f)) { onStyle(AlertStyle.VIBRATE) }
            StyleOption(Icons.Rounded.NotificationsOff, stringResource(R.string.style_silent), ui.alertStyle == AlertStyle.SILENT, Modifier.weight(1f)) { onStyle(AlertStyle.SILENT) }
        }
        Spacer(Modifier.height(18.dp))
        NodButton(
            text = stringResource(R.string.start_trip),
            onClick = onStart,
            trailing = Icons.AutoMirrored.Rounded.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
            enabled = !ui.resolving || ui.name.isNotBlank(),
        )
    }
}

@Composable
private fun StyleOption(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    val border by androidx.compose.animation.animateColorAsState(if (selected) (if (c.isDark) c.accent else MaterialTheme.colorScheme.onSurface) else Color.Transparent, label = "b")
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.cardHigh else c.cardHigh.copy(alpha = 0.5f), label = "bg")
    Column(
        modifier
            .bounceClick(pressedScale = 0.94f) { haptics.tick(); onClick() }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onSurface else c.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onSurface else c.muted)
    }
}

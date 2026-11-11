package app.nod.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import app.nod.R
import app.nod.trip.TripNotifications
import app.nod.data.settings.SettingsRepository
import app.nod.ui.components.NodButton
import app.nod.ui.components.NodMark
import app.nod.ui.components.PulseRings
import app.nod.ui.components.bounceClick
import app.nod.ui.components.rememberHaptics
import app.nod.ui.theme.NodMotion
import app.nod.ui.theme.NodTheme
import app.nod.ui.trip.openBatterySettings
import app.nod.ui.trip.openFullScreenSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.sin

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun done(then: () -> Unit) = viewModelScope.launch { settings.setOnboarded(); then() }
}

private const val PAGES = 4

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val c = NodTheme.colors

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("nod", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(pager.currentPage < PAGES - 1, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    stringResource(R.string.skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.muted,
                    modifier = Modifier.clip(CircleShape).bounceClick { scope.launch { pager.animateScrollToPage(PAGES - 1) } }.padding(12.dp),
                )
            }
        }

        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            val fade = 1f - offset.absoluteValue.coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            // Illustrations drift slower than the page for a subtle parallax.
                            translationX = offset * size.width * 0.45f
                            alpha = fade
                            scaleX = lerp(0.9f, 1f, fade); scaleY = scaleX
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (page) {
                        0 -> NodMark(Modifier.size(220.dp), color = if (c.isDark) c.accent else c.cta, animate = pager.currentPage == 0, breathe = true)
                        1 -> ZoneIllustration(Modifier.size(280.dp))
                        2 -> BuzzIllustration(Modifier.size(260.dp))
                        else -> PermissionList(Modifier.fillMaxWidth())
                    }
                }
                Column(Modifier.graphicsLayer { alpha = fade; translationX = offset * 60f }) {
                    val (title, body) = when (page) {
                        0 -> R.string.onb1_title to R.string.onb1_text
                        1 -> R.string.onb2_title to R.string.onb2_text
                        2 -> R.string.onb3_title to R.string.onb3_text
                        else -> R.string.onb4_title to R.string.onb4_text
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(PAGES) { i ->
                    val sel = pager.currentPage == i
                    val w by animateDpAsState(if (sel) 26.dp else 8.dp, NodMotion.snappy(), label = "w")
                    val col by animateColorAsState(if (sel) MaterialTheme.colorScheme.onBackground else c.hairline, label = "c")
                    Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(col))
                }
            }
            Spacer(Modifier.weight(1f))
            val last = pager.currentPage == PAGES - 1
            AnimatedContent(last, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "cta") { isLast ->
                NodButton(
                    text = stringResource(if (isLast) R.string.get_started else R.string.next),
                    trailing = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = {
                        haptics.click()
                        if (isLast) vm.done(onDone) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoneIllustration(modifier: Modifier) {
    val c = NodTheme.colors
    val t = rememberInfiniteTransition(label = "zone")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val grid = c.hairline
    val ink = MaterialTheme.colorScheme.onBackground
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Faint street grid
            val step = size.width / 7
            for (i in 1..6) {
                drawLine(grid, Offset(step * i, 0f), Offset(step * i + 30f, size.height), strokeWidth = 2f)
                drawLine(grid, Offset(0f, step * i), Offset(size.width, step * i - 24f), strokeWidth = 2f)
            }
            val center = Offset(size.width * 0.64f, size.height * 0.38f)
            val r = size.width * 0.2f
            drawCircle(c.zone.copy(alpha = 0.16f), r, center)
            drawCircle(c.zone, r, center, style = Stroke(5f))
            // The route, drawn as a dotted curve, with a traveller moving along it.
            val path = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.92f)
                cubicTo(size.width * 0.2f, size.height * 0.5f, size.width * 0.45f, size.height * 0.75f, center.x, center.y)
            }
            drawPath(path, ink.copy(alpha = 0.35f), style = Stroke(4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 14f))))
            val pm = PathMeasure().apply { setPath(path, false) }
            val pos = pm.getPosition(pm.length * (p * 0.92f))
            val inside = (pos - center).getDistance() < r
            drawCircle(Color(0xFF3A9BFF).copy(alpha = 0.25f), 22f, pos)
            drawCircle(Color(0xFF3A9BFF), 11f, pos)
            drawCircle(Color.White, 11f, pos, style = Stroke(4f))
            if (inside) {
                val k = ((p * 0.92f - 0.8f) / 0.12f).coerceIn(0f, 1f)
                drawCircle(c.zone.copy(alpha = (1f - k) * 0.8f), r * (1f + 0.5f * k), center, style = Stroke(6f * (1f - k) + 1f))
            }
        }
    }
}

@Composable
private fun BuzzIllustration(modifier: Modifier) {
    val c = NodTheme.colors
    val ink = MaterialTheme.colorScheme.onBackground
    val t = rememberInfiniteTransition(label = "buzz")
    val shake by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "s")
    Box(modifier, contentAlignment = Alignment.Center) {
        PulseRings(c.zone, Modifier.size(260.dp), rings = 3, periodMs = 1600, strokeWidth = 2.dp)
        Canvas(Modifier.size(110.dp, 190.dp).graphicsLayer {
            val burst = shake < 0.35f
            rotationZ = if (burst) sin(shake * 120f) * 5f else 0f
        }) {
            drawRoundRect(if (c.isDark) c.cardHigh else Color.White, cornerRadius = CornerRadius(36f), size = size)
            drawRoundRect(ink, cornerRadius = CornerRadius(36f), size = size, style = Stroke(6f))
            drawRoundRect(c.zone, topLeft = Offset(16f, 40f), size = Size(size.width - 32f, size.height - 80f), cornerRadius = CornerRadius(18f))
            drawCircle(ink, 7f, Offset(size.width / 2, 22f))
        }
    }
}

@Composable
private fun PermissionList(modifier: Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = PermissionStatus.read(context) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        status = PermissionStatus.read(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = PermissionStatus.read(context)
        if (!it) context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionRow(Icons.Rounded.LocationOn, stringResource(R.string.perm_location), stringResource(R.string.perm_location_why), status.location, required = true) {
            locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        PermissionRow(Icons.Rounded.Notifications, stringResource(R.string.perm_notifications), stringResource(R.string.perm_notifications_why), status.notifications, required = true) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            PermissionRow(Icons.Rounded.Lock, stringResource(R.string.perm_live_updates), stringResource(R.string.perm_live_updates_why), status.liveUpdates, required = false) {
                TripNotifications.openLiveUpdateSettings(context)
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            PermissionRow(Icons.Rounded.PhoneAndroid, stringResource(R.string.perm_full_screen), stringResource(R.string.perm_full_screen_why), status.fullScreen, required = true) {
                openFullScreenSettings(context)
            }
        }
        PermissionRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.perm_battery), stringResource(R.string.perm_battery_why), status.battery, required = false) {
            openBatterySettings(context)
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, why: String, granted: Boolean, required: Boolean, onClick: () -> Unit) {
    val c = NodTheme.colors
    val bg by animateColorAsState(if (granted) c.cardHigh.copy(alpha = 0.5f) else c.card, label = "bg")
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(enabled = !granted, pressedScale = 0.98f, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, c.hairline, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (granted) c.accent else c.cardHigh), contentAlignment = Alignment.Center) {
            AnimatedContent(granted, transitionSpec = { scaleIn(NodMotion.bouncy()) togetherWith fadeOut() }, label = "ic") { g ->
                Icon(if (g) Icons.Rounded.Check else icon, null, tint = if (g) c.onAccent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (!required) {
                Text(stringResource(R.string.recommended), style = MaterialTheme.typography.labelSmall, color = if (c.isDark) c.accent else c.zone)
            }
            Text(why, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.allow),
                style = MaterialTheme.typography.labelMedium,
                color = c.onCta,
                modifier = Modifier.clip(CircleShape).background(c.cta).padding(horizontal = 14.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Suppress("unused")
private fun openAppSettings(context: android.content.Context) =
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

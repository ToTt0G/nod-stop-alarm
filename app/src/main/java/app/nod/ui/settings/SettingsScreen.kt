package app.nod.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nod.BuildConfig
import app.nod.R
import app.nod.trip.TripNotifications
import app.nod.alarm.AlarmActivity
import app.nod.data.model.AlertStyle
import app.nod.data.model.ThemeMode
import app.nod.data.model.Units
import app.nod.data.model.VibrationPattern
import app.nod.data.settings.Settings
import app.nod.data.settings.SettingsRepository
import app.nod.trip.AlarmPlayer
import app.nod.ui.components.CircleIconButton
import app.nod.ui.components.NodButton
import app.nod.ui.components.NodButtonStyle
import app.nod.ui.components.NodMark
import app.nod.ui.components.SectionLabel
import app.nod.ui.components.SegmentedToggle
import app.nod.ui.components.bounceClick
import app.nod.ui.components.rememberHaptics
import app.nod.ui.components.StatusBarScrim
import app.nod.ui.home.openNodRepo
import app.nod.ui.onboarding.PermissionStatus
import app.nod.ui.theme.NodTheme
import app.nod.ui.trip.openBatterySettings
import app.nod.ui.trip.openFullScreenSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val player: AlarmPlayer,
) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    fun theme(v: ThemeMode) = viewModelScope.launch { repo.setTheme(v) }
    fun units(v: Units) = viewModelScope.launch { repo.setUnits(v) }
    fun style(v: AlertStyle) = viewModelScope.launch { repo.setAlertStyle(v) }
    fun vibration(v: VibrationPattern) = viewModelScope.launch { repo.setVibration(v); player.previewVibration(v) }
    fun gradual(v: Boolean) = viewModelScope.launch { repo.setGradual(v) }
    fun headsUp(v: Boolean) = viewModelScope.launch { repo.setHeadsUp(v) }
    fun headphonesOnly(v: Boolean) = viewModelScope.launch { repo.setHeadphonesOnly(v) }
    fun speak(v: Boolean) = viewModelScope.launch { repo.setSpeak(v) }
    fun sound(uri: Uri?, name: String?) = viewModelScope.launch { repo.setSound(uri?.toString(), name) }
    fun previewSound() = player.previewSound(settings.value.soundUri)
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    var perms by remember { mutableStateOf(PermissionStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = PermissionStatus.read(context) }

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val uri: Uri? = res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val name = uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
        vm.sound(uri, name)
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + 32.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp)) {
                    CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), onBack, elevated = false, container = Color.Transparent)
                }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp))
            }

            item { SectionLabel(stringResource(R.string.section_alerts), Modifier.padding(start = 4.dp, top = 8.dp)) }
            item {
                Card {
                    Text(stringResource(R.string.default_alert), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    SegmentedToggle(
                        AlertStyle.entries, s.alertStyle, vm::style,
                        label = {
                            stringResource(
                                when (it) {
                                    AlertStyle.ALARM -> R.string.style_alarm
                                    AlertStyle.VIBRATE -> R.string.style_vibrate
                                    AlertStyle.SILENT -> R.string.style_silent
                                },
                            )
                        },
                    )
                }
            }
            item {
                Card(padding = 0.dp) {
                    Row_(Icons.Rounded.MusicNote, stringResource(R.string.alarm_sound), s.soundName ?: stringResource(R.string.default_sound), onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.soundUri?.let(Uri::parse))
                        runCatching { ringtone.launch(intent) }
                    }) {
                        CircleIconButton(Icons.Rounded.PlayArrow, stringResource(R.string.preview), vm::previewSound, size = 38.dp, elevated = false, container = c.cardHigh)
                    }
                    Divider()
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.GraphicEq)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(R.string.vibration), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.vibration_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VibrationPattern.entries.forEach { v ->
                                val sel = v == s.vibration
                                val bg by animateColorAsState(if (sel) c.cta else c.cardHigh, label = "v")
                                Box(
                                    Modifier.weight(1f).bounceClick(pressedScale = 0.92f) { haptics.tick(); vm.vibration(v) }
                                        .clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(stringResource(v.label()), style = MaterialTheme.typography.labelMedium, color = if (sel) c.onCta else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                }
                            }
                        }
                    }
                    Divider()
                    ToggleRow(Icons.Rounded.TrendingUp, stringResource(R.string.gradual_volume), stringResource(R.string.gradual_volume_hint), s.gradualVolume, vm::gradual)
                    Divider()
                    ToggleRow(Icons.Rounded.NotificationsActive, stringResource(R.string.heads_up), stringResource(R.string.heads_up_hint), s.headsUp, vm::headsUp)
                    Divider()
                    ToggleRow(Icons.Rounded.Headphones, stringResource(R.string.audio_headphones_only), stringResource(R.string.audio_headphones_only_hint), s.headphonesOnly, vm::headphonesOnly)
                    Divider()
                    ToggleRow(Icons.Rounded.RecordVoiceOver, stringResource(R.string.audio_speak), stringResource(R.string.audio_speak_hint), s.speak, vm::speak)
                }
            }
            item {
                NodButton(
                    stringResource(R.string.try_alarm),
                    onClick = { AlarmActivity.preview(context) },
                    style = NodButtonStyle.Secondary,
                    leading = Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionLabel(stringResource(R.string.section_widgets), Modifier.padding(start = 4.dp, top = 16.dp)) }
            item {
                Card(padding = 0.dp) {
                    WidgetRow(Icons.Rounded.Widgets, stringResource(R.string.widget_journey_name), stringResource(R.string.widget_journey_desc)) {
                        pinWidget(context, app.nod.widget.JourneyWidgetReceiver::class.java)
                    }
                    Divider()
                    WidgetRow(Icons.Rounded.DonutLarge, stringResource(R.string.widget_ring_name), stringResource(R.string.widget_ring_desc)) {
                        pinWidget(context, app.nod.widget.RingWidgetReceiver::class.java)
                    }
                    Divider()
                    WidgetRow(Icons.Rounded.TouchApp, stringResource(R.string.widget_quick_name), stringResource(R.string.widget_quick_desc)) {
                        pinWidget(context, app.nod.widget.QuickStopWidgetReceiver::class.java)
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.section_appearance), Modifier.padding(start = 4.dp, top = 16.dp)) }
            item {
                Card {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    SegmentedToggle(ThemeMode.entries, s.theme, vm::theme, label = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            },
                        )
                    })
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.units), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    SegmentedToggle(Units.entries, s.units, vm::units, label = {
                        stringResource(if (it == Units.METRIC) R.string.units_metric else R.string.units_imperial)
                    })
                }
            }

            item { SectionLabel(stringResource(R.string.section_reliability), Modifier.padding(start = 4.dp, top = 16.dp)) }
            item {
                Card(padding = 0.dp) {
                    StatusRow(Icons.Rounded.LocationOn, stringResource(R.string.perm_location), perms.location) {
                        context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                    Divider()
                    StatusRow(Icons.Rounded.Notifications, stringResource(R.string.perm_notifications), perms.notifications) {
                        context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        Divider()
                        StatusRow(Icons.Rounded.Lock, stringResource(R.string.perm_live_updates), perms.liveUpdates) {
                            TripNotifications.openLiveUpdateSettings(context)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        Divider()
                        StatusRow(Icons.Rounded.PhoneAndroid, stringResource(R.string.perm_full_screen), perms.fullScreen) { openFullScreenSettings(context) }
                    }
                    Divider()
                    StatusRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.perm_battery), perms.battery) { openBatterySettings(context) }
                }
            }

            item { SectionLabel(stringResource(R.string.section_about), Modifier.padding(start = 4.dp, top = 16.dp)) }
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NodMark(Modifier.size(44.dp), color = if (c.isDark) c.accent else c.cta)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Nod ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.about_free), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            item {
                Card(padding = 0.dp) {
                    Row_(Icons.Rounded.Star, stringResource(R.string.star_settings_title), stringResource(R.string.star_settings_subtitle), onClick = { openNodRepo(context) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
                    }
                }
            }
        }
        StatusBarScrim()
    }
}

private fun pinWidget(context: android.content.Context, receiver: Class<*>) {
    val mgr = android.appwidget.AppWidgetManager.getInstance(context)
    if (mgr.isRequestPinAppWidgetSupported) {
        mgr.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null)
    } else {
        android.widget.Toast.makeText(context, context.getString(R.string.widget_pin_unsupported), android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun WidgetRow(icon: ImageVector, title: String, hint: String, onAdd: () -> Unit) {
    val c = NodTheme.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.widget_add),
            style = MaterialTheme.typography.labelMedium,
            color = c.onCta,
            modifier = Modifier.bounceClick(pressedScale = 0.9f, onClick = onAdd).clip(CircleShape).background(c.cta).padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private fun VibrationPattern.label() = when (this) {
    VibrationPattern.PULSE -> R.string.vib_pulse
    VibrationPattern.HEARTBEAT -> R.string.vib_heartbeat
    VibrationPattern.WAVE -> R.string.vib_wave
    VibrationPattern.URGENT -> R.string.vib_urgent
}

@Composable
private fun Card(padding: androidx.compose.ui.unit.Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = NodTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).border(1.dp, c.hairline, RoundedCornerShape(24.dp)).padding(padding),
        content = content,
    )
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(NodTheme.colors.hairline))

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(NodTheme.colors.cardHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Row_(icon: ImageVector, title: String, value: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().bounceClick(pressedScale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodySmall, color = NodTheme.colors.muted, maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = NodTheme.colors
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().bounceClick(pressedScale = 0.99f) { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = { haptics.tick(); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.cta, checkedThumbColor = c.onCta, checkedBorderColor = c.cta,
                uncheckedTrackColor = c.cardHigh, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.hairline,
            ),
        )
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, ok: Boolean, onClick: () -> Unit) {
    val c = NodTheme.colors
    Row(
        Modifier.fillMaxWidth().bounceClick(pressedScale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (ok) R.string.status_on else R.string.status_fix),
            style = MaterialTheme.typography.labelMedium,
            color = if (ok) c.onAccent else MaterialTheme.colorScheme.onError,
            modifier = Modifier.clip(CircleShape).background(if (ok) c.accent else MaterialTheme.colorScheme.error).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.muted)
    }
}

package app.nod.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.nod.MainActivity
import app.nod.data.places.PlacesRepository
import app.nod.data.settings.SettingsRepository
import app.nod.trip.TripController
import app.nod.trip.TripNotifications
import app.nod.trip.TripStateHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun holder(): TripStateHolder
    fun places(): PlacesRepository
    fun settings(): SettingsRepository
    fun controller(): TripController
    fun sync(): WidgetSync
}

fun Context.widgetDeps(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

object WidgetColors {
    val bg = ColorProvider(Color(WidgetArt.INK))
    val card = ColorProvider(Color(0xFF1F2226))
    val lime = ColorProvider(Color(WidgetArt.LIME))
    val ink = ColorProvider(Color(WidgetArt.INK))
    val text = ColorProvider(Color(WidgetArt.WHITE))
    val muted = ColorProvider(Color(WidgetArt.MUTED))
    val track = ColorProvider(Color(WidgetArt.TRACK))
}

fun style(size: TextUnit = 14.sp, color: ColorProvider = WidgetColors.text, weight: FontWeight = FontWeight.Medium) =
    TextStyle(color = color, fontSize = size, fontWeight = weight)

fun GlanceModifier.surface(radius: Dp = 28.dp) = background(WidgetColors.bg).cornerRadius(radius)

/** Intents into the app. Each gets a unique data URI so PendingIntents don't collapse together. */
object WidgetIntents {
    fun open(context: Context) = Intent(context, MainActivity::class.java)
        .setData(Uri.parse("nod://open"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun trip(context: Context) = open(context).setData(Uri.parse("nod://trip")).putExtra(TripNotifications.EXTRA_OPEN_TRIP, true)
    fun search(context: Context) = open(context).setData(Uri.parse("nod://search")).putExtra(MainActivity.EXTRA_OPEN_SEARCH, true)
    fun start(context: Context, placeId: Long) = open(context).setData(Uri.parse("nod://start/$placeId")).putExtra(MainActivity.EXTRA_START_PLACE, placeId)
}

class EndTripAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.widgetDeps().controller().stop()
    }
}

package app.nod.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import app.nod.R
import app.nod.core.Format
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import kotlinx.coroutines.flow.first

/** A square, wallpaper-friendly ring that closes around the moon as you near your stop. */
class RingWidget : GlanceAppWidget() {
    // Exact: one image per size actually on screen, instead of three speculative ones per update.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        // compose() pushes the first frame, so cold flows must start from real values, not placeholders.
        val initialSettings = deps.settings().settings.first()
        provideContent {
            val trip by deps.sync().shown.collectAsState()
            val settings by deps.settings().settings.collectAsState(initial = initialSettings)
            val ctx = LocalContext.current
            val size = LocalSize.current
            val side = minOf(size.width, size.height)
            // Capped: past ~420 px the launcher just scales it, but every byte is still sent on each update.
            val px = (side.value * ctx.resources.displayMetrics.density).toInt().coerceIn(120, 420)
            // Android 12+ clips the widget to its rounded corners, so the image can be opaque there.
            val opaque = Build.VERSION.SDK_INT >= 31
            val active = trip as? TripState.Active
            val units = settings.units
            val bmp = if (active == null) {
                WidgetArt.ring(ctx, px, null, null, null, ctx.getString(R.string.widget_where_to), opaque)
            } else {
                val d = active.live?.let { Format.distance(it.distanceM, units) }
                val p = if (active.phase == TripPhase.ALARM) 1f else active.live?.progress ?: 0f
                WidgetArt.ring(ctx, px, (p * 100).toInt() / 100f, d?.value, d?.unit, active.plan.destination.name, opaque)
            }
            Box(
                GlanceModifier.fillMaxSize().surface(side / 4).padding(6.dp)
                    .clickable(actionStartActivity(if (active != null) WidgetIntents.trip(ctx) else WidgetIntents.search(ctx))),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(bmp), ctx.getString(R.string.app_name), GlanceModifier.size(side - 12.dp))
            }
        }
    }
}

class RingWidgetReceiver : LeanGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

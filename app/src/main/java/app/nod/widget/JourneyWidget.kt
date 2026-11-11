package app.nod.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import app.nod.R
import app.nod.core.Format
import app.nod.data.db.PlaceEntity
import app.nod.data.model.Units
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import app.nod.trip.iconRes
import kotlinx.coroutines.flow.first

/**
 * The main Nod widget. Idle: "Where to?" plus one-tap saved places.
 * During a trip: live distance, journey progress, alarm countdown and an End button.
 */
class JourneyWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(180.dp, 56.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 180.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        // compose() pushes the first frame, so cold flows must start from real values, not placeholders.
        val initialSaved = deps.places().saved.first()
        val initialSettings = deps.settings().settings.first()
        provideContent {
            val trip by deps.sync().shown.collectAsState()
            val saved by deps.places().saved.collectAsState(initial = initialSaved)
            val settings by deps.settings().settings.collectAsState(initial = initialSettings)
            GlanceTheme {
                when (val t = trip) {
                    is TripState.Active -> Active(t, settings.units)
                    TripState.Idle -> Idle(saved)
                }
            }
        }
    }

    @Composable
    private fun Idle(saved: List<PlaceEntity>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val compact = size.height < MEDIUM.height
        Column(
            GlanceModifier.fillMaxSize().surface().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp)
                .clickable(actionStartActivity(WidgetIntents.open(context))),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_stat_nod), null, GlanceModifier.size(30.dp), colorFilter = ColorFilter.tint(WidgetColors.lime))
                Spacer(GlanceModifier.width(10.dp))
                Text(context.getString(R.string.widget_where_to), style = style(19.sp, weight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Box(
                    GlanceModifier.size(40.dp).background(WidgetColors.lime).cornerRadius(20.dp)
                        .clickable(actionStartActivity(WidgetIntents.search(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.ic_search), context.getString(R.string.search_hint), GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(WidgetColors.ink))
                }
            }
            if (!compact) {
                Spacer(GlanceModifier.defaultWeight())
                if (saved.isEmpty()) {
                    Text(context.getString(R.string.widget_no_saved), style = style(13.sp, WidgetColors.muted), maxLines = 2)
                } else {
                    Row(GlanceModifier.fillMaxWidth()) {
                        saved.take(if (size.width >= 300.dp) 3 else 2).forEachIndexed { i, p ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            PlaceChip(p, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PlaceChip(p: PlaceEntity, modifier: GlanceModifier) {
        val context = LocalContext.current
        Row(
            modifier.height(44.dp).background(WidgetColors.card).cornerRadius(16.dp).padding(horizontal = 10.dp)
                .clickable(actionStartActivity(WidgetIntents.start(context, p.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(p.kind.iconRes()), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(WidgetColors.lime))
            Spacer(GlanceModifier.width(8.dp))
            Text(p.name, style = style(13.sp, weight = FontWeight.Bold), maxLines = 1)
        }
    }

    @Composable
    private fun Active(t: TripState.Active, units: Units) {
        val context = LocalContext.current
        val size = LocalSize.current
        val live = t.live
        val d = live?.let { Format.distance(it.distanceM, units) }
        val alarm = when {
            t.phase == TripPhase.ALARM -> context.getString(R.string.now)
            live?.alarmInSec != null -> "~" + Format.duration(live.alarmInSec)
            else -> context.getString(R.string.on_entry)
        }
        val label = context.getString(
            when (t.phase) {
                TripPhase.ALARM -> R.string.status_arrived
                TripPhase.HEADS_UP -> R.string.status_heads_up
                TripPhase.LOCATING -> R.string.status_locating
                TripPhase.TRACKING -> R.string.on_the_way
            },
        ).uppercase()
        val density = context.resources.displayMetrics.density

        if (size.height < MEDIUM.height) {
            Row(
                GlanceModifier.fillMaxSize().surface().padding(horizontal = 16.dp).clickable(actionStartActivity(WidgetIntents.trip(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(label, style = style(10.sp, WidgetColors.lime, FontWeight.Bold))
                    Text(t.plan.destination.name, style = style(14.sp, weight = FontWeight.Bold), maxLines = 1)
                }
                Text(d?.toString() ?: "—", style = style(20.sp, weight = FontWeight.Bold))
            }
            return
        }

        Column(GlanceModifier.fillMaxSize().surface().padding(16.dp).clickable(actionStartActivity(WidgetIntents.trip(context)))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(8.dp).background(WidgetColors.lime).cornerRadius(4.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(label, style = style(11.sp, WidgetColors.lime, FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Text(
                    context.getString(R.string.widget_end),
                    style = style(12.sp, weight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.background(WidgetColors.card).cornerRadius(14.dp).padding(horizontal = 14.dp, vertical = 7.dp)
                        .clickable(actionRunCallback<EndTripAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(t.plan.destination.name, style = style(15.sp, weight = FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (d != null) {
                    val hPx = (40 * density).toInt()
                    val bmp = WidgetArt.hero(context, d.value, d.unit, hPx)
                    Image(ImageProvider(bmp), d.toString(), GlanceModifier.height(40.dp).width((bmp.width / density).dp))
                } else {
                    Text(context.getString(R.string.locating), style = style(20.sp, weight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    Text(context.getString(R.string.stat_alarm), style = style(11.sp, WidgetColors.muted))
                    Text(alarm, style = style(15.sp, weight = FontWeight.Bold))
                }
            }
            if (size.height >= LARGE.height && live != null) {
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat(context.getString(R.string.stat_speed), Format.speed(live.speedMps, units).toString(), GlanceModifier.defaultWeight())
                    Stat(context.getString(R.string.stat_zone), Format.distance(t.plan.destination.radiusM.toDouble(), units).toString(), GlanceModifier.defaultWeight())
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            LinearProgressIndicator(
                progress = if (t.phase == TripPhase.ALARM) 1f else live?.progress ?: 0f,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = WidgetColors.lime,
                backgroundColor = WidgetColors.track,
            )
        }
    }

    @Composable
    private fun Stat(label: String, value: String, modifier: GlanceModifier) {
        Column(modifier) {
            Text(label, style = style(11.sp, WidgetColors.muted))
            Text(value, style = style(14.sp, weight = FontWeight.Bold))
        }
    }
}

class JourneyWidgetReceiver : LeanGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JourneyWidget()
}


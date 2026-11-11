package app.nod.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment as GAlignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column as GColumn
import androidx.glance.layout.Row as GRow
import androidx.glance.layout.Spacer as GSpacer
import androidx.glance.layout.fillMaxSize as gFillMaxSize
import androidx.glance.layout.padding as gPadding
import androidx.glance.layout.size as gSize
import androidx.glance.layout.width as gWidth
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.lifecycle.lifecycleScope
import app.nod.R
import app.nod.core.Format
import app.nod.trip.TripState
import app.nod.trip.iconRes
import app.nod.ui.components.KindBadge
import app.nod.ui.components.NodButton
import app.nod.ui.components.bounceClick
import app.nod.ui.theme.NodTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material3.Text as MText

val QUICK_PLACE_KEY = longPreferencesKey("place_id")

/** One saved place, one tap: "wake me at Uppsala central". */
class QuickStopWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(56.dp, 56.dp), DpSize(150.dp, 56.dp), DpSize(150.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        // compose() pushes the first frame, so cold flows must start from real values, not placeholders.
        val initialSaved = deps.places().saved.first()
        val initialSettings = deps.settings().settings.first()
        provideContent {
            // Per-widget state is loaded by the session (normal path and compose()) before this runs.
            val placeId = currentState<Preferences>()[QUICK_PLACE_KEY] ?: -1L
            val saved by deps.places().saved.collectAsState(initial = initialSaved)
            val trip by deps.sync().shown.collectAsState()
            val settings by deps.settings().settings.collectAsState(initial = initialSettings)
            val ctx = LocalContext.current
            val size = LocalSize.current
            val place = saved.firstOrNull { it.id == placeId }
            val active = trip as? TripState.Active
            val running = active != null && active.plan.savedPlaceId == placeId && placeId > 0

            val widgetId = GlanceAppWidgetManager(ctx).getAppWidgetId(id)
            val action = when {
                // Not set up yet (some launchers skip the configure step when pinning): pick a place now.
                place == null -> actionStartActivity(
                    Intent(ctx, QuickStopConfigActivity::class.java)
                        .setData(android.net.Uri.parse("nod://configure/$widgetId"))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                running -> actionStartActivity(WidgetIntents.trip(ctx))
                else -> actionStartActivity(WidgetIntents.start(ctx, place.id))
            }
            val iconBox = @androidx.compose.runtime.Composable {
                Box(
                    GlanceModifier.gSize(40.dp).background(if (running) WidgetColors.card else WidgetColors.lime).cornerRadius(14.dp),
                    contentAlignment = GAlignment.Center,
                ) {
                    Image(
                        ImageProvider(place?.kind?.iconRes() ?: R.drawable.ic_kind_place), null, GlanceModifier.gSize(22.dp),
                        colorFilter = ColorFilter.tint(if (running) WidgetColors.lime else WidgetColors.ink),
                    )
                }
            }
            if (size.width < 120.dp) {
                Box(GlanceModifier.gFillMaxSize().surface(22.dp).clickable(action), contentAlignment = GAlignment.Center) { iconBox() }
                return@provideContent
            }
            val subtitle = when {
                place == null -> ctx.getString(R.string.widget_pick_place)
                running -> active?.live?.let { Format.distance(it.distanceM, settings.units).toString() } ?: ctx.getString(R.string.locating)
                else -> ctx.getString(R.string.widget_tap_to_start)
            }
            val content = @androidx.compose.runtime.Composable {
                GColumn {
                    Text(place?.name ?: ctx.getString(R.string.widget_quick_name), style = style(14.sp, weight = FontWeight.Bold), maxLines = 2)
                    Text(subtitle, style = style(12.sp, if (running) WidgetColors.lime else WidgetColors.muted), maxLines = 1)
                }
            }
            if (size.height >= 110.dp) {
                GColumn(GlanceModifier.gFillMaxSize().surface(24.dp).gPadding(14.dp).clickable(action)) {
                    iconBox()
                    GSpacer(GlanceModifier.defaultWeight())
                    content()
                }
            } else {
                GRow(GlanceModifier.gFillMaxSize().surface(22.dp).gPadding(horizontal = 10.dp).clickable(action), verticalAlignment = GAlignment.CenterVertically) {
                    iconBox()
                    GSpacer(GlanceModifier.gWidth(10.dp))
                    content()
                }
            }
        }
    }
}

class QuickStopWidgetReceiver : LeanGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickStopWidget()
}

/** Shown when a Quick stop widget is added: choose which saved place it starts. */
class QuickStopConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val deps = widgetDeps()

        setContent {
            val saved by deps.places().saved.collectAsState(initial = null)
            NodTheme(dark = true) {
                val c = NodTheme.colors
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(20.dp)) {
                    MText(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    MText(stringResource(R.string.widget_config_text), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    Spacer(Modifier.height(20.dp))
                    val list = saved
                    if (list != null && list.isEmpty()) {
                        MText(stringResource(R.string.widget_no_saved), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(16.dp))
                        NodButton(stringResource(R.string.widget_open_app), onClick = {
                            startActivity(WidgetIntents.open(this@QuickStopConfigActivity)); finish()
                        })
                    } else if (list != null) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                            items(list, key = { it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth().bounceClick { choose(widgetId, p.id) }
                                        .clip(RoundedCornerShape(20.dp)).background(c.card).padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    KindBadge(p.kind, highlighted = true)
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        MText(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        if (p.subtitle.isNotBlank()) MText(p.subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(widgetId: Int, placeId: Long) = lifecycleScope.launch {
        val glanceId = GlanceAppWidgetManager(this@QuickStopConfigActivity).getGlanceIdBy(widgetId)
        updateAppWidgetState(this@QuickStopConfigActivity, glanceId) { it[QUICK_PLACE_KEY] = placeId }
        WidgetRenderer.pushOrUpdate(this@QuickStopConfigActivity, QuickStopWidget(), intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

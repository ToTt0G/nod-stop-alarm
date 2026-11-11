package app.nod.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.GlanceAppWidget
import app.nod.MainActivity
import app.nod.R
import app.nod.core.Format
import app.nod.data.db.PlaceEntity
import app.nod.data.model.Units
import app.nod.data.places.PlacesRepository
import app.nod.tile.NodTileService
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import app.nod.trip.iconRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps widgets, the Quick Settings tile and launcher shortcuts in step with the app, as cheaply as
 * possible:
 * - widgets render [shown], a throttled snapshot of the trip, never the live per-fix state;
 * - app-driven redraws are composed in-process ([pushDirect]), with no Glance session or
 *   WorkManager job (each of those holds a wake lock for ~45 s);
 * - each surface has its own "what's visible" key, and only surfaces that are actually placed and
 *   whose key changed are redrawn. Keys survive process restarts, so a cold start redraws nothing;
 * - during a trip, at most one redraw per [TRIP_REFRESH_MS], except when the phase changes.
 */
@Singleton
class WidgetSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: TripStateHolder,
    private val places: PlacesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs by lazy { context.getSharedPreferences("widget_sync", Context.MODE_PRIVATE) }
    private val widgets by lazy { AppWidgetManager.getInstance(context) }

    private val _shown = MutableStateFlow(holder.state.value)
    /** The trip as widgets should draw it. Changes only when a redraw is due. */
    val shown: StateFlow<TripState> = _shown

    private enum class Surface { JOURNEY, RING, QUICK, TILE }

    fun start() {
        scope.launch {
            var lastPhase: String? = null
            var lastRefreshAt = 0L
            holder.state
                .map { s -> Surface.entries.joinToString("\n") { key(it, s) } }
                .distinctUntilChanged()
                .conflate()
                .collect {
                    val phase = phaseOf(holder.state.value)
                    if (phase == lastPhase) {
                        val wait = lastRefreshAt + TRIP_REFRESH_MS - System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                    }
                    lastPhase = phase
                    // Draw the latest state, whatever arrived while we waited.
                    val now = holder.state.value
                    _shown.value = now
                    if (redraw(now)) lastRefreshAt = System.currentTimeMillis()
                }
        }
        scope.launch {
            places.saved.distinctUntilChanged().collect { list ->
                val key = placesKey(list)
                if (key == prefs.getString(KEY_PLACES, null)) return@collect
                runCatching { pushDirect(JourneyWidget(), JourneyWidgetReceiver::class.java) }
                runCatching { pushDirect(QuickStopWidget(), QuickStopWidgetReceiver::class.java) }
                runCatching { publishShortcuts(list) }
                prefs.edit().putString(KEY_PLACES, key).apply()
            }
        }
    }

    /** Redraws each surface whose visible content changed. Returns whether anything was drawn. */
    private suspend fun redraw(s: TripState): Boolean {
        var drew = false
        val edit = prefs.edit()
        for (surface in Surface.entries) {
            val k = key(surface, s)
            if (k == prefs.getString("key_$surface", null)) continue
            drew = true
            runCatching {
                when (surface) {
                    Surface.JOURNEY -> pushDirect(JourneyWidget(), JourneyWidgetReceiver::class.java)
                    Surface.RING -> pushDirect(RingWidget(), RingWidgetReceiver::class.java)
                    Surface.QUICK -> pushDirect(QuickStopWidget(), QuickStopWidgetReceiver::class.java)
                    // An active tile only re-renders when asked; harmless if it isn't added.
                    Surface.TILE -> TileService.requestListeningState(context, ComponentName(context, NodTileService::class.java))
                }
            }.onSuccess {
                // Only remember what is actually on screen, so a failed redraw is retried.
                edit.putString("key_$surface", k)
            }
        }
        edit.apply()
        return drew
    }

    /** Renders every placed instance of [widget] in-process: no Glance session, no WorkManager job. */
    private suspend fun pushDirect(widget: GlanceAppWidget, receiver: Class<*>) {
        val ids = runCatching { widgets.getAppWidgetIds(ComponentName(context, receiver)) }.getOrNull() ?: return
        if (ids.isNotEmpty()) WidgetRenderer.pushOrUpdate(context, widget, ids)
    }

    /** A cheap system lookup: no widget of this kind on any home or lock screen means no work. */
    private fun placed(receiver: Class<*>): Boolean =
        runCatching { widgets.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty() }.getOrDefault(true)

    private fun phaseOf(s: TripState) = when (s) {
        TripState.Idle -> "idle"
        is TripState.Active -> s.phase.name
    }

    /** Exactly what each surface shows, at the precision it shows it. */
    private fun key(surface: Surface, s: TripState): String {
        if (s !is TripState.Active) return "idle"
        val live = s.live
        val dist = live?.let { Format.distance(it.distanceM, Units.METRIC).toString() }.orEmpty()
        val base = "${s.phase}|${s.plan.destination.name}|${s.silenced}"
        return when (surface) {
            Surface.JOURNEY -> "$base|$dist|${(live?.progress?.times(100))?.toInt()}|${live?.alarmInSec?.let { Format.duration(it) }}"
            Surface.RING -> "$base|$dist|${(live?.progress?.times(100))?.toInt()}"
            Surface.QUICK -> "${s.phase}|${s.plan.savedPlaceId}|$dist"
            Surface.TILE -> "$base|$dist"
        }
    }

    /** Names, kinds and order of saved places, plus the language the shortcut labels were made in. */
    private fun placesKey(list: List<PlaceEntity>): String = buildString {
        append(context.resources.configuration.locales[0].toLanguageTag())
        list.forEach { append('|').append(it.id).append(',').append(it.name).append(',').append(it.kind) }
    }

    private companion object {
        const val TRIP_REFRESH_MS = 10_000L
        const val KEY_PLACES = "places"
    }

    /** Long-press the Nod icon: your top saved places, ready to start. */
    private fun publishShortcuts(list: List<PlaceEntity>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
        val search = ShortcutInfoCompat.Builder(context, "search")
            .setShortLabel(context.getString(R.string.shortcut_search))
            .setIcon(adaptiveIcon(R.drawable.ic_search))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_OPEN_SEARCH, true))
            .setRank(0)
            .build()
        val placeShortcuts = list.take(max - 1).mapIndexed { i, p ->
            ShortcutInfoCompat.Builder(context, "place_${p.id}")
                .setShortLabel(p.name)
                .setLongLabel(context.getString(R.string.shortcut_wake_at, p.name))
                .setIcon(adaptiveIcon(p.kind.iconRes()))
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(MainActivity.EXTRA_START_PLACE, p.id))
                .setRank(i + 1)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(search) + placeShortcuts)
    }

    /** Lime glyph on an ink adaptive-icon canvas, matching the launcher icon. */
    private fun adaptiveIcon(res: Int): IconCompat {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { color = WidgetArt.INK })
        val d = ContextCompat.getDrawable(context, res)!!.mutate()
        d.setTint(WidgetArt.LIME)
        val glyph = (40 * density).toInt()
        val o = (size - glyph) / 2
        d.setBounds(o, o, o + glyph, o + glyph)
        d.draw(c)
        return IconCompat.createWithAdaptiveBitmap(bmp)
    }
}

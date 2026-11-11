package app.nod.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.compose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Renders widgets in-process and hands the RemoteViews straight to the launcher.
 *
 * Glance's default path runs every render in a WorkManager job that stays alive ~45 s holding a
 * wake lock. Composing directly costs a few milliseconds and no job at all. Clicks keep working:
 * the widgets only use actionStartActivity / actionRunCallback, which don't need a live session.
 */
object WidgetRenderer {
    private val lock = Mutex()

    /** Returns the ids that couldn't be rendered this way (the caller falls back to Glance). */
    suspend fun push(context: Context, widget: GlanceAppWidget, ids: IntArray): List<Int> = lock.withLock {
        val awm = AppWidgetManager.getInstance(context)
        val glance = GlanceAppWidgetManager(context)
        ids.filter { id ->
            runCatching {
                val views = widget.compose(context, glance.getGlanceIdBy(id), options = awm.getAppWidgetOptions(id))
                awm.updateAppWidget(id, views)
            }.onFailure { if (it is CancellationException) throw it }.isFailure
        }
    }

    /** [push], falling back to Glance's own update for anything that failed. */
    suspend fun pushOrUpdate(context: Context, widget: GlanceAppWidget, ids: IntArray) {
        val glance = GlanceAppWidgetManager(context)
        for (id in push(context, widget, ids)) runCatching { widget.update(context, glance.getGlanceIdBy(id)) }
    }
}

/**
 * Widget receiver whose system-driven renders (placement, boot, app update, force-stop recovery,
 * resize) also skip Glance's long-lived session. Deletion and click actions use Glance as usual.
 */
abstract class LeanGlanceReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, intArrayOf(appWidgetId))
    }

    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                WidgetRenderer.pushOrUpdate(app, glanceAppWidget, ids)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

package app.nod.trip

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.nod.R
import app.nod.data.model.TripPlan
import app.nod.data.places.PlacesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Handles notification buttons that don't need a running trip, like saving a place afterwards. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var places: PlacesRepository
    @Inject lateinit var json: Json

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SAVE) return
        val plan = intent.getStringExtra(EXTRA_PLAN)?.let { runCatching { json.decodeFromString(TripPlan.serializer(), it) }.getOrNull() } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (places.findSaved(plan.destination.lat, plan.destination.lon) == null) places.save(plan)
                context.getSystemService(NotificationManager::class.java).cancel(TripNotifications.ID_SUMMARY)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.saved_toast, plan.destination.name), Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SAVE = "app.nod.SAVE_PLACE"
        const val EXTRA_PLAN = "plan"
    }
}

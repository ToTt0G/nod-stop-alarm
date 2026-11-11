package app.nod.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.nod.data.model.TripPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing API for starting and stopping journeys. All real work happens in [TripService]. */
@Singleton
class TripController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val holder: TripStateHolder,
) {
    val state get() = holder.state

    fun start(plan: TripPlan) = launch(plan.copy(startedAt = System.currentTimeMillis()))

    /** Re-attach to a journey that was running when the process died. */
    fun resume(plan: TripPlan) {
        if (holder.state.value is TripState.Idle) launch(plan)
    }

    private fun launch(stamped: TripPlan) {
        holder.set(TripState.Active(stamped))
        val intent = Intent(context, TripService::class.java)
            .setAction(TripService.ACTION_START)
            .putExtra(TripService.EXTRA_PLAN, json.encodeToString(TripPlan.serializer(), stamped))
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() = send(TripService.ACTION_STOP)
    fun dismissAlarm() = send(TripService.ACTION_DISMISS)
    fun silence() = send(TripService.ACTION_SILENCE)

    private fun send(action: String) {
        if (holder.state.value is TripState.Idle) return
        runCatching { context.startService(Intent(context, TripService::class.java).setAction(action)) }
    }
}

package app.nod.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.nod.MainActivity
import app.nod.R
import app.nod.core.Format
import app.nod.data.model.Units
import app.nod.trip.TripNotifications
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick Settings tile: shows live distance while a trip runs, opens Nod on tap. */
@AndroidEntryPoint
class NodTileService : TileService() {
    @Inject lateinit var holder: TripStateHolder
    private var job: Job? = null

    // Active tile: not bound until asked, so ask once on add to show the idle state right away.
    override fun onTileAdded() {
        super.onTileAdded()
        runCatching { TileService.requestListeningState(this, ComponentName(this, NodTileService::class.java)) }
    }

    override fun onStartListening() {
        super.onStartListening()
        job = CoroutineScope(Dispatchers.Main).launch {
            holder.state.collect { render(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        super.onStopListening()
    }

    private fun render(state: TripState) {
        val tile = qsTile ?: return
        when (state) {
            TripState.Idle -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.app_name)
                if (Build.VERSION.SDK_INT >= 29) tile.subtitle = getString(R.string.tile_idle)
            }
            is TripState.Active -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = state.plan.destination.name
                if (Build.VERSION.SDK_INT >= 29) {
                    tile.subtitle = state.live?.let { Format.distance(it.distanceM, Units.METRIC).toString() } ?: getString(R.string.locating)
                }
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(TripNotifications.EXTRA_OPEN_TRIP, holder.state.value is TripState.Active)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

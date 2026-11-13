package app.nod.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import app.nod.core.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: LocationSource,
) {
    @Volatile var lastKnown: GeoPoint? = null
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best quick guess at where the user is: cached fix first, then a fresh one with a timeout. */
    suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        source.lastLocation()?.let {
            return GeoPoint(it.latitude, it.longitude).also { p -> lastKnown = p }
        }
        val loc = source.currentLocation(precise = false, timeoutMs = 8_000)
        return loc?.let { GeoPoint(it.latitude, it.longitude).also { p -> lastKnown = p } }
    }

    /**
     * Live position while collected, for showing where you are on a map. Satellite precision, since
     * the screen is on and someone is looking; updates stop the moment collection stops. Empty
     * without a location permission.
     */
    fun live(intervalMs: Long = 4_000): Flow<GeoPoint> {
        if (!hasPermission()) return emptyFlow()
        return callbackFlow {
            val listener = object : LocationSource.Listener {
                override fun onLocation(location: Location) {
                    trySend(GeoPoint(location.latitude, location.longitude).also { lastKnown = it })
                }
                override fun onAvailabilityChanged(available: Boolean) = Unit
            }
            source.lastLocation()?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
            try {
                source.requestUpdates(LocationSource.UpdateRequest(intervalMs, precise = true, minUpdateIntervalMs = intervalMs / 2), listener, Looper.getMainLooper())
            } catch (_: SecurityException) {
                close()
            }
            awaitClose { source.removeUpdates(listener) }
        }
    }
}

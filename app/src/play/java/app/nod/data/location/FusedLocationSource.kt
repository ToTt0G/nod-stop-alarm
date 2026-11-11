package app.nod.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Google Play Services' fused location provider (the `play` flavor). */
@Singleton
class FusedLocationSource @Inject constructor(
    @ApplicationContext context: Context,
) : LocationSource {
    private val fused: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val callbacks = ConcurrentHashMap<LocationSource.Listener, LocationCallback>()

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): Location? =
        try {
            fused.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(precise: Boolean, timeoutMs: Long): Location? {
        val cts = CancellationTokenSource()
        val loc = withTimeoutOrNull(timeoutMs) {
            runCatching { fused.getCurrentLocation(priority(precise), cts.token).await() }.getOrNull()
        }
        if (loc == null) cts.cancel()
        return loc
    }

    @SuppressLint("MissingPermission")
    override fun requestUpdates(request: LocationSource.UpdateRequest, listener: LocationSource.Listener, looper: Looper) {
        val callback = callbacks.getOrPut(listener) { Callback(listener) }
        val fusedRequest = LocationRequest.Builder(priority(request.precise), request.intervalMs)
            .setMinUpdateIntervalMillis(request.minUpdateIntervalMs)
            .setWaitForAccurateLocation(false)
            .build()
        // Re-requesting with the same callback replaces its previous request.
        fused.requestLocationUpdates(fusedRequest, callback, looper)
    }

    override fun removeUpdates(listener: LocationSource.Listener) {
        val callback = callbacks.remove(listener) ?: return
        runCatching { fused.removeLocationUpdates(callback) }
    }

    private fun priority(precise: Boolean) =
        if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

    private class Callback(private val listener: LocationSource.Listener) : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(listener::onLocation)
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            listener.onAvailabilityChanged(availability.isLocationAvailable)
        }
    }
}

package app.nod.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The platform [LocationManager] (the `foss` flavor): no Google Play Services.
 *
 * Provider choice: the platform fused provider on Android 12+ (it honours the requested quality),
 * otherwise GPS for precise requests and the network provider (when enabled) for balanced ones,
 * falling back to GPS. Availability is reported from the chosen provider being enabled/disabled;
 * a network provider that is enabled but silently produces nothing is caught by TripService's
 * watchdog, exactly as with the fused provider.
 */
@Singleton
class PlatformLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {
    private val lm: LocationManager? = context.getSystemService(LocationManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private val registrations = ConcurrentHashMap<LocationSource.Listener, Registration>()

    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): Location? {
        val lm = lm ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        // Freshest of whatever each provider has cached; a provider we may not read is skipped.
        return providers
            .filter { has(lm, it) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(precise: Boolean, timeoutMs: Long): Location? {
        val lm = lm ?: return null
        val provider = providerFor(lm, precise) ?: return null
        val signal = CancellationSignal()
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { signal.cancel() }
                    val done = { loc: Location? -> if (cont.isActive) cont.resume(loc) }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Api31.currentLocation(lm, provider, precise, timeoutMs, signal, executor, done)
                    } else {
                        // API 30: LocationManager.getCurrentLocation. Below: a one-shot
                        // requestLocationUpdates that removes itself after the first fix.
                        LocationManagerCompat.getCurrentLocation(lm, provider, signal, executor) { done(it) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            signal.cancel()
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestUpdates(request: LocationSource.UpdateRequest, listener: LocationSource.Listener, looper: Looper) {
        val lm = lm ?: return
        removeUpdates(listener)
        val provider = providerFor(lm, request.precise) ?: return
        val registration = Registration(provider, listener)
        val compatRequest = LocationRequestCompat.Builder(request.intervalMs)
            .setQuality(
                if (request.precise) LocationRequestCompat.QUALITY_HIGH_ACCURACY
                else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY,
            )
            .setMinUpdateIntervalMillis(request.minUpdateIntervalMs)
            .build()
        LocationManagerCompat.requestLocationUpdates(lm, provider, compatRequest, registration, looper)
        registrations[listener] = registration
        // The fused client reports unavailability soon after a request; mirror that for a provider
        // that is already off. Posted, never re-entrant, and dropped if the request was replaced.
        if (!enabled(lm, provider)) {
            Handler(looper).post { if (registration.active) listener.onAvailabilityChanged(false) }
        }
    }

    override fun removeUpdates(listener: LocationSource.Listener) {
        val registration = registrations.remove(listener) ?: return
        registration.active = false
        val lm = lm ?: return
        runCatching { LocationManagerCompat.removeUpdates(lm, registration) }
    }

    private fun providerFor(lm: LocationManager, precise: Boolean): String? {
        if (Build.VERSION.SDK_INT >= 31 && has(lm, LocationManager.FUSED_PROVIDER)) return LocationManager.FUSED_PROVIDER
        // GPS needs the precise permission; with approximate only, the network provider is all there is.
        val gps = LocationManager.GPS_PROVIDER.takeIf { hasFinePermission() && has(lm, it) }
        val network = LocationManager.NETWORK_PROVIDER.takeIf { has(lm, it) }
        return when {
            !precise && network != null && enabled(lm, network) -> network
            gps != null -> gps
            else -> network
        }
    }

    private fun has(lm: LocationManager, provider: String): Boolean =
        runCatching { LocationManagerCompat.hasProvider(lm, provider) }.getOrDefault(false)

    private fun enabled(lm: LocationManager, provider: String): Boolean =
        runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** One registered listener on one provider. Inactive once removed or replaced. */
    private class Registration(
        private val provider: String,
        private val listener: LocationSource.Listener,
    ) : LocationListenerCompat {
        @Volatile var active = true

        override fun onLocationChanged(location: Location) {
            if (active) listener.onLocation(location)
        }

        // A batch: like the fused client's LocationResult.lastLocation, only the newest matters.
        override fun onLocationChanged(locations: MutableList<Location>) {
            locations.lastOrNull()?.let(::onLocationChanged)
        }

        override fun onProviderEnabled(provider: String) {
            if (active && provider == this.provider) listener.onAvailabilityChanged(true)
        }

        override fun onProviderDisabled(provider: String) {
            if (active && provider == this.provider) listener.onAvailabilityChanged(false)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit
    }

    @RequiresApi(31)
    private object Api31 {
        @SuppressLint("MissingPermission")
        fun currentLocation(
            lm: LocationManager,
            provider: String,
            precise: Boolean,
            timeoutMs: Long,
            signal: CancellationSignal,
            executor: java.util.concurrent.Executor,
            done: (Location?) -> Unit,
        ) {
            val request = LocationRequest.Builder(0L)
                .setQuality(if (precise) LocationRequest.QUALITY_HIGH_ACCURACY else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(timeoutMs.coerceAtLeast(1L))
                .build()
            lm.getCurrentLocation(provider, request, signal, executor) { done(it) }
        }
    }
}

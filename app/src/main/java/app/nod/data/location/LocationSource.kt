package app.nod.data.location

import android.location.Location
import android.os.Looper

/**
 * Where fixes come from. The `play` flavor uses Google Play Services' fused provider; the `foss`
 * flavor uses the platform [android.location.LocationManager], so it ships no proprietary code.
 *
 * Callers are responsible for holding a location permission. The suspend functions never throw
 * (other than cancellation) and return null when no fix could be obtained; [requestUpdates] may throw
 * [SecurityException].
 */
interface LocationSource {
    /** The most recent cached fix, if the system has one. Never waits for a new fix. */
    suspend fun lastLocation(): Location?

    /**
     * One fresh fix: satellite precision when [precise], otherwise whatever is cheapest (Wi-Fi/cell).
     * Gives up and returns null after [timeoutMs].
     */
    suspend fun currentLocation(precise: Boolean, timeoutMs: Long): Location?

    /**
     * Continuous updates every [UpdateRequest.intervalMs], delivered on [looper]. Registering a
     * [listener] that is already registered replaces its previous request.
     */
    fun requestUpdates(request: UpdateRequest, listener: Listener, looper: Looper)

    /** Stops updates for [listener]. Safe to call when it isn't registered. */
    fun removeUpdates(listener: Listener)

    data class UpdateRequest(
        val intervalMs: Long,
        /** Satellite precision; otherwise balanced power (Wi-Fi/cell). */
        val precise: Boolean,
        /** Fixes other apps already paid for are welcome, but not more often than this. */
        val minUpdateIntervalMs: Long,
    )

    interface Listener {
        fun onLocation(location: Location)

        /**
         * Whether the source currently expects to be able to produce fixes for this request. False
         * means it can't (provider off, no Wi-Fi/cell to locate from); true means it recovered.
         */
        fun onAvailabilityChanged(available: Boolean)
    }
}

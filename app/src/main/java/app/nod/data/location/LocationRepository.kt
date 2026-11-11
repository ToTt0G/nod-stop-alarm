package app.nod.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.nod.core.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
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
}

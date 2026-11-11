package app.nod.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.nod.trip.TripNotifications
import app.nod.ui.trip.isIgnoringBattery

data class PermissionStatus(
    val location: Boolean,
    val notifications: Boolean,
    val fullScreen: Boolean,
    val battery: Boolean,
    /** Android 16 Live Updates (promoted ongoing notifications); users can switch them off per app. */
    val liveUpdates: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionStatus(
            location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            notifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
            fullScreen = TripNotifications.canUseFullScreen(context),
            battery = isIgnoringBattery(context),
            liveUpdates = TripNotifications.canPromote(context),
        )

        private fun granted(context: Context, p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }
}

package app.nod

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import app.nod.widget.WidgetSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Kept deliberately tiny: the process is also started for widget broadcasts and the Quick Settings
 * tile, so anything heavy (map engine, notification channels, WorkManager) is created on first real
 * use instead.
 */
@HiltAndroidApp
class NodApp : Application(), Configuration.Provider {
    @Inject lateinit var widgetSync: WidgetSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).build()

    override fun onCreate() {
        super.onCreate()
        widgetSync.start()
    }
}

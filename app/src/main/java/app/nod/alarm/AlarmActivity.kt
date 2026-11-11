package app.nod.alarm

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.nod.core.Format
import app.nod.data.model.AlertStyle
import app.nod.data.settings.SettingsRepository
import app.nod.trip.AlarmPlayer
import app.nod.trip.TripController
import app.nod.trip.TripPhase
import app.nod.trip.TripState
import app.nod.trip.TripStateHolder
import app.nod.trip.spoken
import app.nod.ui.theme.NodTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full-screen wake-up shown over the lock screen when you reach your zone. */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var holder: TripStateHolder
    @Inject lateinit var controller: TripController
    @Inject lateinit var player: AlarmPlayer
    @Inject lateinit var settings: SettingsRepository

    private val preview by lazy { intent.getBooleanExtra(EXTRA_PREVIEW, false) }
    private var silenced by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Swallow back: dismissing must be deliberate.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = Unit })

        if (preview) {
            lifecycleScope.launch {
                val s = settings.current()
                val style = if (s.alertStyle == AlertStyle.SILENT) AlertStyle.VIBRATE else s.alertStyle
                player.start(
                    style, s.vibration, s.soundUri, s.gradualVolume, maxMs = 30_000,
                    headphonesOnly = s.headphonesOnly,
                    wakeLine = if (s.speak) spoken(app.nod.R.string.audio_speak_alarm, getString(app.nod.R.string.preview_place)) else null,
                    onAwake = ::dismiss,
                )
            }
        } else {
            lifecycleScope.launch {
                holder.state.collect { st ->
                    val ringing = st is TripState.Active && st.phase == TripPhase.ALARM
                    if (!ringing) finish()
                }
            }
        }

        setContent {
            val state by holder.state.collectAsStateWithLifecycle()
            val s by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val headset by player.headsetConnected.collectAsStateWithLifecycle()
            val active = state as? TripState.Active
            val units = s?.units ?: app.nod.data.model.Units.METRIC
            NodTheme(dark = true) {
                AlarmScreen(
                    place = active?.plan?.destination?.name ?: getString(app.nod.R.string.preview_place),
                    distance = active?.live?.let { Format.distance(it.distanceM, units).toString() },
                    silenced = silenced || active?.silenced == true,
                    preview = preview,
                    earbudsHint = headset,
                    onDismiss = ::dismiss,
                )
            }
        }
    }

    private fun dismiss() {
        if (preview) player.stop() else controller.dismissAlarm()
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            silenced = true
            if (preview) player.silence() else controller.silence()
            return true
        }
        // A wired headset's button (or any media key that reaches the screen): "I'm awake".
        if (keyCode in MEDIA_KEYS) {
            dismiss()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (preview && isFinishing) player.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        private val MEDIA_KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
        )
        fun preview(context: Context) = context.startActivity(
            Intent(context, AlarmActivity::class.java).putExtra(EXTRA_PREVIEW, true),
        )
    }
}

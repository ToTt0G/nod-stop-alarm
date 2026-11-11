package app.nod.trip

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Finds connected personal listening devices: only these count as "in your ears". */
internal object Headsets {
    /** In order of preference: a plugged-in headset is the one actually in use. */
    private val types: List<Int> = buildList {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= 28) add(AudioDeviceInfo.TYPE_HEARING_AID)
    }

    fun find(audio: AudioManager): AudioDeviceInfo? {
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull() ?: return null
        return types.firstNotNullOfOrNull { t -> outputs.firstOrNull { it.type == t } }
    }
}

/**
 * A platform media session that only exists while the alarm rings, so the play/pause button on
 * earbuds (or a wired headset's hook button) means "I'm awake". Released as soon as the alarm ends.
 */
internal class AwakeButton(context: Context, private val onAwake: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var fired = false
    private var session: MediaSession? = MediaSession(context, "NodAlarm").apply {
        setCallback(
            object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION") mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    // Any earbud button counts; act once, on the press rather than the release.
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) awake()
                    return true
                }

                override fun onPlay() = awake()
                override fun onPause() = awake()
                override fun onStop() = awake()
            },
            main,
        )
        setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP,
                )
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build(),
        )
        isActive = true
    }

    private fun awake() {
        if (fired) return
        fired = true
        // Post, so the session is never released from inside its own callback.
        main.post { onAwake() }
    }

    fun release() {
        fired = true
        main.removeCallbacksAndMessages(null)
        session?.let {
            runCatching { it.isActive = false }
            it.release()
        }
        session = null
    }
}

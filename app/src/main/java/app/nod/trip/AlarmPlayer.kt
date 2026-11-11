package app.nod.trip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.nod.data.model.AlertStyle
import app.nod.data.model.VibrationPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Plays the arrival alarm: looping alarm-stream audio with a gentle volume ramp, plus vibration.
 *
 * With headphones connected (and the setting on) the tone plays on the media path to the headset
 * only, so nobody else on the bus hears it. Everything headphone-related (media session, device
 * callback, speech, raised volume) exists only between [start] and [stop].
 */
@Singleton
class AlarmPlayer @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val speech = Speech(context)

    private var player: MediaPlayer? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Set while the tone is playing in headphones; null on the normal alarm path. */
    private var headset: AudioDeviceInfo? = null
    private var savedMusicVolume: Int? = null
    private var speaking = false
    private var awakeButton: AwakeButton? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private var ringSoundUri: String? = null
    private var ringGradual = true

    private val _headsetConnected = MutableStateFlow(false)
    /** Whether a headset is connected while the alarm rings (drives the earbud hint). */
    val headsetConnected: StateFlow<Boolean> = _headsetConnected.asStateFlow()

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val mediaAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * @param headphonesOnly play in a connected headset instead of out loud (falls back to the
     *   alarm stream when none is connected, or when it disconnects mid-alarm).
     * @param wakeLine said once at the start, in headphones mode only.
     * @param onAwake called when an earbud/headset button is pressed: "I'm awake".
     */
    fun start(
        style: AlertStyle,
        pattern: VibrationPattern,
        soundUri: String?,
        gradual: Boolean,
        maxMs: Long = 10 * 60_000L,
        headphonesOnly: Boolean = false,
        wakeLine: Spoken? = null,
        onAwake: (() -> Unit)? = null,
    ) {
        stop()
        acquireWakeLock(maxMs)
        ringSoundUri = soundUri
        ringGradual = gradual
        if (onAwake != null) awakeButton = runCatching { AwakeButton(context, onAwake) }.getOrNull()
        watchHeadsets()
        if (style == AlertStyle.ALARM) {
            val hs = if (headphonesOnly) Headsets.find(audio) else null
            if (hs == null || !playInHeadphones(hs, soundUri, gradual, wakeLine)) playSound(soundUri, gradual)
        }
        if (style != AlertStyle.SILENT) vibrate(pattern, repeat = true)
        timeoutJob = scope.launch { delay(maxMs); stop() }
    }

    /** Stop the noise but keep a soft reminder buzz every so often. */
    fun silence() {
        releasePlayer()
        vibrator.cancel()
    }

    fun stop() {
        timeoutJob?.cancel()
        releasePlayer()
        vibrator.cancel()
        awakeButton?.release()
        awakeButton = null
        deviceCallback?.let { runCatching { audio.unregisterAudioDeviceCallback(it) } }
        deviceCallback = null
        _headsetConnected.value = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * A short double tick: "get ready, you're close". With headphones in, [spoken] is also said
     * over the music, which ducks briefly and comes back.
     */
    fun headsUp(spoken: Spoken? = null) {
        vibrate(longArrayOf(0, 60, 120, 60), intArrayOf(0, 200, 0, 200), repeat = false)
        if (spoken != null && player == null && Headsets.find(audio) != null) {
            speech.speak(spoken.text, spoken.lang, duck = true)
        }
    }

    fun previewVibration(pattern: VibrationPattern) = vibrate(pattern, repeat = false)

    fun previewSound(soundUri: String?) {
        stop()
        playSound(soundUri, gradual = false, volume = 0.6f)
        timeoutJob = scope.launch { delay(3_500); stop() }
    }

    private fun vibrate(pattern: VibrationPattern, repeat: Boolean) =
        vibrate(pattern.timings, pattern.amplitudes, repeat)

    private fun vibrate(timings: LongArray, amplitudes: IntArray, repeat: Boolean) {
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, if (repeat) 0 else -1)
        } else {
            VibrationEffect.createWaveform(timings, if (repeat) 0 else -1)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(effect, alarmAttrs)
        }
    }

    private fun prepare(attrs: AudioAttributes, soundUri: String?): MediaPlayer? {
        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
        }.isSuccess || runCatching {
            // Fallback when a custom sound became unavailable.
            mp.reset()
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            mp.isLooping = true
            mp.prepare()
        }.isSuccess
        if (!ok) { mp.release(); return null }
        return mp
    }

    private fun playSound(soundUri: String?, gradual: Boolean, volume: Float = 1f) {
        val mp = prepare(alarmAttrs, soundUri) ?: return
        requestFocus(alarmAttrs)
        val start = if (gradual) 0.08f else volume
        mp.setVolume(start, start)
        mp.start()
        player = mp
        if (gradual) rampJob = scope.launch { ramp(start, volume, steps = 30, stepMs = 600) }
    }

    /** The discreet path: media usage routed to the headset, music paused, volume safe but audible. */
    private fun playInHeadphones(hs: AudioDeviceInfo, soundUri: String?, gradual: Boolean, wakeLine: Spoken?): Boolean {
        val mp = prepare(mediaAttrs, soundUri) ?: return false
        if (Build.VERSION.SDK_INT >= 28) runCatching { mp.setPreferredDevice(hs) }
        requestFocus(mediaAttrs)
        raiseMusicVolume()
        headset = hs
        mp.setVolume(HP_START, HP_START)
        runCatching { mp.start() }.onFailure {
            mp.release()
            headset = null
            restoreMusicVolume()
            focus?.let { audio.abandonAudioFocusRequest(it) }
            focus = null
            return false
        }
        player = mp
        if (wakeLine != null) {
            speaking = true
            // The alarm already holds focus; the voice simply plays over the quiet start of the tone.
            speech.speak(wakeLine.text, wakeLine.lang, duck = false) { speaking = false }
        }
        rampJob = scope.launch {
            withTimeoutOrNull(SPEECH_HOLD_MS) { while (speaking) delay(100) }
            if (gradual) ramp(HP_START, 1f, steps = 16, stepMs = 500) else runCatching { player?.setVolume(1f, 1f) }
        }
        return true
    }

    private suspend fun CoroutineScope.ramp(from: Float, to: Float, steps: Int, stepMs: Long) {
        for (i in 1..steps) {
            delay(stepMs)
            if (!isActive) break
            val v = from + (to - from) * (i / steps.toFloat())
            runCatching { player?.setVolume(v, v) }
        }
    }

    /** Headset presence for the earbud hint, and the safety net if it disconnects mid-alarm. */
    private fun watchHeadsets() {
        _headsetConnected.value = Headsets.find(audio) != null
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                _headsetConnected.value = Headsets.find(audio) != null
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val next = Headsets.find(audio)
                _headsetConnected.value = next != null
                val current = headset ?: return
                if (removedDevices.orEmpty().none { it.id == current.id }) return
                if (next != null) {
                    headset = next
                    if (Build.VERSION.SDK_INT >= 28) runCatching { player?.setPreferredDevice(next) }
                } else {
                    fallBackToSpeaker()
                }
            }
        }
        runCatching { audio.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper())) }
            .onSuccess { deviceCallback = cb }
    }

    /** Headphones gone: never go silent, ring out loud on the alarm stream instead. */
    private fun fallBackToSpeaker() {
        // Keep the music paused across the switch: take the new focus before dropping the old one.
        val old = focus
        focus = null
        releasePlayer()
        playSound(ringSoundUri, ringGradual)
        old?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
    }

    private fun raiseMusicVolume() {
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val floor = ceil(max * HP_MIN_VOLUME).toInt().coerceIn(1, max)
            if (now < floor) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, floor, 0)
                savedMusicVolume = now
            }
        }
    }

    private fun restoreMusicVolume() {
        savedMusicVolume?.let { runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) } }
        savedMusicVolume = null
    }

    private fun requestFocus(attrs: AudioAttributes) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
        audio.requestAudioFocus(req)
        focus = req
    }

    private fun releasePlayer() {
        rampJob?.cancel()
        speech.cancel()
        speaking = false
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        headset = null
        // Restore the user's volume before giving focus back, so their music resumes as it was.
        restoreMusicVolume()
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun acquireWakeLock(ms: Long) {
        val pm = context.getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nod:alarm").apply {
            setReferenceCounted(false)
            acquire(ms)
        }
    }

    private companion object {
        /** Headphone tone starts here and ramps to full over ~8 s. */
        const val HP_START = 0.15f
        /** Media volume is lifted to at least this share of max while ringing in headphones. */
        const val HP_MIN_VOLUME = 0.35
        /** How long the tone stays quiet for the spoken wake-up line, at most. */
        const val SPEECH_HOLD_MS = 5_000L
    }
}

package app.nod.trip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.StringRes
import app.nod.R
import java.util.Locale

/** A line to say, with the language it is written in so the voice matches the words. */
data class Spoken(val text: String, val lang: String)

/** Resolve a spoken line from this context's (localized) resources. */
fun Context.spoken(@StringRes res: Int, vararg args: Any): Spoken =
    Spoken(getString(res, *args), getString(R.string.audio_speech_lang))

/**
 * One spoken line at a time. The engine is created only when there is something to say and is shut
 * down (with its audio focus abandoned) the moment the line ends, fails or times out. Main thread only.
 */
internal class Speech(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null
    private var onEnd: (() -> Unit)? = null
    private var token = 0
    private val giveUp = Runnable { cancel() }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * @param langTag the language [text] is written in, so the voice matches the words.
     * @param duck take transient may-duck focus so music dips under the voice and comes back. Off
     *   when the caller already holds focus itself (the alarm), so the two requests don't fight.
     * @param onEnd called once when speaking is over, whether it worked or not.
     */
    fun speak(text: String, langTag: String, duck: Boolean, onEnd: () -> Unit = {}) {
        cancel()
        val t = ++token
        this.onEnd = onEnd
        main.postDelayed(giveUp, GIVE_UP_MS)
        tts = runCatching {
            TextToSpeech(app) { status -> main.post { if (t == token) onInit(t, status, text, langTag, duck) } }
        }.getOrNull()
        if (tts == null) cancel()
    }

    private fun onInit(t: Int, status: Int, text: String, langTag: String, duck: Boolean) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return cancel()
        runCatching { engine.setAudioAttributes(attrs) }
        pickLanguage(engine, langTag)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = end()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = end()
            override fun onError(utteranceId: String?, errorCode: Int) = end()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = end()
            private fun end() { main.post { if (t == token) cancel() } }
        })
        if (duck) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            // Refused during a call, for instance: stay quiet rather than talk over it.
            if (audio.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return cancel()
            focus = req
        }
        val ok = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE) }
            .getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS
        if (!ok) cancel()
    }

    /** The app's language first (keeping its region), then the bare language, else the engine default. */
    private fun pickLanguage(engine: TextToSpeech, langTag: String) {
        val bare = Locale.forLanguageTag(langTag)
        val current = Locale.getDefault()
        val candidates = listOfNotNull(current.takeIf { it.language == bare.language }, bare)
        for (loc in candidates) {
            val r = runCatching { engine.isLanguageAvailable(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (r >= TextToSpeech.LANG_AVAILABLE && runCatching { engine.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE) return
        }
    }

    /** Stop talking and release everything. Safe to call at any time. */
    fun cancel() {
        token++
        main.removeCallbacks(giveUp)
        tts?.let {
            runCatching { it.setOnUtteranceProgressListener(null) }
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
        val done = onEnd
        onEnd = null
        done?.invoke()
    }

    private companion object {
        const val UTTERANCE = "nod"
        /** Covers a slow engine bind plus a short sentence; nothing should outlive this. */
        const val GIVE_UP_MS = 20_000L
    }
}

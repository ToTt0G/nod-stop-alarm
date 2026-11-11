package app.nod.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nod.data.model.AlertStyle
import app.nod.data.model.ThemeMode
import app.nod.data.model.TriggerMode
import app.nod.data.model.TripPlan
import app.nod.data.model.Units
import app.nod.data.model.VibrationPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val units: Units = Units.METRIC,
    val alertStyle: AlertStyle = AlertStyle.ALARM,
    val vibration: VibrationPattern = VibrationPattern.HEARTBEAT,
    val soundUri: String? = null,
    val soundName: String? = null,
    val gradualVolume: Boolean = true,
    val headsUp: Boolean = true,
    val triggerMode: TriggerMode = TriggerMode.DISTANCE,
    val minutesBefore: Int = 3,
    /** With a headset connected, ring only in the headset instead of out loud. */
    val headphonesOnly: Boolean = true,
    /** With a headset connected, speak the heads-up (and the wake-up) over the user's audio. */
    val speak: Boolean = true,
    /** The one-time "star Nod on GitHub" card on Home was answered (either button); never show it again. */
    val starPromptDone: Boolean = false,
)

private val Context.store: DataStore<Preferences> by preferencesDataStore("nod_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val units = stringPreferencesKey("units")
        val alertStyle = stringPreferencesKey("alert_style")
        val vibration = stringPreferencesKey("vibration")
        val soundUri = stringPreferencesKey("sound_uri")
        val soundName = stringPreferencesKey("sound_name")
        val gradual = booleanPreferencesKey("gradual")
        val headsUp = booleanPreferencesKey("heads_up")
        val trigger = stringPreferencesKey("trigger")
        val minutes = intPreferencesKey("minutes")
        val activePlan = stringPreferencesKey("active_plan")
        val headphonesOnly = booleanPreferencesKey("audio_headphones_only")
        val speak = booleanPreferencesKey("audio_speak")
        val starPromptDone = booleanPreferencesKey("star_prompt_done")
    }

    private val defaultUnits =
        if (Locale.getDefault().country in setOf("US", "GB", "LR", "MM")) Units.IMPERIAL else Units.METRIC

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            onboarded = p[K.onboarded] ?: false,
            theme = p[K.theme].enumOr(ThemeMode.SYSTEM),
            units = p[K.units].enumOr(defaultUnits),
            alertStyle = p[K.alertStyle].enumOr(AlertStyle.ALARM),
            vibration = p[K.vibration].enumOr(VibrationPattern.HEARTBEAT),
            soundUri = p[K.soundUri],
            soundName = p[K.soundName],
            gradualVolume = p[K.gradual] ?: true,
            headsUp = p[K.headsUp] ?: true,
            triggerMode = p[K.trigger].enumOr(TriggerMode.DISTANCE),
            minutesBefore = p[K.minutes] ?: 3,
            headphonesOnly = p[K.headphonesOnly] ?: true,
            speak = p[K.speak] ?: true,
            starPromptDone = p[K.starPromptDone] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setOnboarded() = context.store.edit { it[K.onboarded] = true }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[K.theme] = v.name }
    suspend fun setUnits(v: Units) = context.store.edit { it[K.units] = v.name }
    suspend fun setAlertStyle(v: AlertStyle) = context.store.edit { it[K.alertStyle] = v.name }
    suspend fun setVibration(v: VibrationPattern) = context.store.edit { it[K.vibration] = v.name }
    suspend fun setGradual(v: Boolean) = context.store.edit { it[K.gradual] = v }
    suspend fun setHeadsUp(v: Boolean) = context.store.edit { it[K.headsUp] = v }
    suspend fun setTrigger(v: TriggerMode) = context.store.edit { it[K.trigger] = v.name }
    suspend fun setMinutes(v: Int) = context.store.edit { it[K.minutes] = v }
    suspend fun setHeadphonesOnly(v: Boolean) = context.store.edit { it[K.headphonesOnly] = v }
    suspend fun setSpeak(v: Boolean) = context.store.edit { it[K.speak] = v }
    suspend fun setStarPromptDone() = context.store.edit { it[K.starPromptDone] = true }
    suspend fun setSound(uri: String?, name: String?) = context.store.edit {
        if (uri == null) it.remove(K.soundUri) else it[K.soundUri] = uri
        if (name == null) it.remove(K.soundName) else it[K.soundName] = name
    }

    suspend fun saveActivePlan(plan: TripPlan?) = context.store.edit {
        if (plan == null) it.remove(K.activePlan) else it[K.activePlan] = json.encodeToString(TripPlan.serializer(), plan)
    }

    suspend fun activePlan(): TripPlan? = context.store.data.first()[K.activePlan]?.let {
        runCatching { json.decodeFromString(TripPlan.serializer(), it) }.getOrNull()
    }

    private inline fun <reified T : Enum<T>> String?.enumOr(default: T): T =
        this?.let { v -> runCatching { enumValueOf<T>(v) }.getOrNull() } ?: default
}

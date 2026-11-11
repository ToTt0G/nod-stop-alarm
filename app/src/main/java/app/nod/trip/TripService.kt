package app.nod.trip

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.nod.R
import app.nod.alarm.AlarmActivity
import app.nod.core.Format
import app.nod.data.db.TripEntity
import app.nod.data.db.TripOutcome
import app.nod.data.location.LocationSource
import app.nod.data.model.AlertStyle
import app.nod.data.model.TripPlan
import app.nod.data.places.PlacesRepository
import app.nod.data.settings.Settings
import app.nod.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Foreground service that watches the journey. Location cadence adapts to how far away the alarm
 * point is, so a long trip barely touches the battery while the last few minutes are tracked closely.
 */
@AndroidEntryPoint
class TripService : LifecycleService() {
    @Inject lateinit var holder: TripStateHolder
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var places: PlacesRepository
    @Inject lateinit var player: AlarmPlayer
    @Inject lateinit var locations: LocationSource
    @Inject lateinit var json: Json

    private var plan: TripPlan? = null
    private var engine: TripEngine? = null
    private var cadence: TripEngine.Cadence? = null
    private var settings = Settings()
    private var lastNotified = 0L
    private var lastContent: TripNotifications.TripContent? = null
    /** Set when network positioning can't produce fixes (no Wi-Fi/cell location): use satellites. */
    private var networkUnavailable = false
    /** elapsedRealtime when [networkUnavailable] was last set; network positioning is retried later. */
    private var networkUnavailableAt = 0L
    private var lastStats: LiveStats? = null
    private var finishing = false
    /** Bumped by every [begin], so a finish() that was suspended can tell a newer trip has started. */
    private var generation = 0
    private var finishJob: Job? = null
    private var lastStartId = 0
    /** A single deferred repost of the trip notification for an update the throttle held back. */
    private var trailingPost: Job? = null

    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private val alarms by lazy { getSystemService(AlarmManager::class.java) }
    private val watchdog by lazy {
        PendingIntent.getService(
            this, 40, Intent(this, TripService::class.java).setAction(ACTION_WATCHDOG),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
    private var lastFixAt = 0L

    private val callback = object : LocationSource.Listener {
        override fun onLocation(location: Location) {
            this@TripService.onLocation(location)
        }

        override fun onAvailabilityChanged(available: Boolean) {
            val c = cadence ?: return
            if (!available && !c.precise && !networkUnavailable) {
                networkUnavailable = true
                networkUnavailableAt = SystemClock.elapsedRealtime()
                cadence = null
                requestUpdates(c)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TripNotifications.createChannels(this)
        lifecycleScope.launch { settingsRepo.settings.collect { settings = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> {
                val p = intent.getStringExtra(EXTRA_PLAN)
                    ?.let { runCatching { json.decodeFromString(TripPlan.serializer(), it) }.getOrNull() }
                if (p == null) stopSelf() else begin(p, persist = true)
            }
            // A watchdog alarm that outlived a killed process starts a fresh instance, and the system
            // then skips the sticky restart, so it must resume the journey just like that would.
            ACTION_WATCHDOG -> if (engine == null) resumeSaved(startId) else onWatchdog()
            ACTION_STOP -> finish(TripOutcome.ENDED)
            ACTION_DISMISS -> finish(TripOutcome.ARRIVED)
            ACTION_SILENCE -> {
                player.silence()
                holder.update { if (it is TripState.Active) it.copy(silenced = true) else it }
            }
            // Restarted by the system after being killed: resume the saved journey.
            else -> resumeSaved(startId)
        }
        return START_STICKY
    }

    private fun resumeSaved(startId: Int) {
        lifecycleScope.launch {
            val saved = settingsRepo.activePlan()
            when {
                engine != null -> Unit // A start (or another resume) got there first.
                saved == null || finishing -> stopSelf(startId)
                else -> begin(saved, persist = false)
            }
        }
    }

    private fun begin(p: TripPlan, persist: Boolean) {
        finishing = false
        generation++
        trailingPost?.cancel()
        trailingPost = null
        val state = TripState.Active(p)
        try {
            ServiceCompat.startForeground(
                this, TripNotifications.ID_TRIP,
                TripNotifications.trip(this, state, settings.units),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Missing permission or not allowed to start from the background.
            holder.set(TripState.Idle)
            // A resumed journey stays saved so opening Nod can pick it up again.
            if (persist) lifecycleScope.launch { settingsRepo.saveActivePlan(null) }
            stopSelf()
            return
        }
        plan = p
        engine = TripEngine(p)
        lastStats = null
        lastContent = null
        cadence = null
        networkUnavailable = false
        lastFixAt = SystemClock.elapsedRealtime()
        holder.set(state)
        if (persist) lifecycleScope.launch { settingsRepo.saveActivePlan(p) }
        requestUpdates(TripEngine.Cadence.START)
        firstFix()
    }

    private fun firstFix() {
        lifecycleScope.launch {
            locations.currentLocation(precise = true, timeoutMs = FIRST_FIX_TIMEOUT_MS)?.let(::onLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(c: TripEngine.Cadence) {
        if (c == cadence) return
        cadence = c
        val precise = c.precise || networkUnavailable
        val request = LocationSource.UpdateRequest(
            intervalMs = c.intervalMs,
            precise = precise,
            // Free fixes that other apps already paid for are welcome, but not more often than this.
            minUpdateIntervalMs = c.intervalMs / 2,
        )
        runCatching {
            locations.removeUpdates(callback)
            locations.requestUpdates(request, callback, Looper.getMainLooper())
        }
        armWatchdog()
    }

    /**
     * Network positioning can silently produce nothing (location accuracy off, no Wi-Fi/cell).
     * While relying on it, keep an inexact, idle-allowed alarm just past when the next fix is due;
     * every fix pushes it back, so it only ever fires if fixes stopped. Never set with GPS or idle.
     */
    private fun armWatchdog() {
        val c = cadence
        if (c == null || c.precise || networkUnavailable) {
            runCatching { alarms.cancel(watchdog) }
            return
        }
        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + c.watchdogMs, watchdog)
        }
    }

    private fun onWatchdog() {
        val c = cadence ?: return
        if (finishing || engine == null) return
        if (SystemClock.elapsedRealtime() - lastFixAt < c.watchdogMs) return armWatchdog()
        networkUnavailable = true
        networkUnavailableAt = SystemClock.elapsedRealtime()
        cadence = null
        requestUpdates(c)
        firstFix()
    }

    private fun onLocation(loc: Location) {
        val engine = engine ?: return
        val current = holder.state.value as? TripState.Active ?: return
        if (current.phase == TripPhase.ALARM || finishing) return
        lastFixAt = SystemClock.elapsedRealtime()

        val fix = Fix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else 50f,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            // Monotonic clock: immune to GPS week rollovers and wall-clock jumps.
            timeMs = loc.elapsedRealtimeNanos / 1_000_000L,
        )
        val (stats, events) = engine.onFix(fix)
        lastStats = stats
        if (current.plan.startToZoneM == null) {
            // Remember where the journey began so a restarted service keeps the same progress.
            val anchored = current.plan.copy(startToZoneM = stats.startToZoneM)
            plan = anchored
            holder.set(current.copy(plan = anchored))
            lifecycleScope.launch { settingsRepo.saveActivePlan(anchored) }
            return onLocationAfterAnchor(anchored, stats, events)
        }
        onLocationAfterAnchor(current.plan, stats, events)
    }

    private fun onLocationAfterAnchor(planNow: TripPlan, stats: LiveStats, events: List<TripEvent>) {
        val engine = engine ?: return
        val current = (holder.state.value as? TripState.Active)?.copy(plan = planNow) ?: return

        val phase = when {
            TripEvent.ARRIVE in events -> TripPhase.ALARM
            engine.headsUpSent -> TripPhase.HEADS_UP
            else -> TripPhase.TRACKING
        }
        val next = current.copy(phase = phase, live = stats)
        holder.set(next)

        if (TripEvent.HEADS_UP in events) {
            if (current.plan.alertStyle != AlertStyle.SILENT) {
                // Spoken only when a headset is connected; AlarmPlayer checks.
                player.headsUp(if (settings.speak) spoken(R.string.audio_speak_heads_up, current.plan.destination.name) else null)
            }
            nm.notify(TripNotifications.ID_HEADS_UP, TripNotifications.headsUp(this, current.plan.destination.name))
        }
        if (TripEvent.ARRIVE in events) {
            alarm(next)
            return
        }

        // Only repost when something the user can see actually changed; System UI re-renders each post.
        val content = TripNotifications.content(this, next, settings.units)
        if (content != lastContent) {
            val wait = lastNotified + NOTIFY_THROTTLE_MS - SystemClock.elapsedRealtime()
            if (wait <= 0) postTrip(next, content) else postTripLater(next, content, wait)
        }
        val nextCadence = engine.cadenceFor(stats)
        retryNetworkIfDue(nextCadence)
        if (nextCadence == cadence) armWatchdog() else requestUpdates(nextCadence)
    }

    private fun postTrip(state: TripState.Active, content: TripNotifications.TripContent) {
        trailingPost?.cancel()
        trailingPost = null
        lastNotified = SystemClock.elapsedRealtime()
        lastContent = content
        nm.notify(TripNotifications.ID_TRIP, TripNotifications.trip(this, state, settings.units, content))
    }

    /** Throttled: post the newest content once the window ends, unless something newer replaces it. */
    private fun postTripLater(state: TripState.Active, content: TripNotifications.TripContent, waitMs: Long) {
        trailingPost?.cancel()
        val gen = generation
        trailingPost = lifecycleScope.launch {
            delay(waitMs)
            trailingPost = null
            val now = holder.state.value as? TripState.Active ?: return@launch
            if (finishing || engine == null || gen != generation || now.phase == TripPhase.ALARM) return@launch
            if (content != lastContent) postTrip(state, content)
        }
    }

    /**
     * Satellites were forced because network positioning failed. That's often temporary (a tunnel),
     * so give it another go after a while; if it still fails, the watchdog escalates again.
     */
    private fun retryNetworkIfDue(next: TripEngine.Cadence) {
        if (networkUnavailable && !next.precise &&
            SystemClock.elapsedRealtime() - networkUnavailableAt >= NETWORK_RETRY_MS
        ) {
            networkUnavailable = false
            cadence = null // Re-register at balanced priority and re-arm the watchdog.
        }
    }

    private fun alarm(state: TripState.Active) {
        trailingPost?.cancel()
        trailingPost = null
        locations.removeUpdates(callback)
        cadence = null
        runCatching { alarms.cancel(watchdog) }
        val plan = state.plan
        nm.cancel(TripNotifications.ID_HEADS_UP)
        nm.notify(TripNotifications.ID_TRIP, TripNotifications.trip(this, state, settings.units))
        player.start(
            plan.alertStyle, settings.vibration, settings.soundUri, settings.gradualVolume,
            headphonesOnly = settings.headphonesOnly,
            wakeLine = if (settings.speak) spoken(R.string.audio_speak_alarm, plan.destination.name) else null,
            // An earbud/headset button press: same as sliding "I'm awake" (ACTION_DISMISS).
            onAwake = { finish(TripOutcome.ARRIVED) },
        )
        val dist = state.live?.let { Format.distance(it.distanceM, settings.units).toString() }
        nm.notify(TripNotifications.ID_ALARM, TripNotifications.alarm(this, plan.destination.name, dist))
        // Works when Nod is on screen; otherwise the full-screen intent takes over.
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun finish(outcome: TripOutcome) {
        if (finishing) {
            // Already torn down, but a later command kept the service alive: let it go now.
            if (finishJob?.isActive != true) stopSelf(lastStartId)
            return
        }
        finishing = true
        trailingPost?.cancel()
        trailingPost = null
        player.stop()
        runCatching { locations.removeUpdates(callback) }
        runCatching { alarms.cancel(watchdog) }
        nm.cancel(TripNotifications.ID_ALARM)
        nm.cancel(TripNotifications.ID_HEADS_UP)
        val p = plan
        val stats = lastStats
        val arrived = outcome == TripOutcome.ARRIVED || engineArrived()
        val gen = generation
        finishJob = lifecycleScope.launch {
            withContext(NonCancellable) {
                // Clear the saved journey before going Idle: MainActivity resumes a saved plan
                // whenever the holder is Idle, which would bring this trip back.
                try {
                    settingsRepo.saveActivePlan(null)
                } finally {
                    if (gen == generation) holder.set(TripState.Idle)
                }
                if (p != null) {
                    val d = p.destination
                    places.record(
                        TripEntity(
                            name = d.name, subtitle = d.subtitle, lat = d.lat, lon = d.lon, kind = d.kind,
                            radiusM = d.radiusM, startedAt = p.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                            endedAt = System.currentTimeMillis(),
                            outcome = if (arrived) TripOutcome.ARRIVED else TripOutcome.ENDED,
                            distanceM = stats?.distanceM,
                        ),
                    )
                    p.savedPlaceId?.let { places.markUsed(it) }
                }
            }
            // A receipt only when it offers something: saving a stop you haven't saved yet. A saved
            // stop the user just acknowledged ("I'm awake" / End) leaves nothing behind.
            if (p != null && p.savedPlaceId == null && places.findSaved(p.destination.lat, p.destination.lon) == null) {
                val travelled = stats?.let { (it.startToZoneM - it.toZoneM).coerceAtLeast(0.0) }
                runCatching { nm.notify(TripNotifications.ID_SUMMARY, TripNotifications.summary(this@TripService, p, arrived, travelled, settings.units)) }
            }
            // A new journey began while this one was being saved: it owns the service now.
            if (gen != generation) return@launch
            plan = null
            engine = null
            ServiceCompat.stopForeground(this@TripService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Only stops if no newer command (e.g. a queued start) has arrived since.
            stopSelf(lastStartId)
        }
    }

    private fun engineArrived() = engine?.arrived == true

    override fun onDestroy() {
        runCatching { locations.removeUpdates(callback) }
        runCatching { alarms.cancel(watchdog) }
        if (!finishing) player.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.nod.START"
        const val ACTION_STOP = "app.nod.STOP"
        const val ACTION_DISMISS = "app.nod.DISMISS"
        const val ACTION_SILENCE = "app.nod.SILENCE"
        const val ACTION_WATCHDOG = "app.nod.WATCHDOG"
        const val EXTRA_PLAN = "plan"

        private const val NOTIFY_THROTTLE_MS = 1_500L
        private const val NETWORK_RETRY_MS = 15 * 60_000L
        /** A one-off precise fix to get going; continuous updates are running alongside it. */
        private const val FIRST_FIX_TIMEOUT_MS = 60_000L
    }
}

package app.nod.trip

import app.nod.core.Geo
import app.nod.data.model.TripPlan
import app.nod.data.model.TriggerMode

data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val timeMs: Long,
)

data class LiveStats(
    val lat: Double,
    val lon: Double,
    val distanceM: Double,
    /** Distance until the zone edge; 0 once inside. */
    val toZoneM: Double,
    /** Current speed, smoothed over the last few fixes. Drops to 0 at a stop. */
    val speedMps: Double,
    /**
     * Expected speed for the rest of the ride (see [TravelPace]): an average over time spent moving,
     * so stops at stations, bus stops and lights don't count, that follows changes of pace such as
     * a train slowing down into a city. This is what arrival estimates use, so they don't jump
     * around every time the vehicle stops. 0 until enough movement has been seen.
     */
    val travelSpeedMps: Double = 0.0,
    /** Seconds until the alarm is expected to fire, null until we have seen enough movement. */
    val alarmInSec: Double?,
    /** 0..1 journey progress towards the alarm point. */
    val progress: Float,
    val accuracyM: Float,
    val timeMs: Long,
    /** Distance to the zone edge when tracking began; anchors the progress bar. */
    val startToZoneM: Double = 0.0,
)

enum class TripEvent { HEADS_UP, ARRIVE }

/**
 * Pure decision logic for a single journey: turns location fixes into live stats and events.
 * Kept free of Android types so it can be unit tested.
 */
class TripEngine(private val plan: TripPlan) {
    private val dest = plan.destination
    private val radius = dest.radiusM.toDouble()

    private var startToZone: Double? = plan.startToZoneM
    private var lastFix: Fix? = null
    private var speed = 0.0
    private val pace = TravelPace()
    private var insideStreak = 0
    private var timeStreak = 0
    private var fixCount = 0

    var headsUpSent = false
        private set
    var arrived = false
        private set

    fun onFix(fix: Fix): Pair<LiveStats, List<TripEvent>> {
        val distance = Geo.distance(fix.lat, fix.lon, dest.lat, dest.lon)
        val toZone = (distance - radius).coerceAtLeast(0.0)
        updateSpeed(fix)
        lastFix = fix
        fixCount++

        val start = startToZone ?: toZone.also { startToZone = it }
        val progress = if (start <= 1.0) 1f else (1.0 - toZone / start).coerceIn(0.0, 1.0).toFloat()

        // Estimates use the expected travel pace, so stops along the way don't stall them. Until
        // there's enough movement for an average, fall back to the current speed.
        val travel = pace.speedFor(distance)
        val etaSpeed = travel ?: speed.takeIf { it >= MOVING_MPS }
        val etaDest = etaSpeed?.let { distance / it }
        val alarmIn: Double? = when {
            arrived -> 0.0
            etaSpeed == null -> null
            plan.triggerMode == TriggerMode.TIME ->
                minOf((etaDest!! - plan.minutesBefore * 60.0), toZone / etaSpeed).coerceAtLeast(0.0)
            else -> toZone / etaSpeed
        }

        val events = mutableListOf<TripEvent>()
        if (!arrived) {
            // Coarse fixes must agree twice before we wake anyone up.
            val trustworthy = fix.accuracyM <= maxOf(radius, 150.0)
            insideStreak = if (distance <= radius) insideStreak + 1 else 0
            val insideZone = insideStreak >= (if (trustworthy) 1 else 2)

            val timeHit = plan.triggerMode == TriggerMode.TIME && etaDest != null &&
                etaDest <= plan.minutesBefore * 60.0
            timeStreak = if (timeHit) timeStreak + 1 else 0

            if (insideZone || timeStreak >= 2) {
                arrived = true
                events += TripEvent.ARRIVE
            } else if (plan.headsUp && !headsUpSent && shouldHeadsUp(toZone, alarmIn)) {
                headsUpSent = true
                events += TripEvent.HEADS_UP
            }
        }

        val stats = LiveStats(
            lat = fix.lat,
            lon = fix.lon,
            distanceM = distance,
            toZoneM = toZone,
            speedMps = speed,
            travelSpeedMps = travel ?: 0.0,
            alarmInSec = if (arrived) 0.0 else alarmIn,
            progress = if (arrived) 1f else progress,
            accuracyM = fix.accuracyM,
            timeMs = fix.timeMs,
            startToZoneM = start,
        )
        return stats to events
    }

    private fun shouldHeadsUp(toZone: Double, alarmIn: Double?): Boolean {
        // Don't nag if the trip started basically on top of the zone.
        val start = startToZone ?: return false
        if (start < 300) return false
        return if (alarmIn != null) alarmIn <= HEADS_UP_SEC else toZone <= radius
    }

    private fun updateSpeed(fix: Fix) {
        val prev = lastFix
        // Prefer real displacement over the chip's reported speed (some devices report 0 while moving);
        // fall back to the reported value when we haven't moved beyond GPS noise.
        val fromDelta: Double? = if (prev != null && fix.timeMs > prev.timeMs) {
            val d = Geo.distance(prev.lat, prev.lon, fix.lat, fix.lon)
            val dt = (fix.timeMs - prev.timeMs) / 1000.0
            val v = if (d > (fix.accuracyM + prev.accuracyM) * 0.5 && dt >= 0.5) d / dt else null
            // Only stretches where we were really travelling count towards the average; standing still
            // (and walking pace, e.g. to the platform) is left out.
            if (v != null && v >= TRAVEL_MIN_MPS && v < MAX_PLAUSIBLE_MPS) {
                pace.add(d, dt)
            }
            v
        } else null
        val measured = fromDelta ?: fix.speedMps?.toDouble()?.takeIf { it >= 0 }
        if (measured != null && measured < MAX_PLAUSIBLE_MPS) {
            speed = if (speed == 0.0) measured else speed * (1 - SMOOTHING) + measured * SMOOTHING
        }
    }

    /**
     * How often to ask for location. Each check may cover at most a quarter of the remaining way to
     * the alarm point, even at a generous assumed speed, so the interval shrinks geometrically as the
     * stop approaches: minutes apart on a long ride, seconds apart in the final stretch.
     */
    fun cadenceFor(stats: LiveStats?): Cadence {
        // Two quick satellite fixes first, so the speed is measured before backing off.
        if (stats == null || arrived || fixCount < 2) return Cadence.START
        // Assume we're at least as fast as a regional train (or 1.5x what we measured, if faster)
        // and that the fix is off by its full accuracy towards the stop. Never sleep through a stop.
        val assumed = maxOf(stats.speedMps * 1.5, ASSUMED_MIN_MPS)
        val toZone = (stats.toZoneM - stats.accuracyM).coerceAtLeast(0.0)
        var secondsAway = toZone / assumed
        if (plan.triggerMode == TriggerMode.TIME) {
            val toDest = (stats.distanceM - stats.accuracyM).coerceAtLeast(0.0)
            secondsAway = minOf(secondsAway, (toDest / assumed - plan.minutesBefore * 60.0).coerceAtLeast(0.0))
        }
        val target = secondsAway * 1000.0 / CHECKS_PER_STRETCH
        val interval = INTERVALS.lastOrNull { it <= target } ?: INTERVALS.first()
        // Wi-Fi/cell positioning is plenty when the stop is far; satellites for the final approach.
        return Cadence(interval, precise = interval < PRECISE_BELOW_MS)
    }

    /** A location request: how often, and whether satellite precision is needed. */
    data class Cadence(val intervalMs: Long, val precise: Boolean) {
        /**
         * If no fix has arrived by now, network positioning isn't working (it's off, or there's no
         * Wi-Fi/cell to locate from) and the service must fall back to satellites.
         */
        val watchdogMs: Long get() = intervalMs * 5 / 4 + 20_000

        companion object {
            val START = Cadence(3_000, precise = true)
        }
    }

    companion object {
        const val MOVING_MPS = 1.5
        /** Slower than this (about 9 km/h) doesn't count towards the travel average: stopped or walking. */
        const val TRAVEL_MIN_MPS = 2.5
        const val MAX_PLAUSIBLE_MPS = 140.0 // ~500 km/h; high-speed rail with margin
        const val SMOOTHING = 0.35
        const val HEADS_UP_SEC = 120.0
        const val ASSUMED_MIN_MPS = 25.0 // 90 km/h
        const val CHECKS_PER_STRETCH = 4.0
        const val PRECISE_BELOW_MS = 120_000L

        /** Quantised so a moving trip doesn't re-register its location request on every fix. */
        val INTERVALS = longArrayOf(3_000, 5_000, 10_000, 20_000, 30_000, 60_000, 120_000, 180_000, 300_000)
    }
}

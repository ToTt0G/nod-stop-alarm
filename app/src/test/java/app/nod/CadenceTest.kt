package app.nod

import app.nod.core.Geo
import app.nod.data.model.Destination
import app.nod.data.model.PlaceKind
import app.nod.data.model.TriggerMode
import app.nod.data.model.TripPlan
import app.nod.trip.Fix
import app.nod.trip.LiveStats
import app.nod.trip.TripEngine
import app.nod.trip.TripEvent
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Battery vs. safety for the adaptive location cadence. The simulator feeds the engine exactly as the
 * service does: the next fix arrives only after the interval the engine asked for.
 */
class CadenceTest {
    private fun dest(radius: Int) = Destination("Stop", lat = 59.3303, lon = 18.0581, kind = PlaceKind.TRAIN, radiusM = radius)

    /** An engine that has already had its first two fixes, as in a running trip. */
    private fun engine(plan: TripPlan): TripEngine = TripEngine(plan).apply {
        repeat(2) { onFix(Fix(plan.destination.lat + 1.0, plan.destination.lon, 10f, null, it * 3_000L)) }
    }

    private fun stats(toZoneM: Double, speed: Double = 0.0, accuracy: Float = 10f, radius: Int = 500) = LiveStats(
        lat = 0.0, lon = 0.0, distanceM = toZoneM + radius, toZoneM = toZoneM, speedMps = speed,
        alarmInSec = null, progress = 0f, accuracyM = accuracy, timeMs = 0,
    )

    @Test fun `far away, checks are minutes apart and use network positioning`() {
        val c = engine(TripPlan(dest(500))).cadenceFor(stats(100_000.0, speed = 30.0))
        assertThat(c.intervalMs).isEqualTo(300_000)
        assertThat(c.precise).isFalse()
    }

    @Test fun `final approach is tracked every few seconds by satellite`() {
        val c = engine(TripPlan(dest(500))).cadenceFor(stats(300.0, speed = 20.0))
        assertThat(c.intervalMs).isEqualTo(3_000)
        assertThat(c.precise).isTrue()
    }

    @Test fun `interval shrinks monotonically as the stop approaches`() {
        val engine = engine(TripPlan(dest(500)))
        val intervals = (300 downTo 0).map { km -> engine.cadenceFor(stats(km * 1000.0, speed = 30.0)).intervalMs }
        assertThat(intervals).isInOrder(Comparator<Long> { a, b -> b.compareTo(a) })
    }

    @Test fun `a coarse fix counts as closer than it claims`() {
        val engine = engine(TripPlan(dest(500)))
        val sharp = engine.cadenceFor(stats(20_000.0, accuracy = 10f))
        val coarse = engine.cadenceFor(stats(20_000.0, accuracy = 10_000f))
        assertThat(coarse.intervalMs).isLessThan(sharp.intervalMs)
    }

    @Test fun `time mode tightens around the wake-up point, not the zone`() {
        val zoneOnly = engine(TripPlan(dest(500))).cadenceFor(stats(12_000.0, speed = 20.0))
        val tenMinutesBefore = engine(TripPlan(dest(500), triggerMode = TriggerMode.TIME, minutesBefore = 10))
            .cadenceFor(stats(12_000.0, speed = 20.0))
        assertThat(tenMinutesBefore.intervalMs).isLessThan(zoneOnly.intervalMs)
    }

    // --- Whole-journey simulations ---------------------------------------------------------------

    private data class Sim(
        val fixes: Int,
        val preciseFixes: Int,
        /** True distance to the stop when the alarm went off. */
        val alarmDistanceM: Double?,
        /** Seconds between physically entering the zone and the alarm. */
        val lateSec: Double?,
        /** Seconds of warning the heads-up gave before entering the zone. */
        val headsUpLeadSec: Double?,
        val alarmEtaSec: Double?,
    )

    /**
     * Travels in a straight line toward the stop. Network fixes are pessimistic: reported a full
     * accuracy radius further away than the truth.
     */
    private fun simulate(
        plan: TripPlan,
        startM: Double,
        speedAt: (tSec: Double) -> Double,
        legacy: Boolean = false,
        networkWorks: Boolean = true,
    ): Sim {
        val engine = TripEngine(plan)
        val r = plan.destination.radiusM.toDouble()
        var t = 0.0
        var remaining = startM
        var cadence = TripEngine.Cadence.START
        var fixes = 0
        var preciseCount = 0
        var enteredAt: Double? = null
        var headsUpAt: Double? = null
        var satellitesOnly = false
        while (t < 12 * 3600) {
            val precise = cadence.precise || satellitesOnly
            val acc = if (precise) 8f else 150f
            val reported = remaining + if (precise) 0.0 else acc.toDouble()
            val p = Geo.offset(plan.destination.lat, plan.destination.lon, reported, 180.0)
            fixes++
            if (precise) preciseCount++
            val (stats, events) = engine.onFix(Fix(p.lat, p.lon, acc, null, (t * 1000).toLong()))
            if (TripEvent.HEADS_UP in events) headsUpAt = t
            if (TripEvent.ARRIVE in events) {
                val etaSec = remaining / speedAt(t).coerceAtLeast(0.1)
                return Sim(fixes, preciseCount, remaining, enteredAt?.let { t - it } ?: 0.0, headsUpAt?.let { h -> enteredAt(startM, r, speedAt)?.let { it - h } }, etaSec)
            }
            cadence = if (legacy) legacyCadence(stats) else engine.cadenceFor(stats)
            var wait = cadence.intervalMs / 1000.0
            if (!cadence.precise && !satellitesOnly && !networkWorks) {
                // No network fix ever comes: the watchdog fires, then GPS needs a cold-ish start.
                wait = cadence.watchdogMs / 1000.0 + GPS_START_SEC
                satellitesOnly = true
            }
            // Advance in 100 ms steps so speed changes inside an interval are honoured.
            val end = t + wait
            while (t < end) {
                remaining -= speedAt(t) * 0.1
                t += 0.1
                if (enteredAt == null && remaining <= r) enteredAt = t
            }
        }
        return Sim(fixes, preciseCount, null, null, null, null)
    }

    private companion object {
        const val GPS_START_SEC = 15.0
    }

    private fun enteredAt(startM: Double, r: Double, speedAt: (Double) -> Double): Double? {
        var t = 0.0
        var remaining = startM
        while (t < 12 * 3600) {
            if (remaining <= r) return t
            remaining -= speedAt(t) * 0.1
            t += 0.1
        }
        return null
    }

    /** The previous fixed-step scheme, kept here as the battery baseline. */
    private fun legacyCadence(s: LiveStats): TripEngine.Cadence {
        val secondsAway = s.toZoneM / maxOf(s.speedMps, 20.0)
        val ms = when {
            secondsAway > 30 * 60 -> 60_000L
            secondsAway > 10 * 60 -> 25_000L
            secondsAway > 3 * 60 -> 10_000L
            else -> 3_000L
        }
        return TripEngine.Cadence(ms, precise = true)
    }

    @Test fun `alarm fires inside the zone, promptly, for buses, trams and trains`() {
        val cases = listOf(
            Triple(PlaceKind.BUS, 450, 10.0),
            Triple(PlaceKind.TRAM, 450, 14.0),
            Triple(PlaceKind.METRO, 600, 22.0),
            Triple(PlaceKind.TRAIN, 2000, 45.0),
            Triple(PlaceKind.TRAIN, 2000, 80.0), // high-speed rail
            Triple(PlaceKind.PLACE, 150, 8.0), // a tight custom circle
        )
        for ((kind, radius, mps) in cases) {
            for (startKm in listOf(3, 20, 80, 250)) {
                val plan = TripPlan(Destination("Stop", lat = 59.3303, lon = 18.0581, kind = kind, radiusM = radius))
                val sim = simulate(plan, startKm * 1000.0, { mps })
                val label = "$kind r=$radius @${mps}m/s from ${startKm}km"
                assertWithMessage(label).that(sim.alarmDistanceM).isNotNull()
                assertWithMessage(label).that(sim.alarmDistanceM!!).isAtMost(radius.toDouble())
                assertWithMessage(label).that(sim.alarmDistanceM).isGreaterThan(-radius.toDouble()) // not past the stop
                assertWithMessage(label).that(sim.lateSec!!).isAtMost(6.0)
                if (startKm >= 20) assertWithMessage(label).that(sim.headsUpLeadSec!!).isAtLeast(60.0)
            }
        }
    }

    @Test fun `a sudden burst to 360 km-h after crawling is still caught`() {
        val plan = TripPlan(dest(2000))
        // Crawl at 5 m/s for 10 minutes so the engine learns a slow speed, then rocket at 100 m/s.
        val sim = simulate(plan, 60_000.0, { t -> if (t < 600) 5.0 else 100.0 })
        assertThat(sim.alarmDistanceM).isNotNull()
        assertThat(sim.alarmDistanceM!!).isAtMost(2000.0)
        assertThat(sim.alarmDistanceM).isGreaterThan(0.0)
    }

    @Test fun `if network positioning never answers, the watchdog still gets you woken in the zone`() {
        for ((radius, mps) in listOf(450 to 10.0, 600 to 22.0, 2000 to 45.0, 2000 to 80.0)) {
            for (startKm in listOf(20, 80, 250)) {
                val sim = simulate(TripPlan(dest(radius)), startKm * 1000.0, { mps }, networkWorks = false)
                val label = "r=$radius @${mps}m/s from ${startKm}km, no network fixes"
                assertWithMessage(label).that(sim.alarmDistanceM).isNotNull()
                assertWithMessage(label).that(sim.alarmDistanceM!!).isAtMost(radius.toDouble())
                assertWithMessage(label).that(sim.alarmDistanceM).isGreaterThan(-radius.toDouble())
                assertWithMessage(label).that(sim.lateSec!!).isAtMost(6.0)
            }
        }
    }

    @Test fun `standing still on the platform costs a check every few minutes`() {
        val engine = engine(TripPlan(dest(500)))
        val c = engine.cadenceFor(stats(40_000.0, speed = 0.0))
        assertThat(c.intervalMs).isAtLeast(180_000)
    }

    @Test fun `time mode wakes you on time`() {
        val plan = TripPlan(dest(500), triggerMode = TriggerMode.TIME, minutesBefore = 5)
        val sim = simulate(plan, 40_000.0, { 20.0 })
        assertThat(sim.alarmEtaSec!!).isIn(com.google.common.collect.Range.closed(4 * 60.0, 5 * 60.0 + 10))
    }

    @Test fun `a 100 km train ride uses at least 3x fewer fixes and 4x fewer satellite fixes than before`() {
        val plan = TripPlan(dest(2000))
        val now = simulate(plan, 100_000.0, { 30.0 })
        val before = simulate(plan, 100_000.0, { 30.0 }, legacy = true)
        println("100 km @108 km/h: fixes ${before.fixes} -> ${now.fixes}, satellite fixes ${before.preciseFixes} -> ${now.preciseFixes}")
        assertThat(now.fixes * 3).isAtMost(before.fixes)
        assertThat(now.preciseFixes * 4).isAtMost(before.preciseFixes)
    }

    @Test fun `a 15 km bus ride is cheaper too`() {
        val plan = TripPlan(Destination("Stop", lat = 59.3303, lon = 18.0581, kind = PlaceKind.BUS, radiusM = 450))
        val now = simulate(plan, 15_000.0, { 9.0 })
        val before = simulate(plan, 15_000.0, { 9.0 }, legacy = true)
        println("15 km @32 km/h: fixes ${before.fixes} -> ${now.fixes}, satellite fixes ${before.preciseFixes} -> ${now.preciseFixes}")
        assertThat(now.fixes).isLessThan(before.fixes)
    }
}

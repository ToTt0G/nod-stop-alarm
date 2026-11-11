package app.nod

import app.nod.core.Geo
import app.nod.data.model.Destination
import app.nod.data.model.PlaceKind
import app.nod.data.model.TriggerMode
import app.nod.data.model.TripPlan
import app.nod.trip.Fix
import app.nod.trip.TripEngine
import app.nod.trip.TripEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TripEngineTest {
    private val dest = Destination("Odenplan", lat = 59.3430, lon = 18.0496, kind = PlaceKind.METRO, radiusM = 500)

    /** Fixes travelling due north toward [dest] at [mps], one every [stepSec]. */
    private fun ride(startM: Double, mps: Double, stepSec: Int = 5, accuracy: Float = 8f): Sequence<Fix> = sequence {
        var t = 0L
        var remaining = startM
        while (remaining > -200) {
            val p = Geo.offset(dest.lat, dest.lon, remaining, 180.0)
            yield(Fix(p.lat, p.lon, accuracy, null, t))
            remaining -= mps * stepSec
            t += stepSec * 1000L
        }
    }

    @Test fun `alarm fires once on entering the zone`() {
        val engine = TripEngine(TripPlan(dest))
        val events = ride(5_000.0, 20.0).flatMap { engine.onFix(it).second }.toList()
        assertThat(events.count { it == TripEvent.ARRIVE }).isEqualTo(1)
        assertThat(events.indexOf(TripEvent.HEADS_UP)).isLessThan(events.indexOf(TripEvent.ARRIVE))
    }

    @Test fun `alarm does not fire before the zone`() {
        val engine = TripEngine(TripPlan(dest))
        ride(5_000.0, 20.0).takeWhile { Geo.distance(it.lat, it.lon, dest.lat, dest.lon) > 560 }.forEach {
            assertThat(engine.onFix(it).second).doesNotContain(TripEvent.ARRIVE)
        }
    }

    @Test fun `speed is measured from displacement`() {
        val engine = TripEngine(TripPlan(dest))
        val last = ride(5_000.0, 25.0).take(8).map { engine.onFix(it).first }.last()
        assertThat(last.speedMps).isWithin(1.0).of(25.0)
        assertThat(last.alarmInSec).isNotNull()
    }

    @Test fun `time mode fires minutes before arrival, ahead of the zone`() {
        val engine = TripEngine(TripPlan(dest.copy(radiusM = 200), triggerMode = TriggerMode.TIME, minutesBefore = 2))
        var firedAt: Double? = null
        for (f in ride(8_000.0, 20.0)) {
            val (stats, ev) = engine.onFix(f)
            if (TripEvent.ARRIVE in ev) { firedAt = stats.distanceM; break }
        }
        // 2 min at 20 m/s is 2.4 km; allow for the 2-fix confirmation.
        assertThat(firedAt).isNotNull()
        assertThat(firedAt!!).isIn(com.google.common.collect.Range.closed(2_000.0, 2_500.0))
    }

    @Test fun `a single wildly inaccurate fix inside the zone is not trusted`() {
        val engine = TripEngine(TripPlan(dest))
        engine.onFix(Fix(59.30, 18.05, 10f, null, 0))
        val (_, ev) = engine.onFix(Fix(dest.lat, dest.lon, 2_000f, null, 5_000))
        assertThat(ev).doesNotContain(TripEvent.ARRIVE)
        val (_, ev2) = engine.onFix(Fix(dest.lat, dest.lon, 2_000f, null, 10_000))
        assertThat(ev2).contains(TripEvent.ARRIVE)
    }

    @Test fun `progress persists via the plan's anchor`() {
        val first = TripEngine(TripPlan(dest))
        val s1 = ride(5_000.0, 20.0).take(1).map { first.onFix(it).first }.single()
        val resumed = TripEngine(TripPlan(dest, startToZoneM = s1.startToZoneM))
        val mid = Geo.offset(dest.lat, dest.lon, 2_750.0, 180.0)
        val s2 = resumed.onFix(Fix(mid.lat, mid.lon, 8f, null, 0)).first
        assertThat(s2.progress).isWithin(0.02f).of(0.5f)
    }
}

package app.nod

import app.nod.trip.TravelPace
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TravelPaceTest {
    /** Feeds [minutes] of travel at [mps], as 5-second stretches like real fixes. */
    private fun TravelPace.drive(minutes: Double, mps: Double) = apply {
        repeat((minutes * 12).toInt()) { add(mps * 5, 5.0) }
    }

    @Test fun `nothing until there's been some movement`() {
        val p = TravelPace().drive(0.1, 20.0)
        assertThat(p.speedFor(10_000.0)).isNull()
    }

    @Test fun `steady pace`() {
        assertThat(TravelPace().drive(10.0, 12.0).speedFor(5_000.0)!!).isWithin(0.01).of(12.0)
    }

    @Test fun `slowing into a city switches to the new pace`() {
        // 20 min intercity at 108 km/h, then 10 min crawling in at 36 km/h, 10 km still to go.
        val p = TravelPace().drive(20.0, 30.0).drive(10.0, 10.0)
        assertThat(p.speedFor(10_000.0)!!).isWithin(0.1).of(10.0)
    }

    @Test fun `back up to speed after a slow town`() {
        // Fast, 4 min slowly through a town, then fast again: the slow stretch is behind us.
        val p = TravelPace().drive(20.0, 30.0).drive(4.0, 12.0).drive(5.0, 30.0)
        assertThat(p.speedFor(30_000.0)!!).isWithin(0.1).of(30.0)
    }

    @Test fun `one slow minute isn't a change of pace`() {
        // A signal check: one minute at half speed in an otherwise fast run is averaged in, not a cut.
        val p = TravelPace().drive(20.0, 30.0).drive(1.0, 15.0).drive(5.0, 30.0)
        val v = p.speedFor(30_000.0)!!
        assertThat(v).isLessThan(30.0)
        assertThat(v).isGreaterThan(28.0)
    }

    @Test fun `far away a varied ride averages out, close by the latest pace counts`() {
        // 30 min alternating 18 and 26 m/s every 5 min (close enough to count as one pace).
        val p = TravelPace()
        repeat(3) { p.drive(5.0, 18.0).drive(5.0, 26.0) }
        // 50 km to go: looks back over the whole ride.
        assertThat(p.speedFor(50_000.0)!!).isWithin(0.5).of(22.0)
        // 3 km to go: only the last few minutes.
        assertThat(p.speedFor(3_000.0)!!).isWithin(0.1).of(26.0)
    }

    @Test fun `a gradual slowdown is followed`() {
        // From 30 down to 10 m/s over 30 min, no sharp change anywhere.
        val p = TravelPace()
        for (minute in 0 until 30) p.drive(1.0, 30.0 - 20.0 * minute / 29)
        val v = p.speedFor(10_000.0)!!
        // A whole-ride average would say 20; the estimate stays near the recent pace.
        assertThat(v).isLessThan(15.0)
        assertThat(v).isGreaterThan(10.0)
    }

    @Test fun `long stretches between fixes are spread over the boxes`() {
        // Far from the stop, fixes can be 5 min apart.
        val p = TravelPace()
        repeat(6) { p.add(30.0 * 300, 300.0) }
        assertThat(p.speedFor(60_000.0)!!).isWithin(0.01).of(30.0)
    }

    // Trains: about 200 km/h (55 m/s) between cities, about 40 km/h (11 m/s) through them.

    @Test fun `slow start, fast middle, slow end`() {
        val p = TravelPace().drive(10.0, 11.0)
        // Two minutes after reaching full speed, mostly caught up; after three, fully.
        p.drive(2.0, 55.0)
        assertThat(p.speedFor(100_000.0)!!).isGreaterThan(35.0)
        p.drive(1.0, 55.0)
        assertThat(p.speedFor(100_000.0)!!).isWithin(0.1).of(55.0)
        // 40 min at full speed, then slowing to 13 m/s on the approach; 5 km to go.
        p.drive(37.0, 55.0).drive(8.0, 13.0)
        assertThat(p.speedFor(5_000.0)!!).isWithin(0.1).of(13.0)
    }

    @Test fun `long fast run isn't diluted by the slow start`() {
        val p = TravelPace().drive(15.0, 11.0).drive(30.0, 55.0)
        assertThat(p.speedFor(150_000.0)!!).isWithin(0.1).of(55.0)
    }

    @Test fun `steps between 200 and 120 and 40 km per h`() {
        val p = TravelPace().drive(20.0, 55.0).drive(10.0, 33.0)
        // 55 to 33 is a change of pace (1.67x): the estimate follows the slower stretch.
        assertThat(p.speedFor(40_000.0)!!).isWithin(0.1).of(33.0)
        p.drive(6.0, 11.0)
        assertThat(p.speedFor(4_000.0)!!).isWithin(0.1).of(11.0)
    }
}

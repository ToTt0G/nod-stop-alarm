package app.nod

import app.nod.core.Format
import app.nod.core.Geo
import app.nod.core.RadiusScale
import app.nod.data.model.Units
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class GeoTest {
    @Test fun `distance Stockholm C to Uppsala C is about 64 km`() {
        val d = Geo.distance(59.3303, 18.0581, 59.8586, 17.6454)
        assertThat(d).isWithin(1500.0).of(64_000.0)
    }

    @Test fun `offset then distance round-trips`() {
        val p = Geo.offset(59.33, 18.06, 1234.0, 37.0)
        assertThat(Geo.distance(59.33, 18.06, p.lat, p.lon)).isWithin(0.5).of(1234.0)
    }

    @Test fun `circle is closed and every point sits on the radius`() {
        val ring = Geo.circle(52.0, 5.0, 800.0, steps = 32)
        assertThat(ring.first().lat).isWithin(1e-9).of(ring.last().lat)
        ring.forEach { assertThat(Geo.distance(52.0, 5.0, it.lat, it.lon)).isWithin(0.5).of(800.0) }
    }

    @Test fun `radius scale is monotonic and snaps to friendly numbers`() {
        var last = 0
        for (i in 0..100) {
            val r = RadiusScale.fromFraction(i / 100f)
            assertThat(r).isAtLeast(last)
            last = r
        }
        assertThat(RadiusScale.snap(437.0)).isEqualTo(430)
        assertThat(RadiusScale.snap(2_437.0)).isEqualTo(2_400)
        assertThat(RadiusScale.snap(12_400.0)).isEqualTo(12_250)
        assertThat(RadiusScale.fromFraction(0f)).isEqualTo(100)
        assertThat(RadiusScale.fromFraction(1f)).isEqualTo(20_000)
    }

    @Test fun `distance formatting`() {
        assertThat(Format.distance(84.0, Units.METRIC, Locale.ROOT).toString()).isEqualTo("80 m")
        assertThat(Format.distance(1_540.0, Units.METRIC, Locale.ROOT).toString()).isEqualTo("1.5 km")
        assertThat(Format.distance(23_600.0, Units.METRIC, Locale.ROOT).toString()).isEqualTo("24 km")
        assertThat(Format.distance(1_609.344, Units.IMPERIAL, Locale.ROOT).toString()).isEqualTo("1.0 mi")
        assertThat(Format.distance(100.0, Units.IMPERIAL, Locale.ROOT).unit).isEqualTo("ft")
    }

    @Test fun `duration formatting`() {
        assertThat(Format.duration(42.0)).isEqualTo("42 s")
        assertThat(Format.duration(250.0)).isEqualTo("4 min")
        assertThat(Format.duration(3_900.0)).isEqualTo("1 h 05")
    }
}

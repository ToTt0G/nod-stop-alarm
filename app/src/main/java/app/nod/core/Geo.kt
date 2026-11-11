package app.nod.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distance(a: GeoPoint, b: GeoPoint) = distance(a.lat, a.lon, b.lat, b.lon)

    /** The point reached travelling [distanceM] from the origin on [bearingDeg]. */
    fun offset(lat: Double, lon: Double, distanceM: Double, bearingDeg: Double): GeoPoint {
        val d = distanceM / EARTH_RADIUS_M
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return GeoPoint(Math.toDegrees(p2), (Math.toDegrees(l2) + 540) % 360 - 180)
    }

    /** A closed ring of points approximating a circle on the earth's surface. */
    fun circle(lat: Double, lon: Double, radiusM: Double, steps: Int = 96): List<GeoPoint> =
        (0..steps).map { i -> offset(lat, lon, radiusM, 360.0 * i / steps) }

    /** Rough zoom level at which a circle of [radiusM] spans about [fraction] of a [widthPx]-wide view. */
    fun zoomForRadius(radiusM: Double, lat: Double, widthDp: Double, fraction: Double = 0.62): Double {
        val metersPerDpAtZoom0 = 156_543.03392 * cos(Math.toRadians(lat)) / 2 // 512-tile vector maps
        val targetMetersPerDp = (radiusM * 2) / (widthDp * fraction)
        return (ln(metersPerDpAtZoom0 / targetMetersPerDp) / ln(2.0)).coerceIn(3.0, 18.0)
    }
}

/** Log-scale mapping for the radius slider so small zones get fine control. */
object RadiusScale {
    const val MIN_M = 100.0
    const val MAX_M = 20_000.0

    fun toFraction(radiusM: Double): Float =
        (ln(radiusM.coerceIn(MIN_M, MAX_M) / MIN_M) / ln(MAX_M / MIN_M)).toFloat()

    fun fromFraction(f: Float): Int {
        val raw = MIN_M * (MAX_M / MIN_M).pow(f.coerceIn(0f, 1f).toDouble())
        return snap(raw)
    }

    /** Snap to human numbers: 10 m steps below 1 km, 50 m to 5 km, 250 m beyond. */
    fun snap(raw: Double): Int {
        val step = when {
            raw < 1000 -> 10
            raw < 5000 -> 50
            else -> 250
        }
        return ((raw / step).toInt() * step).coerceIn(MIN_M.toInt(), MAX_M.toInt())
    }
}

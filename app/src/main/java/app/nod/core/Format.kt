package app.nod.core

import app.nod.data.model.Units
import java.util.Locale
import kotlin.math.roundToInt

/** A number and its unit, split so the UI can style them differently. */
data class Measure(val value: String, val unit: String) {
    override fun toString() = "$value $unit"
}

object Format {
    private const val M_PER_MILE = 1609.344
    private const val M_PER_FOOT = 0.3048

    fun distance(meters: Double, units: Units, locale: Locale = Locale.getDefault()): Measure {
        val m = meters.coerceAtLeast(0.0)
        return when (units) {
            Units.METRIC -> when {
                m < 1000 -> Measure(((m / 10).roundToInt() * 10).toString(), "m")
                m < 10_000 -> Measure(String.format(locale, "%.1f", m / 1000), "km")
                else -> Measure((m / 1000).roundToInt().toString(), "km")
            }
            Units.IMPERIAL -> {
                val miles = m / M_PER_MILE
                when {
                    miles < 0.1 -> Measure(((m / M_PER_FOOT / 10).roundToInt() * 10).toString(), "ft")
                    miles < 10 -> Measure(String.format(locale, "%.1f", miles), "mi")
                    else -> Measure(miles.roundToInt().toString(), "mi")
                }
            }
        }
    }

    fun speed(mps: Double, units: Units): Measure = when (units) {
        Units.METRIC -> Measure((mps * 3.6).roundToInt().toString(), "km/h")
        Units.IMPERIAL -> Measure((mps * 2.236936).roundToInt().toString(), "mph")
    }

    /** "45 s", "4 min", "1 h 05". */
    fun duration(seconds: Double): String {
        val s = seconds.coerceAtLeast(0.0).roundToInt()
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${(s + 30) / 60} min"
            else -> "${s / 3600} h ${String.format(Locale.ROOT, "%02d", (s % 3600) / 60)}"
        }
    }
}

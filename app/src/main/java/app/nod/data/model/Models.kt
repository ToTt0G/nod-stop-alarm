package app.nod.data.model

import kotlinx.serialization.Serializable

/** What kind of stop the destination is. Drives the icon and a sensible default zone size. */
@Serializable
enum class PlaceKind(val defaultRadiusM: Int) {
    TRAIN(2000),
    METRO(600),
    TRAM(450),
    BUS(450),
    FERRY(900),
    CITY(5000),
    PLACE(500),
}

/** How loudly Nod should wake you. */
@Serializable
enum class AlertStyle { ALARM, VIBRATE, SILENT }

/** When to fire: on entering the zone, or N minutes before arriving (based on live speed). */
@Serializable
enum class TriggerMode { DISTANCE, TIME }

@Serializable
enum class VibrationPattern(val timings: LongArray, val amplitudes: IntArray) {
    PULSE(longArrayOf(0, 400, 400), intArrayOf(0, 255, 0)),
    HEARTBEAT(longArrayOf(0, 120, 100, 160, 700), intArrayOf(0, 255, 0, 200, 0)),
    WAVE(longArrayOf(0, 150, 50, 150, 50, 150, 50, 400, 500), intArrayOf(0, 60, 0, 120, 0, 180, 0, 255, 0)),
    URGENT(longArrayOf(0, 90, 60, 90, 60, 90, 300), intArrayOf(0, 255, 0, 255, 0, 255, 0)),
}

enum class Units { METRIC, IMPERIAL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Destination(
    val name: String,
    val subtitle: String = "",
    val lat: Double,
    val lon: Double,
    val kind: PlaceKind = PlaceKind.PLACE,
    val radiusM: Int = kind.defaultRadiusM,
)

/** Everything the trip service needs to watch a journey. Persisted so a trip survives process death. */
@Serializable
data class TripPlan(
    val destination: Destination,
    val triggerMode: TriggerMode = TriggerMode.DISTANCE,
    val minutesBefore: Int = 3,
    val alertStyle: AlertStyle = AlertStyle.ALARM,
    val headsUp: Boolean = true,
    val savedPlaceId: Long? = null,
    val startedAt: Long = 0L,
    /** Distance to the zone edge at the first fix; keeps progress stable across restarts. */
    val startToZoneM: Double? = null,
)

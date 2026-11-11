package app.nod.data.search

import app.nod.core.Geo
import app.nod.data.model.Destination
import app.nod.data.model.PlaceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Category filter for search; each maps onto OpenStreetMap tags understood by Photon. */
enum class SearchFilter(val osmTags: List<String>) {
    ALL(emptyList()),
    TRAIN(listOf("railway:station", "railway:halt", "public_transport:station")),
    METRO(listOf("railway:subway_entrance", "railway:station", "station:subway")),
    TRAM(listOf("railway:tram_stop")),
    BUS(listOf("highway:bus_stop", "amenity:bus_station")),
    FERRY(listOf("amenity:ferry_terminal")),
    CITY(listOf("place:city", "place:town", "place:village", "place:suburb", "place:neighbourhood")),
}

data class PlaceResult(
    val destination: Destination,
    val distanceFromMeM: Double?,
)

@Serializable
private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
private data class PhotonFeature(val properties: PhotonProps, val geometry: PhotonGeometry)

@Serializable
private data class PhotonGeometry(val coordinates: List<Double>)

@Serializable
private data class PhotonProps(
    val name: String? = null,
    val osm_key: String? = null,
    val osm_value: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val district: String? = null,
    val locality: String? = null,
    val state: String? = null,
    val country: String? = null,
    val extent: List<Double>? = null,
)

/** Free, key-less geocoding via Photon (komoot), built on OpenStreetMap. */
@Singleton
class SearchRepository @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val base = "https://photon.komoot.io"
    private val photonLangs = setOf("en", "de", "fr", "it")

    suspend fun search(query: String, filter: SearchFilter, nearLat: Double?, nearLon: Double?): List<PlaceResult> {
        val url = "$base/api/".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("limit", "20")
            lang()?.let { addQueryParameter("lang", it) }
            if (nearLat != null && nearLon != null) {
                // ~1 km is plenty to rank nearby results; coarser also means more cache hits
                // and less precise location leaving the device.
                addQueryParameter("lat", "%.2f".format(Locale.ROOT, nearLat))
                addQueryParameter("lon", "%.2f".format(Locale.ROOT, nearLon))
            }
            filter.osmTags.forEach { addQueryParameter("osm_tag", it) }
        }.build()
        val results = fetch(url.toString()).features.mapNotNull { it.toResult(nearLat, nearLon) }
        return results.distinctBy { Triple(it.destination.name, it.destination.subtitle, (it.destination.lat * 500).roundToInt()) }
    }

    suspend fun reverse(lat: Double, lon: Double): Destination? {
        val url = "$base/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("limit", "1")
            .apply { lang()?.let { addQueryParameter("lang", it) } }
            .build()
        val f = fetch(url.toString()).features.firstOrNull() ?: return null
        val r = f.toResult(null, null) ?: return null
        // Keep the dropped-pin location; only borrow the name.
        return r.destination.copy(lat = lat, lon = lon)
    }

    private fun lang(): String? = Locale.getDefault().language.takeIf { it in photonLangs }

    /** Recent responses, so typing back and forth ("Slus" → "Sluss" → "Slus") costs no network. */
    private val recent = object : LinkedHashMap<String, Pair<Long, PhotonResponse>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, PhotonResponse>>?) = size > 32
    }

    private suspend fun fetch(url: String): PhotonResponse {
        synchronized(recent) {
            recent[url]?.let { (at, r) -> if (System.currentTimeMillis() - at < 10 * 60_000) return r }
        }
        return download(url).also { synchronized(recent) { recent[url] = System.currentTimeMillis() to it } }
    }

    private suspend fun download(url: String): PhotonResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "Nod-Android/1.0").build()
        val body = http.newCall(request).await().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        json.decodeFromString(PhotonResponse.serializer(), body)
    }

    private fun PhotonFeature.toResult(nearLat: Double?, nearLon: Double?): PlaceResult? {
        val c = geometry.coordinates
        if (c.size < 2) return null
        val lon = c[0]
        val lat = c[1]
        val p = properties
        val kind = classify(p.osm_key, p.osm_value, p.name)
        val name = p.name ?: listOfNotNull(p.street, p.housenumber).joinToString(" ").ifBlank { null } ?: return null
        val subtitle = listOfNotNull(
            p.city?.takeIf { it != name } ?: p.locality ?: p.district,
            p.country,
        ).distinct().joinToString(" · ")
        val radius = if (kind == PlaceKind.CITY && p.extent != null && p.extent.size == 4) {
            // extent = [minLon, maxLat, maxLon, minLat]; use ~half the smaller side.
            val w = Geo.distance(lat, p.extent[0], lat, p.extent[2])
            val h = Geo.distance(p.extent[1], lon, p.extent[3], lon)
            app.nod.core.RadiusScale.snap((minOf(w, h) / 2).coerceIn(800.0, 15_000.0))
        } else kind.defaultRadiusM
        val dist = if (nearLat != null && nearLon != null) Geo.distance(nearLat, nearLon, lat, lon) else null
        return PlaceResult(Destination(name, subtitle, lat, lon, kind, radius), dist)
    }

    private fun classify(key: String?, value: String?, name: String?): PlaceKind {
        val n = name.orEmpty().lowercase()
        return when {
            key == "railway" && value == "tram_stop" -> PlaceKind.TRAM
            key == "railway" && value == "subway_entrance" -> PlaceKind.METRO
            key == "station" && value == "subway" -> PlaceKind.METRO
            key == "railway" && (value == "station" || value == "halt") ->
                if ("metro" in n || "subway" in n || "u-bahn" in n) PlaceKind.METRO else PlaceKind.TRAIN
            key == "public_transport" && value == "station" -> PlaceKind.TRAIN
            key == "highway" && value == "bus_stop" -> PlaceKind.BUS
            key == "amenity" && value == "bus_station" -> PlaceKind.BUS
            key == "amenity" && value == "ferry_terminal" -> PlaceKind.FERRY
            key == "place" && value in setOf("city", "town", "village", "suburb", "neighbourhood", "hamlet", "municipality") -> PlaceKind.CITY
            key == "boundary" -> PlaceKind.CITY
            else -> PlaceKind.PLACE
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

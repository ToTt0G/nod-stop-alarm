package app.nod.data.places

import app.nod.data.db.PlaceDao
import app.nod.data.db.PlaceEntity
import app.nod.data.db.TripDao
import app.nod.data.db.TripEntity
import app.nod.data.model.Destination
import app.nod.data.model.TripPlan
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesRepository @Inject constructor(
    private val places: PlaceDao,
    private val trips: TripDao,
) {
    val saved: Flow<List<PlaceEntity>> = places.observeAll()
    fun recentTrips(limit: Int = 50): Flow<List<TripEntity>> = trips.observeRecent(limit)
    val arrivedCount: Flow<Int> = trips.observeArrivedCount()
    val firstTripAt: Flow<Long?> = trips.observeFirstTripAt()

    suspend fun get(id: Long) = places.get(id)
    suspend fun findSaved(lat: Double, lon: Double) = places.findNear(lat, lon)

    suspend fun save(plan: TripPlan, existingId: Long? = null): Long {
        val d = plan.destination
        val entity = PlaceEntity(
            id = existingId ?: 0,
            name = d.name,
            subtitle = d.subtitle,
            lat = d.lat,
            lon = d.lon,
            kind = d.kind,
            radiusM = d.radiusM,
            triggerMode = plan.triggerMode,
            minutesBefore = plan.minutesBefore,
            alertStyle = plan.alertStyle,
        )
        val id = places.upsert(entity)
        return if (id == -1L) existingId ?: 0 else id
    }

    suspend fun delete(id: Long) = places.delete(id)
    suspend fun markUsed(id: Long) = places.markUsed(id, System.currentTimeMillis())

    suspend fun record(trip: TripEntity) = trips.insert(trip)
    suspend fun deleteTrip(id: Long) = trips.delete(id)
    suspend fun clearHistory() = trips.clear()
}

fun PlaceEntity.toDestination() = Destination(name, subtitle, lat, lon, kind, radiusM)
fun TripEntity.toDestination() = Destination(name, subtitle, lat, lon, kind, radiusM)

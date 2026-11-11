package app.nod.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import app.nod.data.model.AlertStyle
import app.nod.data.model.PlaceKind
import app.nod.data.model.TriggerMode
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val kind: PlaceKind,
    val radiusM: Int,
    val triggerMode: TriggerMode,
    val minutesBefore: Int,
    val alertStyle: AlertStyle,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0,
    val useCount: Int = 0,
)

enum class TripOutcome { ARRIVED, ENDED }

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val kind: PlaceKind,
    val radiusM: Int,
    val startedAt: Long,
    val endedAt: Long,
    val outcome: TripOutcome,
    val distanceM: Double?,
)

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY useCount DESC, lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun get(id: Long): PlaceEntity?

    @Query("SELECT * FROM places WHERE ABS(lat - :lat) < 0.0005 AND ABS(lon - :lon) < 0.0005 LIMIT 1")
    suspend fun findNear(lat: Double, lon: Double): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE places SET lastUsedAt = :at, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TripEntity>>

    /** Trips that ended with the user woken at their stop (outcome is stored by enum name). */
    @Query("SELECT COUNT(*) FROM trips WHERE outcome = 'ARRIVED'")
    fun observeArrivedCount(): Flow<Int>

    /** Start time of the oldest recorded trip, or null with no history. */
    @Query("SELECT MIN(startedAt) FROM trips")
    fun observeFirstTripAt(): Flow<Long?>

    @Upsert
    suspend fun insert(trip: TripEntity): Long

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trips")
    suspend fun clear()
}

class Converters {
    @TypeConverter fun kind(v: PlaceKind): String = v.name
    @TypeConverter fun kind(v: String): PlaceKind = runCatching { PlaceKind.valueOf(v) }.getOrDefault(PlaceKind.PLACE)
    @TypeConverter fun trigger(v: TriggerMode): String = v.name
    @TypeConverter fun trigger(v: String): TriggerMode = runCatching { TriggerMode.valueOf(v) }.getOrDefault(TriggerMode.DISTANCE)
    @TypeConverter fun style(v: AlertStyle): String = v.name
    @TypeConverter fun style(v: String): AlertStyle = runCatching { AlertStyle.valueOf(v) }.getOrDefault(AlertStyle.ALARM)
    @TypeConverter fun outcome(v: TripOutcome): String = v.name
    @TypeConverter fun outcome(v: String): TripOutcome = runCatching { TripOutcome.valueOf(v) }.getOrDefault(TripOutcome.ENDED)
}

@Database(entities = [PlaceEntity::class, TripEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class NodDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun trips(): TripDao
}

package app.nod.di

import android.content.Context
import androidx.room.Room
import app.nod.data.db.NodDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Provides @Singleton
    fun http(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .build()

    @Provides @Singleton
    fun db(@ApplicationContext context: Context): NodDatabase =
        Room.databaseBuilder(context, NodDatabase::class.java, "nod.db").build()

    @Provides fun placeDao(db: NodDatabase) = db.places()
    @Provides fun tripDao(db: NodDatabase) = db.trips()
}

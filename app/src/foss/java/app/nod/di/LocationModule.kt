package app.nod.di

import app.nod.data.location.LocationSource
import app.nod.data.location.PlatformLocationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** `foss` flavor: fixes come from the platform LocationManager, no Google Play Services. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun locationSource(impl: PlatformLocationSource): LocationSource
}

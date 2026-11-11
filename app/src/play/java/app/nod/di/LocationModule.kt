package app.nod.di

import app.nod.data.location.FusedLocationSource
import app.nod.data.location.LocationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** `play` flavor: fixes come from Google Play Services. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    abstract fun locationSource(impl: FusedLocationSource): LocationSource
}

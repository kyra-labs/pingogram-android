package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStorePowerSavingRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingRepository

/** The power saving settings (Settings → Power saving), in the app's settings file of [DataStoreModule]. */
@Module
@InstallIn(SingletonComponent::class)
interface PowerSavingModule {
    @Binds
    fun powerSavingRepository(repository: DataStorePowerSavingRepository): PowerSavingRepository
}

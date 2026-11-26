package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreCleanupSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettingsRepository

/** Cleanup suggestions' settings, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface CleanupModule {
    @Binds
    fun cleanupSettingsRepository(repository: DataStoreCleanupSettingsRepository): CleanupSettingsRepository
}

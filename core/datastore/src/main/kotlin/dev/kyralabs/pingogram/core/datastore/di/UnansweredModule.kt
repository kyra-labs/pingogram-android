package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreUnansweredSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettingsRepository

/** Unanswered's settings, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface UnansweredModule {
    @Binds
    fun unansweredSettingsRepository(repository: DataStoreUnansweredSettingsRepository): UnansweredSettingsRepository
}

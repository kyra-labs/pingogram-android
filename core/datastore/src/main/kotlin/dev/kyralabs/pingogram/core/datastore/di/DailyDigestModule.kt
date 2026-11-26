package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreDailyDigestSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository

/** The Daily digest's settings, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface DailyDigestModule {
    @Binds
    fun dailyDigestSettingsRepository(repository: DataStoreDailyDigestSettingsRepository): DailyDigestSettingsRepository
}

package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreListenSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ListenSettingsRepository

/** Listen to messages' switch, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface ListenSettingsModule {
    @Binds
    fun listenSettingsRepository(repository: DataStoreListenSettingsRepository): ListenSettingsRepository
}

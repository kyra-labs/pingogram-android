package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreTypingStatusRepository
import dev.kyralabs.pingogram.core.domain.repository.TypingStatusRepository

/** Whether conversations tell the chat what the user is doing ("Send typing status"), in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface TypingStatusModule {
    @Binds
    fun typingStatusRepository(repository: DataStoreTypingStatusRepository): TypingStatusRepository
}

package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreCompactChatListRepository
import dev.kyralabs.pingogram.core.domain.repository.CompactChatListRepository

/** Whether the chat list shows compact rows, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface CompactChatListModule {
    @Binds
    fun compactChatListRepository(repository: DataStoreCompactChatListRepository): CompactChatListRepository
}

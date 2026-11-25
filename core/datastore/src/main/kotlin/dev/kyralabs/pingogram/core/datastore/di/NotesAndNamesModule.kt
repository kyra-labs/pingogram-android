package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreNotesAndNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository

/** Whether the user's notes and names for chats show, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface NotesAndNamesModule {
    @Binds
    fun notesAndNamesRepository(repository: DataStoreNotesAndNamesRepository): NotesAndNamesRepository
}

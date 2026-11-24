package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreBirthdaysRowRepository
import dev.kyralabs.pingogram.core.domain.repository.BirthdaysRowRepository

/** Whether the Contacts tab shows its "Birthdays" row, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface BirthdaysModule {
    @Binds
    fun birthdaysRowRepository(repository: DataStoreBirthdaysRowRepository): BirthdaysRowRepository
}

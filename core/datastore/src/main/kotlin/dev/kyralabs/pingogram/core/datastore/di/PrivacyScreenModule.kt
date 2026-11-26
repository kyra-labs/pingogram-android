package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStorePrivacyScreenRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository

/** The privacy screen's switch, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface PrivacyScreenModule {
    @Binds
    fun privacyScreenRepository(repository: DataStorePrivacyScreenRepository): PrivacyScreenRepository
}

package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun authRepository(repository: TdlibAuthRepository): AuthRepository

    @Binds
    fun connectionRepository(repository: TdlibConnectionRepository): ConnectionRepository

    @Binds
    fun countryRepository(repository: TdlibCountryRepository): CountryRepository
}

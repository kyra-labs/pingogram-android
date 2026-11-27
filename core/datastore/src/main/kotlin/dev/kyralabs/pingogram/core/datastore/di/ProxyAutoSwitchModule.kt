package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreProxyAutoSwitchRepository
import dev.kyralabs.pingogram.core.domain.repository.ProxyAutoSwitchRepository

/** The switch to a working proxy, in the settings file. */
@Module
@InstallIn(SingletonComponent::class)
interface ProxyAutoSwitchModule {
    @Binds
    fun proxyAutoSwitchRepository(repository: DataStoreProxyAutoSwitchRepository): ProxyAutoSwitchRepository
}

package dev.kyralabs.pingogram.feature.calls.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.feature.calls.CallNetwork
import dev.kyralabs.pingogram.feature.calls.DataSavingNetworkRepository

/** The network the call managers follow: the device's, with "Use less data for calls" applied. */
@Module
@InstallIn(SingletonComponent::class)
internal interface CallNetworkModule {
    @Binds
    @CallNetwork
    fun callNetwork(network: DataSavingNetworkRepository): NetworkRepository
}

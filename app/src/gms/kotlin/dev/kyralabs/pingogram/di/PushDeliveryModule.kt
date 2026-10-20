package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.background.BackgroundConnection
import dev.kyralabs.pingogram.background.NoBackgroundConnection
import dev.kyralabs.pingogram.core.domain.model.PushDelivery

/**
 * How new messages reach the `gms` flavor in the background: Firebase Cloud Messaging (later, `REQUIREMENTS.md`
 * FR-N3), so Settings → Notifications offers no "Keep connected".
 */
@Module
@InstallIn(SingletonComponent::class)
interface PushDeliveryModule {
    @Binds
    fun backgroundConnection(connection: NoBackgroundConnection): BackgroundConnection

    companion object {
        @Provides
        fun pushDelivery(): PushDelivery = PushDelivery.Firebase
    }
}

package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.background.BackgroundConnection
import dev.kyralabs.pingogram.background.KeepConnectedController
import dev.kyralabs.pingogram.core.domain.model.PushDelivery

/**
 * How new messages reach the `foss` flavor in the background: TDLib's own connection, which "Keep connected"
 * ([KeepConnectedController]) may keep open (`docs/adr/0003-keep-connected-foreground-service.md`).
 */
@Module
@InstallIn(SingletonComponent::class)
interface PushDeliveryModule {
    @Binds
    fun backgroundConnection(controller: KeepConnectedController): BackgroundConnection

    companion object {
        @Provides
        fun pushDelivery(): PushDelivery = PushDelivery.TdlibConnection
    }
}

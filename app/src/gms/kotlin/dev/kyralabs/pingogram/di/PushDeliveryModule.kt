package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.background.BackgroundConnection
import dev.kyralabs.pingogram.background.FirebasePushConnection
import dev.kyralabs.pingogram.core.domain.model.PushDelivery
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import dev.kyralabs.pingogram.push.FirebasePushTokenSource

/**
 * How new messages reach the `gms` flavor in the background: Firebase Cloud Messaging
 * (`docs/adr/0004-push-delivery.md`), so Settings → Notifications offers no "Keep connected". The push service is FCM
 * ([FirebasePushTokenSource]); `foss` binds none, so core:data's `Optional<PushTokenSource>` stays empty there.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PushDeliveryModule {
    @Binds
    fun backgroundConnection(connection: FirebasePushConnection): BackgroundConnection

    @Binds
    fun pushTokenSource(source: FirebasePushTokenSource): PushTokenSource

    companion object {
        @Provides
        fun pushDelivery(): PushDelivery = PushDelivery.Firebase
    }
}

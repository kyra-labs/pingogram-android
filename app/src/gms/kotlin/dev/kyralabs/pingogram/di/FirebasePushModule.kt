package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import dev.kyralabs.pingogram.push.FirebasePushTokenSource

/**
 * The `gms` flavor's push service, Firebase Cloud Messaging (`docs/adr/0004-push-delivery.md`). `foss` has no such
 * module, so core:data's `Optional<PushTokenSource>` stays empty there.
 */
@Module
@InstallIn(SingletonComponent::class)
interface FirebasePushModule {
    @Binds
    fun pushTokenSource(source: FirebasePushTokenSource): PushTokenSource
}

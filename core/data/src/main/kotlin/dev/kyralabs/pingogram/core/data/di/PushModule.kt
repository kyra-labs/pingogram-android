package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.repository.TdlibPushRepository
import dev.kyralabs.pingogram.core.domain.repository.PushRepository
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource

/**
 * Push notifications (`docs/adr/0004-push-delivery.md`). The push service is optional: `:app`'s `gms` flavor binds a
 * [PushTokenSource] (Firebase Cloud Messaging), `foss` binds none, and [TdlibPushRepository] then does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
interface PushModule {
    @BindsOptionalOf
    fun pushTokenSource(): PushTokenSource
}

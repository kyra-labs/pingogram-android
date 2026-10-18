package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.kyralabs.pingogram.core.data.repository.TdlibChatActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository

/**
 * The set of every [TdlibUpdateCache], which `PingogramApplication` injects so each cache exists, and is subscribed to
 * TDLib's updates, before TDLib starts. Every new caching repository joins it here.
 */
@Module
@InstallIn(SingletonComponent::class)
interface UpdateCacheModule {
    @Binds
    @IntoSet
    fun userCache(repository: TdlibUserRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun fileCache(repository: TdlibFileRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun chatCache(repository: TdlibChatRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun messageCache(repository: TdlibMessageRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun chatActionCache(repository: TdlibChatActionRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun groupCache(repository: TdlibGroupRepository): TdlibUpdateCache
}

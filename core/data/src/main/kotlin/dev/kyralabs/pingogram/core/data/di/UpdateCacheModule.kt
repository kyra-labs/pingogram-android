package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.kyralabs.pingogram.core.data.attachment.UploadCleaner
import dev.kyralabs.pingogram.core.data.repository.TdlibChatActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSecretChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository

/**
 * The set of every [TdlibUpdateCache], which `PingogramApplication` injects so each cache exists, and is subscribed to
 * TDLib's updates, before TDLib starts. Every new caching repository joins it here.
 */
@Suppress("TooManyFunctions") // One binding per cache: every caching repository must join the set here.
@Module
@InstallIn(SingletonComponent::class)
interface UpdateCacheModule {
    @Binds
    @IntoSet
    fun userCache(repository: TdlibUserRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun fileCache(repository: TdlibFileRepository): TdlibUpdateCache

    /** Follows the messages being sent from their first update on. */
    @Binds
    @IntoSet
    fun transferCache(repository: TdlibTransferRepository): TdlibUpdateCache

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

    /** TDLib announces each secret chat once, before its chat, and again only when it changes. */
    @Binds
    @IntoSet
    fun secretChatCache(repository: TdlibSecretChatRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun profileCache(repository: TdlibProfileRepository): TdlibUpdateCache

    /** TDLib sends the notifications that stay active from before the start only once, right after it starts. */
    @Binds
    @IntoSet
    fun notificationCache(repository: TdlibNotificationRepository): TdlibUpdateCache

    /** Not a repository, but it follows the messages being sent from their first update on, to delete their copies. */
    @Binds
    @IntoSet
    fun uploadCleaner(cleaner: UploadCleaner): TdlibUpdateCache
}

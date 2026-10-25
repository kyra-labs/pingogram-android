package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.migration.DisableInstallInCheck
import dagger.multibindings.IntoSet
import dev.kyralabs.pingogram.core.data.attachment.UploadCleaner
import dev.kyralabs.pingogram.core.data.repository.TdlibCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatSettingsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConferenceCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibContactSyncRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibQuickReactionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibReactionNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSecretChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStoryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository

/**
 * The set of every [TdlibUpdateCache] of an account ([AccountComponent.updateCaches]), which `PingogramApplication`
 * makes so each cache exists, and is subscribed to TDLib's updates, before TDLib starts. Every new caching repository
 * joins it here.
 *
 * One binding per caching repository, so it has as many functions as there are caches (`TooManyFunctions`): splitting
 * it would scatter the one list of caches.
 */
@Suppress("TooManyFunctions")
@Module
@DisableInstallInCheck
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

    /** TDLib announces each chat's active stories only with `updateChatActiveStories`. */
    @Binds
    @IntoSet
    fun storyCache(repository: TdlibStoryRepository): TdlibUpdateCache

    /** TDLib announces each call, an incoming one too, only with `updateCall`. */
    @Binds
    @IntoSet
    fun callCache(repository: TdlibCallRepository): TdlibUpdateCache

    /** TDLib announces a chat's voice chat with the chat, and group calls and their participants only by updates. */
    @Binds
    @IntoSet
    fun groupCallCache(repository: TdlibGroupCallRepository): TdlibUpdateCache

    /** TDLib announces a conference call's verification and its blockchain's members only by updates. */
    @Binds
    @IntoSet
    fun conferenceCallCache(repository: TdlibConferenceCallRepository): TdlibUpdateCache

    @Binds
    @IntoSet
    fun profileCache(repository: TdlibProfileRepository): TdlibUpdateCache

    /** TDLib sends the notifications that stay active from before the start only once, right after it starts. */
    @Binds
    @IntoSet
    fun notificationCache(repository: TdlibNotificationRepository): TdlibUpdateCache

    /** Not a cache, but it clears contacts sync's settings at logout, which it sees only from the updates. */
    @Binds
    @IntoSet
    fun contactSyncCache(repository: TdlibContactSyncRepository): TdlibUpdateCache

    /** For the emoji Telegram offers as reactions, which come once at TDLib's start. */
    @Binds
    @IntoSet
    fun chatSettingsCache(repository: TdlibChatSettingsRepository): TdlibUpdateCache

    /** For the reaction notification settings, which TDLib announces once per start and cannot be asked for. */
    @Binds
    @IntoSet
    fun reactionNotificationCache(repository: TdlibReactionNotificationRepository): TdlibUpdateCache

    /** For the quick reaction, which TDLib announces once per start and cannot be asked for. */
    @Binds
    @IntoSet
    fun quickReactionCache(repository: TdlibQuickReactionRepository): TdlibUpdateCache

    /** Not a repository, but it follows the messages being sent from their first update on, to delete their copies. */
    @Binds
    @IntoSet
    fun uploadCleaner(cleaner: UploadCleaner): TdlibUpdateCache
}

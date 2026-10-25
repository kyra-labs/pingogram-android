package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreArchiveRowRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreChatListSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreComposerSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreDoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenPinsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLockedChatIdsRepository
import dev.kyralabs.pingogram.core.datastore.DataStorePhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreRecentEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository

/**
 * The repositories of the settings file ([DataStoreModule]) that conversations and the chat list read: the composer's,
 * the bars' and the link previews', whether phone numbers show on screen, the chat list's own settings, the archive
 * row's, and the locked chats.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ChatPreferencesModule {
    @Binds
    fun composerSettingsRepository(repository: DataStoreComposerSettingsRepository): ComposerSettingsRepository

    @Binds
    fun doubleTapSettingsRepository(repository: DataStoreDoubleTapSettingsRepository): DoubleTapSettingsRepository

    @Binds
    fun recentEmojiRepository(repository: DataStoreRecentEmojiRepository): RecentEmojiRepository

    @Binds
    fun hiddenPinsRepository(repository: DataStoreHiddenPinsRepository): HiddenPinsRepository

    @Binds
    fun archiveRowRepository(repository: DataStoreArchiveRowRepository): ArchiveRowRepository

    @Binds
    fun hiddenTranslateBarsRepository(repository: DataStoreHiddenTranslateBarsRepository): HiddenTranslateBarsRepository

    @Binds
    fun linkPreviewSettingsRepository(repository: DataStoreLinkPreviewSettingsRepository): LinkPreviewSettingsRepository

    @Binds
    fun phoneNumberVisibilityRepository(
        repository: DataStorePhoneNumberVisibilityRepository,
    ): PhoneNumberVisibilityRepository

    @Binds
    fun chatListSettingsRepository(repository: DataStoreChatListSettingsRepository): ChatListSettingsRepository

    @Binds
    fun lockedChatIdsRepository(repository: DataStoreLockedChatIdsRepository): LockedChatIdsRepository
}

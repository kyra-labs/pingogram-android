package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreIdVisibilityRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLockedChatIdsRepository
import dev.kyralabs.pingogram.core.datastore.DataStorePhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.IdVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository

/**
 * The repositories of the settings file ([DataStoreModule]) that keep what the screen and Telegram see of the user's
 * chats: the locked chats, link previews in secret chats, and whether phone numbers and IDs show on screen.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ChatPrivacyModule {
    @Binds
    fun lockedChatIdsRepository(repository: DataStoreLockedChatIdsRepository): LockedChatIdsRepository

    @Binds
    fun linkPreviewSettingsRepository(repository: DataStoreLinkPreviewSettingsRepository): LinkPreviewSettingsRepository

    @Binds
    fun phoneNumberVisibilityRepository(
        repository: DataStorePhoneNumberVisibilityRepository,
    ): PhoneNumberVisibilityRepository

    @Binds
    fun idVisibilityRepository(repository: DataStoreIdVisibilityRepository): IdVisibilityRepository
}

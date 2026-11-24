package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreAiEditorConsentRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreCleanLinksRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreDeclinedBirthdaysRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreDirectShareRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreIdVisibilityRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreIncognitoKeyboardRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLockedChatIdsRepository
import dev.kyralabs.pingogram.core.datastore.DataStorePhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreSharePhoneNumberRepository
import dev.kyralabs.pingogram.core.domain.repository.AiEditorConsentRepository
import dev.kyralabs.pingogram.core.domain.repository.CleanLinksRepository
import dev.kyralabs.pingogram.core.domain.repository.DeclinedBirthdaysRepository
import dev.kyralabs.pingogram.core.domain.repository.DirectShareRepository
import dev.kyralabs.pingogram.core.domain.repository.IdVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.IncognitoKeyboardRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.SharePhoneNumberRepository

/**
 * The repositories of the settings file ([DataStoreModule]) that keep what the screen and Telegram see of the user's
 * chats: the locked chats, link previews in secret chats, whether phone numbers and IDs show on screen, whether
 * Android's share sheet offers the chats, and the birthday suggestions the user declined.
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

    @Binds
    fun incognitoKeyboardRepository(repository: DataStoreIncognitoKeyboardRepository): IncognitoKeyboardRepository

    @Binds
    fun cleanLinksRepository(repository: DataStoreCleanLinksRepository): CleanLinksRepository

    @Binds
    fun sharePhoneNumberRepository(repository: DataStoreSharePhoneNumberRepository): SharePhoneNumberRepository

    @Binds
    fun directShareRepository(repository: DataStoreDirectShareRepository): DirectShareRepository

    @Binds
    fun declinedBirthdaysRepository(repository: DataStoreDeclinedBirthdaysRepository): DeclinedBirthdaysRepository

    @Binds
    fun aiEditorConsentRepository(repository: DataStoreAiEditorConsentRepository): AiEditorConsentRepository
}

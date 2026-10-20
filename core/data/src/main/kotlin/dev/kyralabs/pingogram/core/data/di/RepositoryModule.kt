package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.emoji.AndroidEmojiCatalogRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceLocationRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidSharedStorageRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAppLockRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAutoDownloadPolicy
import dev.kyralabs.pingogram.core.data.repository.TdlibAttachmentRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationSettingsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSendAsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSponsoredMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStorageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibVoiceNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceNoteRepository

/**
 * Binds the repositories to their interfaces. Caching ones also join the set in [UpdateCacheModule]. One binding per
 * repository, hence `TooManyFunctions` is suppressed.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun authRepository(repository: TdlibAuthRepository): AuthRepository

    @Binds
    fun appLockRepository(repository: DefaultAppLockRepository): AppLockRepository

    @Binds
    fun connectionRepository(repository: TdlibConnectionRepository): ConnectionRepository

    @Binds
    fun countryRepository(repository: TdlibCountryRepository): CountryRepository

    @Binds
    fun userRepository(repository: TdlibUserRepository): UserRepository

    @Binds
    fun fileRepository(repository: TdlibFileRepository): FileRepository

    @Binds
    fun chatRepository(repository: TdlibChatRepository): ChatRepository

    @Binds
    fun messageRepository(repository: TdlibMessageRepository): MessageRepository

    @Binds
    fun messageActionRepository(repository: TdlibMessageActionRepository): MessageActionRepository

    @Binds
    fun sponsoredMessageRepository(repository: TdlibSponsoredMessageRepository): SponsoredMessageRepository

    @Binds
    fun attachmentRepository(repository: TdlibAttachmentRepository): AttachmentRepository

    @Binds
    fun voiceNoteRepository(repository: TdlibVoiceNoteRepository): VoiceNoteRepository

    @Binds
    fun chatActionRepository(repository: TdlibChatActionRepository): ChatActionRepository

    @Binds
    fun chatMediaRepository(repository: TdlibChatMediaRepository): ChatMediaRepository

    @Binds
    fun groupRepository(repository: TdlibGroupRepository): GroupRepository

    @Binds
    fun searchRepository(repository: TdlibSearchRepository): SearchRepository

    @Binds
    fun storageRepository(repository: TdlibStorageRepository): StorageRepository

    @Binds
    fun privacyRepository(repository: TdlibPrivacyRepository): PrivacyRepository

    @Binds
    fun blockListRepository(repository: TdlibBlockListRepository): BlockListRepository

    @Binds
    fun sessionRepository(repository: TdlibSessionRepository): SessionRepository

    @Binds
    fun twoStepVerificationRepository(repository: TdlibTwoStepVerificationRepository): TwoStepVerificationRepository

    @Binds
    fun sendAsRepository(repository: TdlibSendAsRepository): SendAsRepository

    @Binds
    fun profileRepository(repository: TdlibProfileRepository): ProfileRepository

    @Binds
    fun notificationRepository(repository: TdlibNotificationRepository): NotificationRepository

    @Binds
    fun notificationSettingsRepository(repository: TdlibNotificationSettingsRepository): NotificationSettingsRepository

    @Binds
    fun sharedStorageRepository(repository: AndroidSharedStorageRepository): SharedStorageRepository

    @Binds
    fun deviceLocationRepository(repository: AndroidDeviceLocationRepository): DeviceLocationRepository

    @Binds
    fun stickerRepository(repository: TdlibStickerRepository): StickerRepository

    @Binds
    fun emojiCatalogRepository(repository: AndroidEmojiCatalogRepository): EmojiCatalogRepository

    @Binds
    fun networkRepository(repository: AndroidNetworkRepository): NetworkRepository

    @Binds
    fun autoDownloadPolicy(policy: DefaultAutoDownloadPolicy): AutoDownloadPolicy
}

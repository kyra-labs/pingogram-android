package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.emoji.AndroidEmojiCatalogRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidBatterySaverRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceContactsRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceLanguageDetector
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceLocationRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidPickedPhotoRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidSharedStorageRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAppLockRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAutoDownloadPolicy
import dev.kyralabs.pingogram.core.data.repository.DefaultChatLockRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultPowerSavingPolicy
import dev.kyralabs.pingogram.core.data.repository.TdlibAccountRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibArchiveSettingsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibAttachmentRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibBotButtonRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibBotInfoRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCallHistoryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatBoostRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatCreationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatEventLogRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatFolderRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatJoinRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatSettingsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatStatisticsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConferenceCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectedWebsiteRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibContactJoinedRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibContactRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibContactSyncRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCustomEmojiRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileStreamRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibForumTopicRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupAdministrationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupCallRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupInfoRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibInlineBotRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibInviteLinkRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibJoinRequestRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibLinkRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibLoginEmailRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageActionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageInteractionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageThreadRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkUsageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibNotificationSettingsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPersonalChannelRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPinnedMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPollRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibProxyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibQuickReactionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibReactionNotificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSavedGifRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibScheduledMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSecretChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSendAsRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibShareRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSharedMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSponsoredMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStickerSetRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStorageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStoryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTransferRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTranslationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibVideoNoteRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibVoiceNoteRepository
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.CallHistoryRepository
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatBoostRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatEventLogRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatJoinRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatSearchRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.domain.repository.ConferenceCallRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectedWebsiteRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactJoinedRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceLanguageDetector
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.FileStreamRepository
import dev.kyralabs.pingogram.core.domain.repository.ForumTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupAdministrationRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.domain.repository.InviteLinkRepository
import dev.kyralabs.pingogram.core.domain.repository.JoinRequestRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkRepository
import dev.kyralabs.pingogram.core.domain.repository.LoginEmailRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageInteractionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageThreadRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkUsageRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PersonalChannelRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingPolicy
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import dev.kyralabs.pingogram.core.domain.repository.ReactionNotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedGifRepository
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.ShareRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.domain.repository.StorageRepository
import dev.kyralabs.pingogram.core.domain.repository.StoryRepository
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import dev.kyralabs.pingogram.core.domain.repository.TranslationRepository
import dev.kyralabs.pingogram.core.domain.repository.TwoStepVerificationRepository
import dev.kyralabs.pingogram.core.domain.repository.UserProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository
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
    fun chatLockRepository(repository: DefaultChatLockRepository): ChatLockRepository

    @Binds
    fun connectionRepository(repository: TdlibConnectionRepository): ConnectionRepository

    @Binds
    fun countryRepository(repository: TdlibCountryRepository): CountryRepository

    @Binds
    fun userRepository(repository: TdlibUserRepository): UserRepository

    @Binds
    fun fileRepository(repository: TdlibFileRepository): FileRepository

    @Binds
    fun fileStreamRepository(repository: TdlibFileStreamRepository): FileStreamRepository

    @Binds
    fun transferRepository(repository: TdlibTransferRepository): TransferRepository

    @Binds
    fun chatRepository(repository: TdlibChatRepository): ChatRepository

    @Binds
    fun messageRepository(repository: TdlibMessageRepository): MessageRepository

    @Binds
    fun messageActionRepository(repository: TdlibMessageActionRepository): MessageActionRepository

    @Binds
    fun sponsoredMessageRepository(repository: TdlibSponsoredMessageRepository): SponsoredMessageRepository

    @Binds
    fun pinnedMessageRepository(repository: TdlibPinnedMessageRepository): PinnedMessageRepository

    @Binds
    fun scheduledMessageRepository(repository: TdlibScheduledMessageRepository): ScheduledMessageRepository

    @Binds
    fun forumTopicRepository(repository: TdlibForumTopicRepository): ForumTopicRepository

    @Binds
    fun messageThreadRepository(repository: TdlibMessageThreadRepository): MessageThreadRepository

    @Binds
    fun messageInteractionRepository(repository: TdlibMessageInteractionRepository): MessageInteractionRepository

    @Binds
    fun attachmentRepository(repository: TdlibAttachmentRepository): AttachmentRepository

    @Binds
    fun voiceNoteRepository(repository: TdlibVoiceNoteRepository): VoiceNoteRepository

    @Binds
    fun videoNoteRepository(repository: TdlibVideoNoteRepository): VideoNoteRepository

    @Binds
    fun chatActionRepository(repository: TdlibChatActionRepository): ChatActionRepository

    @Binds
    fun chatMediaRepository(repository: TdlibChatMediaRepository): ChatMediaRepository

    @Binds
    fun groupRepository(repository: TdlibGroupRepository): GroupRepository

    @Binds
    fun searchRepository(repository: TdlibSearchRepository): SearchRepository

    @Binds
    fun contactRepository(repository: TdlibContactRepository): ContactRepository

    @Binds
    fun contactSyncRepository(repository: TdlibContactSyncRepository): ContactSyncRepository

    @Binds
    fun deviceContactsRepository(repository: AndroidDeviceContactsRepository): DeviceContactsRepository

    @Binds
    fun chatCreationRepository(repository: TdlibChatCreationRepository): ChatCreationRepository

    @Binds
    fun pickedPhotoRepository(repository: AndroidPickedPhotoRepository): PickedPhotoRepository

    @Binds
    fun storageRepository(repository: TdlibStorageRepository): StorageRepository

    @Binds
    fun privacyRepository(repository: TdlibPrivacyRepository): PrivacyRepository

    @Binds
    fun archiveSettingsRepository(repository: TdlibArchiveSettingsRepository): ArchiveSettingsRepository

    @Binds
    fun blockListRepository(repository: TdlibBlockListRepository): BlockListRepository

    @Binds
    fun userProfileRepository(repository: TdlibUserProfileRepository): UserProfileRepository

    @Binds
    fun groupInfoRepository(repository: TdlibGroupInfoRepository): GroupInfoRepository

    @Binds
    fun groupAdministrationRepository(repository: TdlibGroupAdministrationRepository): GroupAdministrationRepository

    @Binds
    fun inviteLinkRepository(repository: TdlibInviteLinkRepository): InviteLinkRepository

    @Binds
    fun chatJoinRepository(repository: TdlibChatJoinRepository): ChatJoinRepository

    @Binds
    fun chatBoostRepository(repository: TdlibChatBoostRepository): ChatBoostRepository

    @Binds
    fun sharedMediaRepository(repository: TdlibSharedMediaRepository): SharedMediaRepository

    @Binds
    fun sessionRepository(repository: TdlibSessionRepository): SessionRepository

    @Binds
    fun chatFolderRepository(repository: TdlibChatFolderRepository): ChatFolderRepository

    @Binds
    fun accountRepository(repository: TdlibAccountRepository): AccountRepository

    @Binds
    fun phoneNumberChangeRepository(repository: TdlibPhoneNumberChangeRepository): PhoneNumberChangeRepository

    @Binds
    fun loginEmailRepository(repository: TdlibLoginEmailRepository): LoginEmailRepository

    @Binds
    fun joinRequestRepository(repository: TdlibJoinRequestRepository): JoinRequestRepository

    @Binds
    fun chatEventLogRepository(repository: TdlibChatEventLogRepository): ChatEventLogRepository

    @Binds
    fun chatStatisticsRepository(repository: TdlibChatStatisticsRepository): ChatStatisticsRepository

    @Binds
    fun chatSettingsRepository(repository: TdlibChatSettingsRepository): ChatSettingsRepository

    @Binds
    fun reactionNotificationRepository(repository: TdlibReactionNotificationRepository): ReactionNotificationRepository

    @Binds
    fun quickReactionRepository(repository: TdlibQuickReactionRepository): QuickReactionRepository

    @Binds
    fun connectedWebsiteRepository(repository: TdlibConnectedWebsiteRepository): ConnectedWebsiteRepository

    @Binds
    fun contactJoinedRepository(repository: TdlibContactJoinedRepository): ContactJoinedRepository

    @Binds
    fun personalChannelRepository(repository: TdlibPersonalChannelRepository): PersonalChannelRepository

    @Binds
    fun proxyRepository(repository: TdlibProxyRepository): ProxyRepository

    @Binds
    fun networkUsageRepository(repository: TdlibNetworkUsageRepository): NetworkUsageRepository

    @Binds
    fun twoStepVerificationRepository(repository: TdlibTwoStepVerificationRepository): TwoStepVerificationRepository

    @Binds
    fun sendAsRepository(repository: TdlibSendAsRepository): SendAsRepository

    @Binds
    fun chatSearchRepository(repository: TdlibChatSearchRepository): ChatSearchRepository

    @Binds
    fun botButtonRepository(repository: TdlibBotButtonRepository): BotButtonRepository

    @Binds
    fun pollRepository(repository: TdlibPollRepository): PollRepository

    @Binds
    fun botInfoRepository(repository: TdlibBotInfoRepository): BotInfoRepository

    @Binds
    fun inlineBotRepository(repository: TdlibInlineBotRepository): InlineBotRepository

    @Binds
    fun secretChatRepository(repository: TdlibSecretChatRepository): SecretChatRepository

    @Binds
    fun storyRepository(repository: TdlibStoryRepository): StoryRepository

    @Binds
    fun callRepository(repository: TdlibCallRepository): CallRepository

    @Binds
    fun callHistoryRepository(repository: TdlibCallHistoryRepository): CallHistoryRepository

    @Binds
    fun groupCallRepository(repository: TdlibGroupCallRepository): GroupCallRepository

    @Binds
    fun conferenceCallRepository(repository: TdlibConferenceCallRepository): ConferenceCallRepository

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
    fun savedGifRepository(repository: TdlibSavedGifRepository): SavedGifRepository

    @Binds
    fun stickerSetRepository(repository: TdlibStickerSetRepository): StickerSetRepository

    @Binds
    fun emojiCatalogRepository(repository: AndroidEmojiCatalogRepository): EmojiCatalogRepository

    @Binds
    fun networkRepository(repository: AndroidNetworkRepository): NetworkRepository

    @Binds
    fun autoDownloadPolicy(policy: DefaultAutoDownloadPolicy): AutoDownloadPolicy

    @Binds
    fun batterySaverRepository(repository: AndroidBatterySaverRepository): BatterySaverRepository

    @Binds
    fun powerSavingPolicy(policy: DefaultPowerSavingPolicy): PowerSavingPolicy

    @Binds
    fun linkRepository(repository: TdlibLinkRepository): LinkRepository

    @Binds
    fun shareRepository(repository: TdlibShareRepository): ShareRepository

    @Binds
    fun translationRepository(repository: TdlibTranslationRepository): TranslationRepository

    @Binds
    fun deviceLanguageDetector(detector: AndroidDeviceLanguageDetector): DeviceLanguageDetector

    @Binds
    fun customEmojiRepository(repository: TdlibCustomEmojiRepository): CustomEmojiRepository
}

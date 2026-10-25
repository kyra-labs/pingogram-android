package dev.kyralabs.pingogram.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.AccountRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.BotButtonRepository
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.CallHistoryRepository
import dev.kyralabs.pingogram.core.domain.repository.CallRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatBoostRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatCreationRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatEventLogRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExporter
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
import dev.kyralabs.pingogram.core.domain.repository.NetworkUsageRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PersonalChannelRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.domain.repository.PinnedMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import dev.kyralabs.pingogram.core.domain.repository.PushRepository
import dev.kyralabs.pingogram.core.domain.repository.QuickReactionRepository
import dev.kyralabs.pingogram.core.domain.repository.ReactionNotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedGifRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTopicRepository
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.domain.repository.ShareRepository
import dev.kyralabs.pingogram.core.domain.repository.SharedMediaRepository
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
 * The active account's repositories (`docs/adr/0031-multiple-accounts.md`), for everything outside [AccountComponent]:
 * view models, workers, services and the app's own singletons. Unscoped: each lookup asks [Accounts] again, so a view
 * model made after a switch gets the new account's. One function per repository, hence `TooManyFunctions`.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object ActiveAccountModule {
    @Provides
    fun authRepository(accounts: Accounts): AuthRepository = accounts.active.authRepository()

    @Provides
    fun chatLockRepository(accounts: Accounts): ChatLockRepository = accounts.active.chatLockRepository()

    @Provides
    fun connectionRepository(accounts: Accounts): ConnectionRepository = accounts.active.connectionRepository()

    @Provides
    fun countryRepository(accounts: Accounts): CountryRepository = accounts.active.countryRepository()

    @Provides
    fun userRepository(accounts: Accounts): UserRepository = accounts.active.userRepository()

    @Provides
    fun fileRepository(accounts: Accounts): FileRepository = accounts.active.fileRepository()

    @Provides
    fun fileStreamRepository(accounts: Accounts): FileStreamRepository = accounts.active.fileStreamRepository()

    @Provides
    fun transferRepository(accounts: Accounts): TransferRepository = accounts.active.transferRepository()

    @Provides
    fun chatRepository(accounts: Accounts): ChatRepository = accounts.active.chatRepository()

    @Provides
    fun messageRepository(accounts: Accounts): MessageRepository = accounts.active.messageRepository()

    @Provides
    fun messageActionRepository(accounts: Accounts): MessageActionRepository = accounts.active.messageActionRepository()

    @Provides
    fun sponsoredMessageRepository(accounts: Accounts): SponsoredMessageRepository =
        accounts.active.sponsoredMessageRepository()

    @Provides
    fun pinnedMessageRepository(accounts: Accounts): PinnedMessageRepository = accounts.active.pinnedMessageRepository()

    @Provides
    fun scheduledMessageRepository(accounts: Accounts): ScheduledMessageRepository =
        accounts.active.scheduledMessageRepository()

    @Provides
    fun forumTopicRepository(accounts: Accounts): ForumTopicRepository = accounts.active.forumTopicRepository()

    @Provides
    fun savedMessagesTopicRepository(accounts: Accounts): SavedMessagesTopicRepository =
        accounts.active.savedMessagesTopicRepository()

    @Provides
    fun messageThreadRepository(accounts: Accounts): MessageThreadRepository = accounts.active.messageThreadRepository()

    @Provides
    fun messageInteractionRepository(accounts: Accounts): MessageInteractionRepository =
        accounts.active.messageInteractionRepository()

    @Provides
    fun attachmentRepository(accounts: Accounts): AttachmentRepository = accounts.active.attachmentRepository()

    @Provides
    fun voiceNoteRepository(accounts: Accounts): VoiceNoteRepository = accounts.active.voiceNoteRepository()

    @Provides
    fun videoNoteRepository(accounts: Accounts): VideoNoteRepository = accounts.active.videoNoteRepository()

    @Provides
    fun chatActionRepository(accounts: Accounts): ChatActionRepository = accounts.active.chatActionRepository()

    @Provides
    fun chatMediaRepository(accounts: Accounts): ChatMediaRepository = accounts.active.chatMediaRepository()

    @Provides
    fun groupRepository(accounts: Accounts): GroupRepository = accounts.active.groupRepository()

    @Provides
    fun searchRepository(accounts: Accounts): SearchRepository = accounts.active.searchRepository()

    @Provides
    fun contactRepository(accounts: Accounts): ContactRepository = accounts.active.contactRepository()

    @Provides
    fun contactSyncRepository(accounts: Accounts): ContactSyncRepository = accounts.active.contactSyncRepository()

    @Provides
    fun chatCreationRepository(accounts: Accounts): ChatCreationRepository = accounts.active.chatCreationRepository()

    @Provides
    fun storageRepository(accounts: Accounts): StorageRepository = accounts.active.storageRepository()

    @Provides
    fun privacyRepository(accounts: Accounts): PrivacyRepository = accounts.active.privacyRepository()

    @Provides
    fun archiveSettingsRepository(accounts: Accounts): ArchiveSettingsRepository =
        accounts.active.archiveSettingsRepository()

    @Provides
    fun blockListRepository(accounts: Accounts): BlockListRepository = accounts.active.blockListRepository()

    @Provides
    fun userProfileRepository(accounts: Accounts): UserProfileRepository = accounts.active.userProfileRepository()

    @Provides
    fun groupInfoRepository(accounts: Accounts): GroupInfoRepository = accounts.active.groupInfoRepository()

    @Provides
    fun groupAdministrationRepository(accounts: Accounts): GroupAdministrationRepository =
        accounts.active.groupAdministrationRepository()

    @Provides
    fun inviteLinkRepository(accounts: Accounts): InviteLinkRepository = accounts.active.inviteLinkRepository()

    @Provides
    fun chatJoinRepository(accounts: Accounts): ChatJoinRepository = accounts.active.chatJoinRepository()

    @Provides
    fun chatBoostRepository(accounts: Accounts): ChatBoostRepository = accounts.active.chatBoostRepository()

    @Provides
    fun sharedMediaRepository(accounts: Accounts): SharedMediaRepository = accounts.active.sharedMediaRepository()

    @Provides
    fun sessionRepository(accounts: Accounts): SessionRepository = accounts.active.sessionRepository()

    @Provides
    fun chatFolderRepository(accounts: Accounts): ChatFolderRepository = accounts.active.chatFolderRepository()

    @Provides
    fun accountRepository(accounts: Accounts): AccountRepository = accounts.active.accountRepository()

    @Provides
    fun phoneNumberChangeRepository(accounts: Accounts): PhoneNumberChangeRepository =
        accounts.active.phoneNumberChangeRepository()

    @Provides
    fun loginEmailRepository(accounts: Accounts): LoginEmailRepository = accounts.active.loginEmailRepository()

    @Provides
    fun joinRequestRepository(accounts: Accounts): JoinRequestRepository = accounts.active.joinRequestRepository()

    @Provides
    fun chatEventLogRepository(accounts: Accounts): ChatEventLogRepository = accounts.active.chatEventLogRepository()

    @Provides
    fun chatStatisticsRepository(accounts: Accounts): ChatStatisticsRepository =
        accounts.active.chatStatisticsRepository()

    @Provides
    fun chatSettingsRepository(accounts: Accounts): ChatSettingsRepository = accounts.active.chatSettingsRepository()

    @Provides
    fun reactionNotificationRepository(accounts: Accounts): ReactionNotificationRepository =
        accounts.active.reactionNotificationRepository()

    @Provides
    fun quickReactionRepository(accounts: Accounts): QuickReactionRepository = accounts.active.quickReactionRepository()

    @Provides
    fun connectedWebsiteRepository(accounts: Accounts): ConnectedWebsiteRepository =
        accounts.active.connectedWebsiteRepository()

    @Provides
    fun contactJoinedRepository(accounts: Accounts): ContactJoinedRepository = accounts.active.contactJoinedRepository()

    @Provides
    fun personalChannelRepository(accounts: Accounts): PersonalChannelRepository =
        accounts.active.personalChannelRepository()

    @Provides
    fun proxyRepository(accounts: Accounts): ProxyRepository = accounts.active.proxyRepository()

    @Provides
    fun networkUsageRepository(accounts: Accounts): NetworkUsageRepository = accounts.active.networkUsageRepository()

    @Provides
    fun twoStepVerificationRepository(accounts: Accounts): TwoStepVerificationRepository =
        accounts.active.twoStepVerificationRepository()

    @Provides
    fun sendAsRepository(accounts: Accounts): SendAsRepository = accounts.active.sendAsRepository()

    @Provides
    fun chatSearchRepository(accounts: Accounts): ChatSearchRepository = accounts.active.chatSearchRepository()

    @Provides
    fun botButtonRepository(accounts: Accounts): BotButtonRepository = accounts.active.botButtonRepository()

    @Provides
    fun pollRepository(accounts: Accounts): PollRepository = accounts.active.pollRepository()

    @Provides
    fun botInfoRepository(accounts: Accounts): BotInfoRepository = accounts.active.botInfoRepository()

    @Provides
    fun inlineBotRepository(accounts: Accounts): InlineBotRepository = accounts.active.inlineBotRepository()

    @Provides
    fun secretChatRepository(accounts: Accounts): SecretChatRepository = accounts.active.secretChatRepository()

    @Provides
    fun storyRepository(accounts: Accounts): StoryRepository = accounts.active.storyRepository()

    @Provides
    fun callRepository(accounts: Accounts): CallRepository = accounts.active.callRepository()

    @Provides
    fun callHistoryRepository(accounts: Accounts): CallHistoryRepository = accounts.active.callHistoryRepository()

    @Provides
    fun groupCallRepository(accounts: Accounts): GroupCallRepository = accounts.active.groupCallRepository()

    @Provides
    fun conferenceCallRepository(accounts: Accounts): ConferenceCallRepository =
        accounts.active.conferenceCallRepository()

    @Provides
    fun profileRepository(accounts: Accounts): ProfileRepository = accounts.active.profileRepository()

    @Provides
    fun notificationRepository(accounts: Accounts): NotificationRepository = accounts.active.notificationRepository()

    @Provides
    fun notificationSettingsRepository(accounts: Accounts): NotificationSettingsRepository =
        accounts.active.notificationSettingsRepository()

    @Provides
    fun stickerRepository(accounts: Accounts): StickerRepository = accounts.active.stickerRepository()

    @Provides
    fun savedGifRepository(accounts: Accounts): SavedGifRepository = accounts.active.savedGifRepository()

    @Provides
    fun stickerSetRepository(accounts: Accounts): StickerSetRepository = accounts.active.stickerSetRepository()

    @Provides
    fun linkRepository(accounts: Accounts): LinkRepository = accounts.active.linkRepository()

    @Provides
    fun shareRepository(accounts: Accounts): ShareRepository = accounts.active.shareRepository()

    @Provides
    fun translationRepository(accounts: Accounts): TranslationRepository = accounts.active.translationRepository()

    @Provides
    fun customEmojiRepository(accounts: Accounts): CustomEmojiRepository = accounts.active.customEmojiRepository()

    @Provides
    fun chatExportRepository(accounts: Accounts): ChatExportRepository = accounts.active.chatExportRepository()

    @Provides
    fun chatExporter(accounts: Accounts): ChatExporter = accounts.active.chatExporter()

    @Provides
    fun pushRepository(accounts: Accounts): PushRepository = accounts.active.pushRepository()
}

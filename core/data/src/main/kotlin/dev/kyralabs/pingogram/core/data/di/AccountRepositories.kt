package dev.kyralabs.pingogram.core.data.di

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
 * An account's repositories, as [AccountComponent] provides them and [ActiveAccountModule] hands the active account's
 * to the rest of the app. One function per repository of [RepositoryModule], hence `TooManyFunctions` is suppressed.
 */
@Suppress("TooManyFunctions", "ComplexInterface")
interface AccountRepositories {
    fun authRepository(): AuthRepository

    fun connectionRepository(): ConnectionRepository

    fun countryRepository(): CountryRepository

    fun userRepository(): UserRepository

    fun fileRepository(): FileRepository

    fun fileStreamRepository(): FileStreamRepository

    fun transferRepository(): TransferRepository

    fun chatRepository(): ChatRepository

    fun messageRepository(): MessageRepository

    fun messageActionRepository(): MessageActionRepository

    fun sponsoredMessageRepository(): SponsoredMessageRepository

    fun pinnedMessageRepository(): PinnedMessageRepository

    fun scheduledMessageRepository(): ScheduledMessageRepository

    fun forumTopicRepository(): ForumTopicRepository

    fun messageThreadRepository(): MessageThreadRepository

    fun messageInteractionRepository(): MessageInteractionRepository

    fun attachmentRepository(): AttachmentRepository

    fun voiceNoteRepository(): VoiceNoteRepository

    fun videoNoteRepository(): VideoNoteRepository

    fun chatActionRepository(): ChatActionRepository

    fun chatMediaRepository(): ChatMediaRepository

    fun groupRepository(): GroupRepository

    fun searchRepository(): SearchRepository

    fun contactRepository(): ContactRepository

    fun contactSyncRepository(): ContactSyncRepository

    fun chatCreationRepository(): ChatCreationRepository

    fun storageRepository(): StorageRepository

    fun privacyRepository(): PrivacyRepository

    fun archiveSettingsRepository(): ArchiveSettingsRepository

    fun blockListRepository(): BlockListRepository

    fun userProfileRepository(): UserProfileRepository

    fun groupInfoRepository(): GroupInfoRepository

    fun groupAdministrationRepository(): GroupAdministrationRepository

    fun inviteLinkRepository(): InviteLinkRepository

    fun chatJoinRepository(): ChatJoinRepository

    fun chatBoostRepository(): ChatBoostRepository

    fun sharedMediaRepository(): SharedMediaRepository

    fun sessionRepository(): SessionRepository

    fun chatFolderRepository(): ChatFolderRepository

    fun accountRepository(): AccountRepository

    fun phoneNumberChangeRepository(): PhoneNumberChangeRepository

    fun loginEmailRepository(): LoginEmailRepository

    fun joinRequestRepository(): JoinRequestRepository

    fun chatEventLogRepository(): ChatEventLogRepository

    fun chatStatisticsRepository(): ChatStatisticsRepository

    fun chatSettingsRepository(): ChatSettingsRepository

    fun reactionNotificationRepository(): ReactionNotificationRepository

    fun quickReactionRepository(): QuickReactionRepository

    fun connectedWebsiteRepository(): ConnectedWebsiteRepository

    fun contactJoinedRepository(): ContactJoinedRepository

    fun personalChannelRepository(): PersonalChannelRepository

    fun proxyRepository(): ProxyRepository

    fun networkUsageRepository(): NetworkUsageRepository

    fun twoStepVerificationRepository(): TwoStepVerificationRepository

    fun sendAsRepository(): SendAsRepository

    fun chatSearchRepository(): ChatSearchRepository

    fun botButtonRepository(): BotButtonRepository

    fun pollRepository(): PollRepository

    fun botInfoRepository(): BotInfoRepository

    fun inlineBotRepository(): InlineBotRepository

    fun secretChatRepository(): SecretChatRepository

    fun storyRepository(): StoryRepository

    fun callRepository(): CallRepository

    fun callHistoryRepository(): CallHistoryRepository

    fun groupCallRepository(): GroupCallRepository

    fun conferenceCallRepository(): ConferenceCallRepository

    fun profileRepository(): ProfileRepository

    fun notificationRepository(): NotificationRepository

    fun notificationSettingsRepository(): NotificationSettingsRepository

    fun stickerRepository(): StickerRepository

    fun savedGifRepository(): SavedGifRepository

    fun stickerSetRepository(): StickerSetRepository

    fun linkRepository(): LinkRepository

    fun shareRepository(): ShareRepository

    fun translationRepository(): TranslationRepository

    fun customEmojiRepository(): CustomEmojiRepository

    fun chatExportRepository(): ChatExportRepository

    fun chatExporter(): ChatExporter

    fun pushRepository(): PushRepository
}

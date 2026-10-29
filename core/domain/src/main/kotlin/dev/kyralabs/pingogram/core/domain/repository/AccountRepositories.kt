package dev.kyralabs.pingogram.core.domain.repository

/**
 * An account's repositories (`docs/adr/0031-multiple-accounts.md`): what `core:data`'s account graph provides, the
 * active account's of which the rest of the app injects one by one, and every account's of which [AccountGraphs] lists
 * for what follows them all. One function per repository of `core:data`'s `RepositoryModule`, hence `TooManyFunctions`
 * is suppressed.
 */
@Suppress("TooManyFunctions", "ComplexInterface")
interface AccountRepositories {
    fun authRepository(): AuthRepository

    fun chatLockRepository(): ChatLockRepository

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

    fun savedMessagesTopicRepository(): SavedMessagesTopicRepository

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

    fun dataOptionsRepository(): DataOptionsRepository

    fun frequentContactsRepository(): FrequentContactsRepository

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

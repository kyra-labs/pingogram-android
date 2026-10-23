package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * Builders for TdApi objects in tests (`docs/TESTING.md`), with harmless defaults: test-DC phone numbers
 * (`+99966…`) and `example.org` addresses. TdApi's `toString()` is native, so never print these in a JVM test.
 */
object TdApiFixtures {
    const val PHONE_NUMBER = "+99966 2 1234"

    fun waitTdlibParameters() = TdApi.AuthorizationStateWaitTdlibParameters()

    fun waitPhoneNumber() = TdApi.AuthorizationStateWaitPhoneNumber()

    fun waitPremiumPurchase(
        storeProductId: String = "premium.monthly",
        premiumDayCount: Int = 30,
        supportEmailAddress: String = "support@example.org",
        supportEmailSubject: String = "Login",
    ) = TdApi.AuthorizationStateWaitPremiumPurchase(
        storeProductId,
        premiumDayCount,
        supportEmailAddress,
        supportEmailSubject,
    )

    fun waitEmailAddress(
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
    ) = TdApi.AuthorizationStateWaitEmailAddress(allowAppleId, allowGoogleId)

    fun waitEmailCode(
        emailAddressPattern: String = "a***@example.org",
        codeLength: Int = 6,
        allowAppleId: Boolean = false,
        allowGoogleId: Boolean = false,
        emailAddressResetState: TdApi.EmailAddressResetState? = null,
    ) = TdApi.AuthorizationStateWaitEmailCode(
        allowAppleId,
        allowGoogleId,
        TdApi.EmailAddressAuthenticationCodeInfo(emailAddressPattern, codeLength),
        emailAddressResetState,
    )

    fun waitCode(codeInfo: TdApi.AuthenticationCodeInfo = authenticationCodeInfo()) =
        TdApi.AuthorizationStateWaitCode(codeInfo)

    fun authenticationCodeInfo(
        phoneNumber: String = PHONE_NUMBER,
        type: TdApi.AuthenticationCodeType = TdApi.AuthenticationCodeTypeSms(5),
        nextType: TdApi.AuthenticationCodeType? = null,
        timeoutSeconds: Int = 60,
    ) = TdApi.AuthenticationCodeInfo(phoneNumber, type, nextType, timeoutSeconds)

    fun waitOtherDeviceConfirmation(link: String = "tg://login?token=dGVzdA") =
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation(link)

    fun waitRegistration(
        termsOfService: TdApi.TermsOfService = TdApi.TermsOfService(formattedText("Terms of Service"), 0, true),
    ) = TdApi.AuthorizationStateWaitRegistration(termsOfService)

    fun waitPassword(
        passwordHint: String = "",
        hasRecoveryEmailAddress: Boolean = false,
        hasPassportData: Boolean = false,
        recoveryEmailAddressPattern: String = "",
    ) = TdApi.AuthorizationStateWaitPassword(
        passwordHint,
        hasRecoveryEmailAddress,
        hasPassportData,
        recoveryEmailAddressPattern,
    )

    fun ready() = TdApi.AuthorizationStateReady()

    fun loggingOut() = TdApi.AuthorizationStateLoggingOut()

    fun closing() = TdApi.AuthorizationStateClosing()

    fun closed() = TdApi.AuthorizationStateClosed()

    /** One instance of every `AuthorizationState` of the pinned TDLib version, in td_api.tl order. */
    fun allAuthorizationStates(): List<TdApi.AuthorizationState> =
        listOf(
            waitTdlibParameters(),
            waitPhoneNumber(),
            waitPremiumPurchase(),
            waitEmailAddress(),
            waitEmailCode(),
            waitCode(),
            waitOtherDeviceConfirmation(),
            waitRegistration(),
            waitPassword(),
            ready(),
            loggingOut(),
            closing(),
            closed(),
        )

    /** One instance of every `ConnectionState`, in td_api.tl order. */
    fun allConnectionStates(): List<TdApi.ConnectionState> =
        listOf(
            TdApi.ConnectionStateWaitingForNetwork(),
            TdApi.ConnectionStateConnectingToProxy(),
            TdApi.ConnectionStateConnecting(),
            TdApi.ConnectionStateUpdating(),
            TdApi.ConnectionStateReady(),
        )

    fun authorizationStateUpdate(state: TdApi.AuthorizationState) = TdApi.UpdateAuthorizationState(state)

    fun connectionStateUpdate(state: TdApi.ConnectionState) = TdApi.UpdateConnectionState(state)

    fun formattedText(text: String) = TdApi.FormattedText(text, emptyArray())

    fun error(
        code: Int = 400,
        message: String = "BAD_REQUEST",
    ) = TdApi.Error(code, message)

    /** What TDLib returns for a flood wait: code 429 with the seconds in the message. */
    fun floodWait(seconds: Int) = TdApi.Error(429, "Too Many Requests: retry after $seconds")

    /** A remote file that is not downloaded yet; pass [localPath] and [downloadedSize] for other states. */
    fun file(
        id: Int = 1,
        size: Long = 1_024,
        expectedSize: Long = size,
        localPath: String = "",
        downloadedSize: Long = 0,
        isDownloadingActive: Boolean = false,
        isDownloadingCompleted: Boolean = false,
        canBeDownloaded: Boolean = true,
    ) = TdApi.File(
        id,
        size,
        expectedSize,
        TdApi.LocalFile(
            localPath,
            canBeDownloaded,
            isDownloadingCompleted,
            isDownloadingActive,
            isDownloadingCompleted,
            0,
            downloadedSize,
            downloadedSize,
        ),
        TdApi.RemoteFile("remote-$id", "unique-$id", false, true, size),
    )

    fun minithumbnail(
        width: Int = 40,
        height: Int = 30,
        data: ByteArray = byteArrayOf(1, 2, 3),
    ) = TdApi.Minithumbnail(width, height, data)

    /** A regular user with a test-DC phone number (digits only, as TDLib reports it), no photo and no status. */
    fun user(
        id: Long = 1,
        firstName: String = "Ada",
        lastName: String = "",
        usernames: List<String> = emptyList(),
        phoneNumber: String = "9996621234",
        status: TdApi.UserStatus = TdApi.UserStatusEmpty(),
        profilePhoto: TdApi.ProfilePhoto? = null,
        verificationStatus: TdApi.VerificationStatus? = null,
        isPremium: Boolean = false,
        isContact: Boolean = false,
        isMutualContact: Boolean = false,
        isSupport: Boolean = false,
        haveAccess: Boolean = true,
        type: TdApi.UserType = TdApi.UserTypeRegular(),
    ) = TdApi.User().also {
        it.id = id
        it.firstName = firstName
        it.lastName = lastName
        it.usernames = usernames(usernames)
        it.phoneNumber = phoneNumber
        it.status = status
        it.profilePhoto = profilePhoto
        it.verificationStatus = verificationStatus
        it.isPremium = isPremium
        it.isContact = isContact
        it.isMutualContact = isMutualContact
        it.isSupport = isSupport
        it.haveAccess = haveAccess
        it.type = type
        it.languageCode = "en"
    }

    /** [active] usernames, the first one editable. */
    fun usernames(active: List<String>) =
        TdApi.Usernames(active.toTypedArray(), emptyArray(), active.firstOrNull().orEmpty(), emptyArray(), "")

    fun profilePhoto(
        smallFileId: Int = 10,
        bigFileId: Int = 11,
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
        id: Long = 1,
    ) = TdApi.ProfilePhoto(id, file(smallFileId), file(bigFileId), minithumbnail, false, false)

    /** Online until [expires], a Unix time. */
    fun onlineStatus(expires: Int) = TdApi.UserStatusOnline(expires)

    /** Last online at [wasOnline], a Unix time. */
    fun offlineStatus(wasOnline: Int) = TdApi.UserStatusOffline(wasOnline)

    fun updateUser(user: TdApi.User = user()) = TdApi.UpdateUser(user)

    fun updateUserStatus(
        userId: Long,
        status: TdApi.UserStatus,
    ) = TdApi.UpdateUserStatus(userId, status)

    /** The option that tells the logged-in user's id; [userId] null for the empty value of a removed option. */
    fun myIdOption(userId: Long?) =
        TdApi.UpdateOption("my_id", userId?.let { TdApi.OptionValueInteger(it) } ?: TdApi.OptionValueEmpty())

    fun updateFile(file: TdApi.File) = TdApi.UpdateFile(file)

    // Messages. Dates are Unix seconds; ids are arbitrary but distinct per kind, so mix-ups show in assertions.

    /**
     * An incoming text message from user 42 in chat 100; every other field is TDLib's "none" (null, 0, empty).
     * TdApi.Message has 46 constructor parameters, so the builder sets fields by name instead.
     */
    fun message(
        id: Long = 1,
        chatId: Long = 100,
        sender: TdApi.MessageSender = TdApi.MessageSenderUser(42),
        date: Int = 1_700_000_000,
        content: TdApi.MessageContent = messageText(),
        isOutgoing: Boolean = false,
        sendingState: TdApi.MessageSendingState? = null,
        editDate: Int = 0,
        isPinned: Boolean = false,
        isChannelPost: Boolean = false,
        containsUnreadMention: Boolean = false,
        mediaAlbumId: Long = 0,
        authorSignature: String = "",
        viaBotUserId: Long = 0,
        replyTo: TdApi.MessageReplyTo? = null,
        forwardInfo: TdApi.MessageForwardInfo? = null,
        interactionInfo: TdApi.MessageInteractionInfo? = null,
        selfDestructType: TdApi.MessageSelfDestructType? = null,
    ) = TdApi.Message().also {
        it.id = id
        it.chatId = chatId
        it.senderId = sender
        it.date = date
        it.content = content
        it.isOutgoing = isOutgoing
        it.sendingState = sendingState
        it.editDate = editDate
        it.isPinned = isPinned
        it.isChannelPost = isChannelPost
        it.containsUnreadMention = containsUnreadMention
        it.mediaAlbumId = mediaAlbumId
        it.authorSignature = authorSignature
        it.viaBotUserId = viaBotUserId
        it.replyTo = replyTo
        it.forwardInfo = forwardInfo
        it.interactionInfo = interactionInfo
        it.selfDestructType = selfDestructType
        it.unreadReactions = emptyArray()
        it.senderTag = ""
        it.summaryLanguageCode = ""
    }

    fun textEntity(
        offset: Int,
        length: Int,
        type: TdApi.TextEntityType,
    ) = TdApi.TextEntity(offset, length, type)

    fun messageText(
        text: String = "Hello",
        entities: Array<TdApi.TextEntity> = emptyArray(),
        linkPreview: TdApi.LinkPreview? = null,
    ) = TdApi.MessageText(TdApi.FormattedText(text, entities), linkPreview, null)

    /** A link preview of an article; pass [type] for other kinds of links. */
    fun linkPreview(
        url: String = "https://example.org/article",
        title: String = "Example article",
        type: TdApi.LinkPreviewType = TdApi.LinkPreviewTypeArticle(photo()),
    ) = TdApi.LinkPreview(
        url,
        "example.org/article",
        "Example",
        title,
        formattedText("An example page"),
        "",
        type,
        true,
        true,
        false,
        false,
        false,
        0,
    )

    fun photoSize(
        type: String = "x",
        width: Int = 800,
        height: Int = 600,
        file: TdApi.File = file(id = 11),
    ) = TdApi.PhotoSize(type, file, width, height, intArrayOf())

    fun photo(
        sizes: Array<TdApi.PhotoSize> = arrayOf(photoSize("m", 320, 240, file(id = 10)), photoSize()),
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
    ) = TdApi.Photo(false, minithumbnail, sizes)

    fun messagePhoto(
        photo: TdApi.Photo = photo(),
        caption: TdApi.FormattedText = formattedText(""),
        hasSpoiler: Boolean = false,
        isSecret: Boolean = false,
    ) = TdApi.MessagePhoto(photo, null, caption, false, hasSpoiler, isSecret)

    fun thumbnail(
        format: TdApi.ThumbnailFormat = TdApi.ThumbnailFormatJpeg(),
        width: Int = 320,
        height: Int = 180,
        file: TdApi.File = file(id = 20),
    ) = TdApi.Thumbnail(format, width, height, file)

    fun video(
        duration: Int = 30,
        width: Int = 1280,
        height: Int = 720,
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
        thumbnail: TdApi.Thumbnail? = thumbnail(),
        file: TdApi.File = file(id = 21, size = 5_000_000),
    ) = TdApi.Video(duration, width, height, "video.mp4", "video/mp4", false, true, minithumbnail, thumbnail, file)

    fun messageVideo(
        video: TdApi.Video = video(),
        caption: TdApi.FormattedText = formattedText(""),
        hasSpoiler: Boolean = false,
    ) = TdApi.MessageVideo(video, emptyArray(), emptyArray(), null, 0, caption, false, hasSpoiler, false)

    fun animation(
        duration: Int = 3,
        width: Int = 480,
        height: Int = 270,
        file: TdApi.File = file(id = 22),
    ) = TdApi.Animation(duration, width, height, "animation.mp4", "video/mp4", false, null, thumbnail(), file)

    fun audio(
        duration: Int = 200,
        title: String = "Song",
        performer: String = "Band",
        file: TdApi.File = file(id = 23),
    ) = TdApi.Audio(duration, title, performer, "song.mp3", "audio/mpeg", null, null, emptyArray(), file)

    fun voiceNote(
        duration: Int = 5,
        waveform: ByteArray = byteArrayOf(0, 4, 8, 31),
        file: TdApi.File = file(id = 30),
    ) = TdApi.VoiceNote(duration, waveform, "audio/ogg", null, file)

    fun messageVoiceNote(
        voiceNote: TdApi.VoiceNote = voiceNote(),
        caption: TdApi.FormattedText = formattedText(""),
        isListened: Boolean = false,
    ) = TdApi.MessageVoiceNote(voiceNote, caption, isListened)

    fun videoNote(
        duration: Int = 10,
        length: Int = 384,
        file: TdApi.File = file(id = 31),
    ) = TdApi.VideoNote(duration, byteArrayOf(), length, minithumbnail(), thumbnail(), null, file)

    fun document(
        fileName: String = "report.pdf",
        mimeType: String = "application/pdf",
        thumbnail: TdApi.Thumbnail? = null,
        file: TdApi.File = file(id = 40),
    ) = TdApi.Document(fileName, mimeType, null, thumbnail, file)

    fun messageDocument(
        document: TdApi.Document = document(),
        caption: TdApi.FormattedText = formattedText(""),
    ) = TdApi.MessageDocument(document, caption)

    fun sticker(
        format: TdApi.StickerFormat = TdApi.StickerFormatWebp(),
        emoji: String = "👍",
        setId: Long = 7,
        thumbnail: TdApi.Thumbnail? = null,
        file: TdApi.File = file(id = 50),
    ) = TdApi.Sticker(1, setId, 512, 512, emoji, format, TdApi.StickerFullTypeRegular(null), thumbnail, file)

    fun messageSticker(sticker: TdApi.Sticker = sticker()) = TdApi.MessageSticker(sticker, false)

    fun pollOption(
        text: String,
        voterCount: Int = 0,
        votePercentage: Int = 0,
        isChosen: Boolean = false,
    ) = TdApi.PollOption(
        "",
        formattedText(text),
        null,
        voterCount,
        votePercentage,
        emptyArray(),
        isChosen,
        false,
        null,
        0,
    )

    /** An open anonymous poll; pass a `TdApi.PollTypeQuiz` as [type] for a quiz. */
    fun poll(
        question: String = "Lunch?",
        options: Array<TdApi.PollOption> = arrayOf(pollOption("Pizza"), pollOption("Salad")),
        type: TdApi.PollType = TdApi.PollTypeRegular(),
        totalVoterCount: Int = 0,
        canSeeResults: Boolean = false,
        isAnonymous: Boolean = true,
        allowsMultipleAnswers: Boolean = false,
        isClosed: Boolean = false,
        optionOrder: IntArray = intArrayOf(),
    ) = TdApi.Poll(
        1,
        formattedText(question),
        options,
        totalVoterCount,
        emptyArray(),
        false,
        canSeeResults,
        isAnonymous,
        allowsMultipleAnswers,
        false,
        false,
        emptyArray(),
        optionOrder,
        type,
        0,
        0,
        isClosed,
        null,
    )

    fun messagePoll(
        poll: TdApi.Poll = poll(),
        description: TdApi.FormattedText = formattedText(""),
    ) = TdApi.MessagePoll(poll, description, null, false)

    /** The service message for users added to a group; a user who joins by themselves adds only their own id. */
    fun messageChatAddMembers(vararg memberUserIds: Long) = TdApi.MessageChatAddMembers(memberUserIds)

    // Chats. The default chat, 100, is the private chat with user 42 that message() puts its messages in.

    /** A position in [list]; order 0 is how TDLib takes a chat out of a list. */
    fun chatPosition(
        list: TdApi.ChatList = TdApi.ChatListMain(),
        order: Long = 1_000,
        isPinned: Boolean = false,
    ) = TdApi.ChatPosition(list, order, isPinned, null)

    /**
     * Settings that follow the defaults of the chat's scope; [useDefaultMuteFor] false gives the chat a mute setting of
     * its own, [useDefaultSound] and [useDefaultShowPreview] false a sound or preview setting.
     */
    fun chatNotificationSettings(
        useDefaultMuteFor: Boolean = true,
        muteFor: Int = 0,
        useDefaultSound: Boolean = true,
        soundId: Long = 0,
        useDefaultShowPreview: Boolean = true,
        showPreview: Boolean = true,
    ) = TdApi.ChatNotificationSettings(
        useDefaultMuteFor,
        muteFor,
        useDefaultSound,
        soundId,
        useDefaultShowPreview,
        showPreview,
        true,
        false,
        true,
        0,
        true,
        true,
        true,
        false,
        true,
        false,
    )

    fun updateChatNotificationSettings(
        chatId: Long,
        settings: TdApi.ChatNotificationSettings,
    ) = TdApi.UpdateChatNotificationSettings(chatId, settings)

    /** Unmuted defaults; a positive [muteFor] (seconds) mutes the scope. */
    fun scopeNotificationSettings(muteFor: Int = 0) =
        TdApi.ScopeNotificationSettings(muteFor, 0, true, true, false, 0, true, false, false)

    fun chatPhotoInfo(
        smallFileId: Int = 12,
        bigFileId: Int = 13,
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
    ) = TdApi.ChatPhotoInfo(file(smallFileId), file(bigFileId), minithumbnail, false, false)

    fun draftMessage(
        content: TdApi.DraftMessageContent = draftText(),
        date: Int = 1_700_000_000,
    ) = TdApi.DraftMessage(null, date, content, 0, null)

    fun draftText(text: String = "Draft") = TdApi.DraftMessageContentText(formattedText(text), null)

    /**
     * A read private chat with user 42 in the main list, with the default notification settings, where text can be
     * sent, that can be deleted for self or for both users; every other field is TDLib's "none". TdApi.Chat has 46
     * constructor parameters, so the builder sets fields by name instead.
     */
    fun chat(
        id: Long = 100,
        type: TdApi.ChatType = TdApi.ChatTypePrivate(42),
        title: String = "Ada",
        photo: TdApi.ChatPhotoInfo? = null,
        lastMessage: TdApi.Message? = null,
        positions: Array<TdApi.ChatPosition> = arrayOf(chatPosition()),
        unreadCount: Int = 0,
        unreadMentionCount: Int = 0,
        unreadReactionCount: Int = 0,
        lastReadInboxMessageId: Long = 0,
        lastReadOutboxMessageId: Long = 0,
        isMarkedAsUnread: Boolean = false,
        notificationSettings: TdApi.ChatNotificationSettings = chatNotificationSettings(),
        draftMessage: TdApi.DraftMessage? = null,
        hasProtectedContent: Boolean = false,
        messageAutoDeleteTime: Int = 0,
        canBeDeletedOnlyForSelf: Boolean = true,
        canBeDeletedForAllUsers: Boolean = true,
        permissions: TdApi.ChatPermissions = TdApi.ChatPermissions().apply { canSendBasicMessages = true },
        messageSenderId: TdApi.MessageSender? = null,
    ) = TdApi.Chat().also {
        it.id = id
        it.type = type
        it.title = title
        it.photo = photo
        it.lastMessage = lastMessage
        it.positions = positions
        it.chatLists = positions.map { position -> position.list }.toTypedArray()
        it.unreadCount = unreadCount
        it.unreadMentionCount = unreadMentionCount
        it.unreadReactionCount = unreadReactionCount
        it.lastReadInboxMessageId = lastReadInboxMessageId
        it.lastReadOutboxMessageId = lastReadOutboxMessageId
        it.isMarkedAsUnread = isMarkedAsUnread
        it.notificationSettings = notificationSettings
        it.draftMessage = draftMessage
        it.hasProtectedContent = hasProtectedContent
        it.messageAutoDeleteTime = messageAutoDeleteTime
        it.canBeDeletedOnlyForSelf = canBeDeletedOnlyForSelf
        it.canBeDeletedForAllUsers = canBeDeletedForAllUsers
        it.permissions = permissions
        it.messageSenderId = messageSenderId
        it.profileAccentColorId = -1
        it.clientData = ""
    }

    fun updateNewChat(chat: TdApi.Chat = chat()) = TdApi.UpdateNewChat(chat)

    fun updateChatPosition(
        chatId: Long,
        position: TdApi.ChatPosition,
    ) = TdApi.UpdateChatPosition(chatId, position)

    fun updateScopeNotificationSettings(
        scope: TdApi.NotificationSettingsScope,
        muteFor: Int,
    ) = TdApi.UpdateScopeNotificationSettings(scope, scopeNotificationSettings(muteFor))

    /** A folder without a color tag; [colorId] 0–6 gives it one. */
    fun chatFolderInfo(
        id: Int = 1,
        title: String = "Work",
        iconName: String = "Work",
        colorId: Int = -1,
    ) = TdApi.ChatFolderInfo(
        id,
        TdApi.ChatFolderName(formattedText(title), false),
        TdApi.ChatFolderIcon(iconName),
        colorId,
        false,
        false,
    )

    /** The folders in order, with the main list first. */
    fun updateChatFolders(
        vararg folders: TdApi.ChatFolderInfo,
        mainChatListPosition: Int = 0,
    ) = TdApi.UpdateChatFolders(arrayOf(*folders), mainChatListPosition, false)

    fun updateUnreadChatCount(
        list: TdApi.ChatList = TdApi.ChatListMain(),
        totalCount: Int = 0,
        unreadCount: Int = 0,
        unreadUnmutedCount: Int = 0,
        markedAsUnreadCount: Int = 0,
        markedAsUnreadUnmutedCount: Int = 0,
    ) = TdApi.UpdateUnreadChatCount(
        list,
        totalCount,
        unreadCount,
        unreadUnmutedCount,
        markedAsUnreadCount,
        markedAsUnreadUnmutedCount,
    )

    // Groups and channels. The default basic group is 5 and the default supergroup 6, as in chat types of tests.

    /** The owner; one who left ([isMember] false) stays the owner. */
    fun creatorStatus(isMember: Boolean = true) = TdApi.ChatMemberStatusCreator(false, isMember)

    fun administratorStatus() = TdApi.ChatMemberStatusAdministrator(false, TdApi.ChatAdministratorRights())

    fun memberStatus() = TdApi.ChatMemberStatusMember(0)

    fun restrictedStatus(isMember: Boolean = true) =
        TdApi.ChatMemberStatusRestricted(isMember, 0, TdApi.ChatPermissions())

    fun leftStatus() = TdApi.ChatMemberStatusLeft()

    fun bannedStatus() = TdApi.ChatMemberStatusBanned(0)

    /** An active basic group of 3 members, the current user among them. */
    fun basicGroup(
        id: Long = 5,
        memberCount: Int = 3,
        status: TdApi.ChatMemberStatus = memberStatus(),
        isActive: Boolean = true,
        upgradedToSupergroupId: Long = 0,
    ) = TdApi.BasicGroup(id, memberCount, status, isActive, upgradedToSupergroupId)

    /**
     * A private supergroup of unknown size, the current user a member, without badges; [isChannel] for a channel.
     * TdApi.Supergroup has 25 constructor parameters, so the builder sets fields by name instead.
     */
    fun supergroup(
        id: Long = 6,
        usernames: List<String> = emptyList(),
        memberCount: Int = 0,
        status: TdApi.ChatMemberStatus = memberStatus(),
        isChannel: Boolean = false,
        isForum: Boolean = false,
        verificationStatus: TdApi.VerificationStatus? = null,
    ) = TdApi.Supergroup().also {
        it.id = id
        it.usernames = usernames(usernames)
        it.date = 1_700_000_000
        it.status = status
        it.memberCount = memberCount
        it.isChannel = isChannel
        it.isForum = isForum
        it.verificationStatus = verificationStatus
    }

    fun updateBasicGroup(group: TdApi.BasicGroup = basicGroup()) = TdApi.UpdateBasicGroup(group)

    fun updateSupergroup(group: TdApi.Supergroup = supergroup()) = TdApi.UpdateSupergroup(group)

    // Chat actions ("typing…").

    /** [action] of user 42 in chat 100 itself; a [topicId] reports it for a thread or topic of the chat. */
    fun updateChatAction(
        action: TdApi.ChatAction = TdApi.ChatActionTyping(),
        chatId: Long = 100,
        sender: TdApi.MessageSender = TdApi.MessageSenderUser(42),
        topicId: TdApi.MessageTopic? = null,
    ) = TdApi.UpdateChatAction(chatId, topicId, sender, action)

    // Chat history: getChatHistory answers and the updates of messages.

    /** A list of messages as getChatHistory answers it, newest first; td_api.tl allows null entries. */
    fun messages(vararg messages: TdApi.Message?) = TdApi.Messages(messages.size, arrayOf(*messages))

    /** A message being sent: it has a temporary id until updateMessageSendSucceeded or updateMessageSendFailed. */
    fun pendingState() = TdApi.MessageSendingStatePending(0)

    fun failedState(
        error: TdApi.Error = error(),
        canRetry: Boolean = false,
    ) = TdApi.MessageSendingStateFailed(error, canRetry, false, false, false, 0, 0.0)

    fun updateNewMessage(message: TdApi.Message) = TdApi.UpdateNewMessage(message)

    fun updateMessageSendSucceeded(
        message: TdApi.Message,
        oldMessageId: Long,
    ) = TdApi.UpdateMessageSendSucceeded(message, oldMessageId)

    /** [message] carries the failed sending state and its new id. */
    fun updateMessageSendFailed(
        message: TdApi.Message,
        oldMessageId: Long,
        error: TdApi.Error = error(),
    ) = TdApi.UpdateMessageSendFailed(message, oldMessageId, error)

    fun updateMessageContent(
        chatId: Long,
        messageId: Long,
        content: TdApi.MessageContent,
    ) = TdApi.UpdateMessageContent(chatId, messageId, content)

    fun updateMessageEdited(
        chatId: Long,
        messageId: Long,
        editDate: Int,
    ) = TdApi.UpdateMessageEdited(chatId, messageId, editDate, null)

    fun updateMessageIsPinned(
        chatId: Long,
        messageId: Long,
        isPinned: Boolean,
    ) = TdApi.UpdateMessageIsPinned(chatId, messageId, isPinned)

    fun updateMessageInteractionInfo(
        chatId: Long,
        messageId: Long,
        interactionInfo: TdApi.MessageInteractionInfo?,
    ) = TdApi.UpdateMessageInteractionInfo(chatId, messageId, interactionInfo)

    fun updateMessageContentOpened(
        chatId: Long,
        messageId: Long,
    ) = TdApi.UpdateMessageContentOpened(chatId, messageId)

    fun updateMessageMentionRead(
        chatId: Long,
        messageId: Long,
        unreadMentionCount: Int = 0,
    ) = TdApi.UpdateMessageMentionRead(chatId, messageId, unreadMentionCount)

    /**
     * Messages deleted for good by default. Not [isPermanent]: they became inaccessible. [fromCache]: TDLib only
     * unloaded them from memory.
     */
    fun updateDeleteMessages(
        chatId: Long,
        vararg messageIds: Long,
        isPermanent: Boolean = true,
        fromCache: Boolean = false,
    ) = TdApi.UpdateDeleteMessages(chatId, messageIds, isPermanent, fromCache)
}

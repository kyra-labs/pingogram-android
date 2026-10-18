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
        it.usernames =
            TdApi.Usernames(usernames.toTypedArray(), emptyArray(), usernames.firstOrNull().orEmpty(), emptyArray(), "")
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

    fun profilePhoto(
        smallFileId: Int = 10,
        bigFileId: Int = 11,
        minithumbnail: TdApi.Minithumbnail? = minithumbnail(),
    ) = TdApi.ProfilePhoto(1, file(smallFileId), file(bigFileId), minithumbnail, false, false)

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
}

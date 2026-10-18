package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.Video
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * Domain models for the viewer's tests. File ids follow the message id, so each item's files differ: a photo's full
 * size is `id * 10 + 1` and its small size `id * 10`; a video's or GIF's file is `id * 10 + 1`, its thumbnail
 * `id * 10 + 2`.
 */
object MediaFixtures {
    const val CHAT = 100L
    const val ADA = 2L
    const val CHANNEL = -1_000_000_000_200L

    /** 2026-10-18T09:41:30Z, a Sunday. */
    val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")

    fun text(text: String) = FormattedText(text, persistentListOf())

    fun fullFileId(messageId: Long) = (messageId * 10 + 1).toInt()

    fun smallFileId(messageId: Long) = (messageId * 10).toInt()

    fun photoContent(
        messageId: Long,
        caption: String = "",
        hasSpoiler: Boolean = false,
        isSecret: Boolean = false,
    ) = MessageContent.Photo(
        photo =
            Photo(
                sizes =
                    persistentListOf(
                        PhotoSize("m", 320, 240, FileRef(smallFileId(messageId), 20_000)),
                        PhotoSize("y", 1280, 960, FileRef(fullFileId(messageId), 200_000)),
                    ),
                minithumbnail = null,
            ),
        caption = text(caption),
        hasSpoiler = hasSpoiler,
        isSecret = isSecret,
        showCaptionAboveMedia = false,
    )

    fun videoContent(
        messageId: Long,
        duration: Int = 60,
    ) = MessageContent.Video(
        video =
            Video(
                duration = duration,
                width = 1920,
                height = 1080,
                minithumbnail = null,
                thumbnail = Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef((messageId * 10 + 2).toInt(), 9_000)),
                supportsStreaming = true,
                file = FileRef(fullFileId(messageId), 12_000_000),
            ),
        caption = text(""),
        hasSpoiler = false,
        isSecret = false,
        showCaptionAboveMedia = false,
    )

    fun gifContent(messageId: Long) =
        MessageContent.Animation(
            animation =
                Animation(
                    duration = 3,
                    width = 480,
                    height = 270,
                    mimeType = "video/mp4",
                    minithumbnail = null,
                    thumbnail = null,
                    file = FileRef(fullFileId(messageId), 400_000),
                ),
            caption = text(""),
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    fun message(
        id: Long,
        content: MessageContent = photoContent(id),
        sender: MessageSender = MessageSender.User(ADA),
        date: Instant = NOW,
    ) = Message(
        id = id,
        chatId = CHAT,
        sender = sender,
        date = date.epochSecond,
        editDate = 0,
        isOutgoing = false,
        sendingState = null,
        isPinned = false,
        isChannelPost = false,
        containsUnreadMention = false,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = null,
        forwardInfo = null,
        interactionInfo = null,
        selfDestruct = null,
        content = content,
    )

    /** Photos with ids [ids]. */
    fun photos(ids: LongRange): List<Message> = ids.map { message(it) }

    fun user(
        id: Long,
        firstName: String,
        lastName: String = "",
    ) = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        usernames = persistentListOf(),
        phoneNumber = "",
        status = UserStatus.Empty,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = UserType.Regular,
        haveAccess = true,
    )

    fun chat(
        id: Long,
        title: String,
    ) = Chat(
        id = id,
        type = ChatType.Supergroup(200, isChannel = true),
        title = title,
        photo = null,
        lastMessage = null,
        positions = persistentListOf(),
        unreadCount = 0,
        unreadMentionCount = 0,
        unreadReactionCount = 0,
        lastReadInboxMessageId = 0,
        lastReadOutboxMessageId = 0,
        isMarkedAsUnread = false,
        isMuted = false,
        draft = null,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
        permissions = ChatPermissions.All,
    )
}

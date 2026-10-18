package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/** Domain models for the chat list's tests, with defaults for everything a test does not care about. */
object ChatListFixtures {
    /** The current user. */
    const val ME = 1L

    /** 2026-10-18T09:41:30Z, a Sunday. */
    val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")

    fun text(text: String) = FormattedText(text, persistentListOf())

    fun chat(
        id: Long,
        type: ChatType = ChatType.Private(userId = id),
        title: String = "Chat $id",
        lastMessage: Message? = null,
        lists: List<ChatListType> = listOf(ChatListType.Main),
        pinnedIn: List<ChatListType> = emptyList(),
        unreadCount: Int = 0,
        unreadMentionCount: Int = 0,
        unreadReactionCount: Int = 0,
        lastReadOutboxMessageId: Long = 0,
        isMarkedAsUnread: Boolean = false,
        isMuted: Boolean = false,
        draft: ChatDraft? = null,
    ) = Chat(
        id = id,
        type = type,
        title = title,
        photo = null,
        lastMessage = lastMessage,
        positions =
            lists
                .mapIndexed { index, list -> ChatPosition(list, order = 1000L - index, isPinned = list in pinnedIn) }
                .toImmutableList(),
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        unreadReactionCount = unreadReactionCount,
        lastReadInboxMessageId = 0,
        lastReadOutboxMessageId = lastReadOutboxMessageId,
        isMarkedAsUnread = isMarkedAsUnread,
        isMuted = isMuted,
        draft = draft,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
    )

    fun message(
        chatId: Long,
        id: Long = 10,
        sender: MessageSender = MessageSender.User(chatId),
        content: MessageContent = MessageContent.Text(text("Hello"), linkPreview = null),
        date: Instant = NOW,
        isOutgoing: Boolean = false,
        sendingState: SendingState? = null,
        isChannelPost: Boolean = false,
    ) = Message(
        id = id,
        chatId = chatId,
        sender = sender,
        date = date.epochSecond,
        editDate = 0,
        isOutgoing = isOutgoing,
        sendingState = sendingState,
        isPinned = false,
        isChannelPost = isChannelPost,
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

    fun draft(
        text: String,
        date: Instant = NOW,
    ) = ChatDraft(DraftContent.Text(text(text)), date.epochSecond)

    fun user(
        id: Long,
        firstName: String = "User $id",
        lastName: String = "",
        status: UserStatus = UserStatus.Empty,
        type: UserType = UserType.Regular,
        isVerified: Boolean = false,
        isScam: Boolean = false,
        isFake: Boolean = false,
    ) = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        usernames = persistentListOf(),
        phoneNumber = "",
        status = status,
        profilePhoto = null,
        isVerified = isVerified,
        isScam = isScam,
        isFake = isFake,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = type,
        haveAccess = true,
    )

    fun supergroup(
        id: Long,
        isChannel: Boolean = false,
        isVerified: Boolean = false,
        isScam: Boolean = false,
        isFake: Boolean = false,
    ) = Supergroup(
        id = id,
        usernames = persistentListOf(),
        memberCount = 0,
        status = ChatMemberStatus.Member,
        isChannel = isChannel,
        isForum = false,
        isVerified = isVerified,
        isScam = isScam,
        isFake = isFake,
    )

    fun folder(
        id: Int,
        title: String = "Folder $id",
    ) = ChatFolder(id, text(title), iconName = "Custom", colorId = null)
}

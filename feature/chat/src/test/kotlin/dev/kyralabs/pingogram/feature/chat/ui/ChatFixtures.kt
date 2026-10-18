package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/** Domain models for the conversation's tests, with defaults for everything a test does not care about. */
object ChatFixtures {
    /** The current user. */
    const val ME = 1L

    /** The other user of the private chat [PRIVATE_CHAT]. */
    const val ADA = 2L

    const val GRACE = 3L

    /** A private chat's id is its user's. */
    const val PRIVATE_CHAT = ADA

    const val GROUP_CHAT = -100L
    const val BASIC_GROUP_ID = 8L
    const val CHANNEL_CHAT = -1_000_000_000_200L
    const val SUPERGROUP_ID = 200L

    /** 2026-10-18T09:41:30Z, a Sunday. */
    val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")

    fun text(text: String) = FormattedText(text, persistentListOf())

    fun chat(
        id: Long = PRIVATE_CHAT,
        type: ChatType = ChatType.Private(userId = id),
        title: String = "Chat $id",
        unreadCount: Int = 0,
        lastReadInboxMessageId: Long = 0,
        lastReadOutboxMessageId: Long = 0,
    ) = Chat(
        id = id,
        type = type,
        title = title,
        photo = null,
        lastMessage = null,
        positions = persistentListOf(),
        unreadCount = unreadCount,
        unreadMentionCount = 0,
        unreadReactionCount = 0,
        lastReadInboxMessageId = lastReadInboxMessageId,
        lastReadOutboxMessageId = lastReadOutboxMessageId,
        isMarkedAsUnread = false,
        isMuted = false,
        draft = null,
        hasProtectedContent = false,
        messageAutoDeleteTime = 0,
    )

    val groupChat = chat(id = GROUP_CHAT, type = ChatType.BasicGroup(BASIC_GROUP_ID), title = "Group")

    val channelChat =
        chat(id = CHANNEL_CHAT, type = ChatType.Supergroup(SUPERGROUP_ID, isChannel = true), title = "Channel")

    @Suppress("LongParameterList") // Every field a test sets, all with defaults.
    fun message(
        id: Long,
        chatId: Long = PRIVATE_CHAT,
        sender: MessageSender = MessageSender.User(ADA),
        date: Instant = NOW,
        isOutgoing: Boolean = false,
        content: MessageContent = MessageContent.Text(text("Message $id"), linkPreview = null),
        sendingState: SendingState? = null,
        editDate: Long = 0,
        replyTo: ReplyTo? = null,
        forwardInfo: ForwardInfo? = null,
        interactionInfo: InteractionInfo? = null,
    ) = Message(
        id = id,
        chatId = chatId,
        sender = sender,
        date = date.epochSecond,
        editDate = editDate,
        isOutgoing = isOutgoing,
        sendingState = sendingState,
        isPinned = false,
        isChannelPost = false,
        containsUnreadMention = false,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = replyTo,
        forwardInfo = forwardInfo,
        interactionInfo = interactionInfo,
        selfDestruct = null,
        content = content,
    )

    /** A message of the current user. */
    fun outgoing(
        id: Long,
        chatId: Long = PRIVATE_CHAT,
        date: Instant = NOW,
        sendingState: SendingState? = null,
    ) = message(id, chatId, MessageSender.User(ME), date, isOutgoing = true, sendingState = sendingState)

    fun service(
        id: Long,
        action: ServiceAction,
        chatId: Long = GROUP_CHAT,
        sender: MessageSender = MessageSender.User(ADA),
        date: Instant = NOW,
    ) = message(id, chatId, sender, date, content = MessageContent.Service(action))

    /** A reply to message [messageId] of [chatId], without a quote. */
    fun replyTo(
        messageId: Long,
        chatId: Long = PRIVATE_CHAT,
    ) = ReplyTo.Message(chatId, messageId, quote = null, origin = null, originDate = 0, content = null)

    /** A history of [messages], in any order: sorted newest first, as the repository keeps it. */
    fun history(
        vararg messages: Message,
        reachedStart: Boolean = true,
    ) = ChatHistory(messages.sortedByDescending { it.id }.toImmutableList(), reachedStart)

    fun user(
        id: Long,
        firstName: String = "User $id",
        lastName: String = "",
        status: UserStatus = UserStatus.Empty,
        type: UserType = UserType.Regular,
    ) = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        usernames = persistentListOf(),
        phoneNumber = "",
        status = status,
        profilePhoto = null,
        isVerified = false,
        isScam = false,
        isFake = false,
        isPremium = false,
        isContact = false,
        isMutualContact = false,
        isSupport = false,
        type = type,
        haveAccess = true,
    )

    fun basicGroup(memberCount: Int) =
        BasicGroup(
            id = BASIC_GROUP_ID,
            memberCount = memberCount,
            status = ChatMemberStatus.Member,
            isActive = true,
            upgradedToSupergroupId = null,
        )

    fun supergroup(
        memberCount: Int,
        isChannel: Boolean = true,
    ) = Supergroup(
        id = SUPERGROUP_ID,
        usernames = persistentListOf(),
        memberCount = memberCount,
        status = ChatMemberStatus.Member,
        isChannel = isChannel,
        isForum = false,
        isVerified = false,
        isScam = false,
        isFake = false,
    )
}

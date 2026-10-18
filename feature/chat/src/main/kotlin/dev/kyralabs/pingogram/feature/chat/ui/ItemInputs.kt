package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.text.UiText

/**
 * Everything one item is made from: the message and its place, what it refers to (its sender, the members a service
 * message names, the replied message and its sender, the forward's origin, as the [User], [ChatRef] or null that was
 * looked up for it), and the chat's state it depends on. [toItem] looks up nothing else, so equal inputs make equal
 * items, and [ChatItemMapper] reuses the item.
 */
internal data class ItemInputs(
    val message: Message,
    val position: GroupPosition,
    val date: UiText,
    val kind: ChatKind,
    val sender: Any?,
    val members: List<User?>,
    val reply: ReplyInputs?,
    val forwardOrigin: Any?,
    val isRead: Boolean,
    val spoilersRevealed: Boolean,
    val myUserId: Long?,
    val timeFormatter: ChatTimeFormatter,
)

/** What kind of chat it is, as far as bubbles show: sender names and avatars in groups, no checks in channels. */
internal enum class ChatKind {
    Private,
    SavedMessages,
    Group,
    Channel,
}

/** The replied message, if loaded or fetched, and its sender's user or chat; the bundled origin's otherwise. */
internal data class ReplyInputs(
    val replyTo: ReplyTo,
    val replied: Message?,
    val state: ReplyState?,
    val repliedSender: Any?,
    val origin: Any?,
)

internal fun ItemSources.inputsOf(
    message: Message,
    position: GroupPosition,
    date: UiText,
): ItemInputs =
    ItemInputs(
        message = message,
        position = position,
        date = date,
        kind = chatKind,
        sender = refOf(message.sender),
        members = memberIdsOf(message.content).map(users::get),
        reply = message.replyTo?.let { replyInputs(message, it) },
        forwardOrigin = message.forwardInfo?.let { refOf(it.origin) },
        isRead = message.isOutgoing && message.id <= chat.lastReadOutboxMessageId,
        spoilersRevealed = message.id in messages.revealedSpoilers,
        myUserId = myUserId,
        timeFormatter = timeFormatter,
    )

private val ItemSources.chatKind: ChatKind
    get() =
        when {
            chat.isChannel -> ChatKind.Channel
            chat.isGroup -> ChatKind.Group
            chat.type is ChatType.Private && chat.userId == myUserId -> ChatKind.SavedMessages
            else -> ChatKind.Private
        }

/** The replied message from the history (same chat) or from the fetched ones, and who sent it. */
private fun ItemSources.replyInputs(
    message: Message,
    replyTo: ReplyTo,
): ReplyInputs {
    val replied =
        (replyTo as? ReplyTo.Message)
            ?.takeIf { it.chatId == chat.id }
            ?.let { history.messages.findById(it.messageId) }
    val state = if (replied == null) messages.replies[message.id] else null
    val repliedMessage = replied ?: (state as? ReplyState.Loaded)?.message
    return ReplyInputs(
        replyTo = replyTo,
        replied = replied,
        state = state,
        repliedSender = repliedMessage?.let { refOf(it.sender) },
        origin = (replyTo as? ReplyTo.Message)?.origin?.let(::refOf),
    )
}

/** The user or chat [sender] is: a [User], a [ChatRef], or null while it is not known. */
private fun ItemSources.refOf(sender: MessageSender): Any? =
    when (sender) {
        is MessageSender.User -> users[sender.userId]
        is MessageSender.Chat -> chatRefOf(sender.chatId)
    }

private fun ItemSources.refOf(origin: MessageOrigin): Any? =
    when (origin) {
        is MessageOrigin.User -> users[origin.userId]
        is MessageOrigin.HiddenUser -> null
        is MessageOrigin.Chat -> chatRefOf(origin.chatId)
        is MessageOrigin.Channel -> chatRefOf(origin.chatId)
    }

private fun ItemSources.chatRefOf(chatId: Long): ChatRef? =
    if (chatId == chat.id) ChatRef(chat.id, chat.title, chat.photo) else people.chats[chatId]

/** The users a service message names besides its sender, whose names its text may show. */
private fun memberIdsOf(content: MessageContent): List<Long> =
    when (val action = (content as? MessageContent.Service)?.action) {
        is ServiceAction.MembersAdded -> listOfNotNull(action.userIds.singleOrNull())
        is ServiceAction.MemberRemoved -> listOf(action.userId)
        else -> emptyList()
    }

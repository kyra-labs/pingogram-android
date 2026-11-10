package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.dates.hasRelativeDates

/**
 * Everything one item is made from: the message and its place, what it refers to (its sender, the members a service
 * message names, the replied message and its sender, the forward's origin, as the [User], [ChatRef] or null that was
 * looked up for it), and the chat's state it depends on. [toItem] looks up nothing else, so equal inputs make equal
 * items, and [ChatItemMapper] reuses the item.
 *
 * For an album, [message] is the item whose caption the bubble shows (the newest item when none has one) and [album]
 * holds every loaded item, oldest first; it is empty for any other message.
 */
internal data class ItemInputs(
    val message: Message,
    val album: List<Message>,
    val position: GroupPosition,
    val date: UiText,
    val kind: ChatKind,
    val sender: Any?,
    val members: List<User?>,
    val reply: ReplyInputs?,
    val forwardOrigin: Any?,
    val isRead: Boolean,
    val spoilersRevealed: Boolean,
    val timeShown: Boolean,
    val myUserId: Long?,
    val timeFormatter: ChatTimeFormatter,
    /** The sender's role label in a group ([ChatPeople.roles]); null for anyone else. */
    val senderRole: UiText? = null,
    /** The user's own usernames, lowercase and without `@`, which `@username` mentions of the user name. */
    val myUsernames: Set<String> = emptySet(),
    /**
     * The current minute (Unix time) for a text with dates worded relative to now ("in 3 days"), so the item is mapped
     * again each minute; null for every other text, whose item stays reused.
     */
    val relativeNow: Long? = null,
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
    unit: ItemUnit,
    position: GroupPosition,
    date: UiText,
): ItemInputs {
    val newest = unit.newest
    val message = if (unit.isAlbum) unit.messages.firstOrNull { it.content.hasCaption } ?: newest else newest
    return ItemInputs(
        message = message,
        album = if (unit.isAlbum) unit.messages.reversed() else emptyList(),
        position = position,
        date = date,
        kind = chatKind,
        sender = refOf(message.sender),
        members = memberIdsOf(message.content).map(users::get),
        reply = message.replyTo?.let { replyInputs(message, it) },
        forwardOrigin = message.forwardInfo?.let { refOf(it.origin) },
        isRead = newest.isOutgoing && newest.id <= chat.lastReadOutboxMessageId,
        spoilersRevealed = message.id in messages.revealedSpoilers,
        timeShown = message.id in messages.shownTimes,
        myUserId = myUserId,
        timeFormatter = timeFormatter,
        senderRole = people.roles[message.sender.id],
        myUsernames =
            myUserId
                ?.let(users::get)
                ?.usernames
                .orEmpty()
                .mapTo(HashSet()) { it.lowercase() },
        relativeNow =
            now?.takeIf { message.content.textOrCaption.hasRelativeDates }?.let {
                it.epochSecond / SECONDS_PER_MINUTE * SECONDS_PER_MINUTE
            },
    )
}

/** A photo, video or GIF with a caption, which an album's bubble shows. */
private val MessageContent.hasCaption: Boolean
    get() =
        when (this) {
            is MessageContent.Photo -> caption.text.isNotEmpty()
            is MessageContent.Video -> caption.text.isNotEmpty()
            is MessageContent.Animation -> caption.text.isNotEmpty()
            else -> false
        }

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
internal fun memberIdsOf(content: MessageContent): List<Long> =
    when (content) {
        is MessageContent.Service -> {
            when (val action = content.action) {
                is ServiceAction.MembersAdded -> listOfNotNull(action.userIds.singleOrNull())
                is ServiceAction.MemberRemoved -> listOf(action.userId)
                else -> emptyList()
            }
        }

        // Who ticked each task, for the avatars at their ends.
        is MessageContent.Checklist -> {
            content.list.tasks
                .mapNotNull { (it.completedBy as? MessageSender.User)?.userId }
                .distinct()
        }

        else -> {
            emptyList()
        }
    }

/**
 * Whether the message shows large, without a bubble: a text of only a few emoji ([isEmojiOnly]), regular or custom
 * ones, without other formatting, link preview, reply or forward, which a bubble would have to hold.
 */
internal fun ItemInputs.showsLargeEmoji(
    content: MessageContent,
    text: FormattedText,
): Boolean =
    content is MessageContent.Text && content.linkPreview == null && reply == null &&
        text.entities.all { it.type is TextEntityType.CustomEmoji } &&
        message.forwardInfo == null && isEmojiOnly(text.text)

/**
 * Whether the message shouts (`docs/DESIGN_SYSTEM.md` §13.6): an incoming group text of one word of at most
 * [SHOUT_MAX_LENGTH] characters, all its letters capitals and at least one of them, without formatting, link preview,
 * reply or forward ("LOL", "OK!"), shown large and italic.
 */
internal fun ItemInputs.isShout(
    content: MessageContent,
    text: FormattedText,
): Boolean {
    val value = text.text
    return kind == ChatKind.Group && !message.isOutgoing && content is MessageContent.Text &&
        content.linkPreview == null && reply == null && message.forwardInfo == null && text.entities.isEmpty() &&
        value.length in 1..SHOUT_MAX_LENGTH && value.none { it.isWhitespace() } &&
        value.any { it.isLetter() } && value == value.uppercase() && value != value.lowercase()
}

/**
 * The length of a channel post's headline (§13.7): its first line where that line is at most [HEADLINE_MAX_LENGTH]
 * characters, wholly bold, and more text follows it; 0 elsewhere.
 */
internal fun ItemInputs.headlineLength(text: FormattedText): Int {
    val end = text.text.indexOf('\n')
    val isHeadline =
        kind == ChatKind.Channel && end in 1..HEADLINE_MAX_LENGTH && text.text.substring(end).isNotBlank() &&
            text.entities.any { it.type == TextEntityType.Bold && it.offset <= 0 && it.offset + it.length >= end }
    return if (isHeadline) end else 0
}

private const val SHOUT_MAX_LENGTH = 6
private const val HEADLINE_MAX_LENGTH = 32

/**
 * Whether a group's service message [action] is someone else joining or being added, which the
 * "Wave" greets (§13.6); never the user's own joining.
 */
internal fun ItemInputs.canWave(action: ServiceAction): Boolean {
    val joined =
        when (action) {
            ServiceAction.MemberJoined, ServiceAction.MemberJoinedByLink, ServiceAction.MemberJoinedByRequest -> {
                listOf(message.sender.id)
            }

            is ServiceAction.MembersAdded -> {
                action.userIds
            }

            else -> {
                emptyList()
            }
        }
    return kind == ChatKind.Group && joined.isNotEmpty() && myUserId !in joined
}

private const val SECONDS_PER_MINUTE = 60L

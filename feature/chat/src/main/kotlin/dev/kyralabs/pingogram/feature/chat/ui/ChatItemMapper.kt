package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

/** The name and photo of a chat that messages refer to: a sender, a forward's origin, a replied message's sender. */
internal data class ChatRef(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "ChatRef(id=$id, …)"
}

/** Where the replied message of a message is, when it is not in the loaded history. */
internal sealed interface ReplyState {
    /** Asked for, no answer yet. */
    data object Pending : ReplyState

    data class Loaded(
        val message: Message,
    ) : ReplyState

    /** It could not be fetched: [DomainError.NotFound] when it was deleted. Not asked for again. */
    data class Failed(
        val error: DomainError,
    ) : ReplyState
}

/**
 * The users and chats the header and the messages name, as of one emission: every user, the current user, and the
 * chats messages refer to besides the conversation's own chat, by id.
 */
internal class ChatPeople(
    val users: Map<Long, User>,
    val myUserId: Long?,
    val chats: Map<Long, ChatRef>,
)

/**
 * The messages of one emission: the loaded [history], the replied messages fetched for messages whose replied message
 * is not loaded ([replies], by the id of the replying message), and the messages whose spoilers were revealed.
 */
internal class MessageState(
    val history: ChatHistory,
    val replies: Map<Long, ReplyState>,
    val revealedSpoilers: Set<Long>,
)

/**
 * What the items of one emission are made from.
 *
 * @property unreadAfterId the last message read when the chat was opened: the unread divider goes before the first
 *   incoming message after it; [NO_UNREAD_DIVIDER] for none.
 */
internal class ItemSources(
    val chat: Chat,
    val messages: MessageState,
    val people: ChatPeople,
    val unreadAfterId: Long,
    val today: LocalDate,
    val timeFormatter: ChatTimeFormatter,
) {
    val history: ChatHistory get() = messages.history
    val users: Map<Long, User> get() = people.users
    val myUserId: Long? get() = people.myUserId
}

/** [ItemSources.unreadAfterId] when the chat had nothing unread when it was opened. */
internal const val NO_UNREAD_DIVIDER = Long.MAX_VALUE

/** How far apart (in seconds) two messages of one sender may be to share a group. */
internal const val GROUP_GAP_SECONDS = 5 * 60L

/**
 * Maps a chat's history to [ChatItem]s, newest first (`REQUIREMENTS.md` FR-C1–C2, FR-C4): bubbles grouped by sender,
 * service messages, a date separator above the first message of each day, and the unread divider above the first
 * message that was unread when the chat was opened. What each item shows is mapped in `BubbleMapping.kt`.
 *
 * Consecutive photos and videos with the same `mediaAlbumId` are one bubble ([ChatItem.Bubble.album]), keyed by the
 * album, so it stays one item as older pages bring in more of it ([itemUnits]); for grouping, dates and the divider
 * the album counts as one message from its oldest to its newest item.
 *
 * A message is mapped again only when something it shows changed (`docs/PERFORMANCE.md` §4): the previous item is
 * reused when its inputs ([ItemInputs]) are equal, compared mostly by identity, as the repositories keep unchanged
 * objects. User statuses change many times a minute, and each change emits the whole history again.
 *
 * Not thread-safe: one flow collection uses it at a time.
 */
internal class ChatItemMapper {
    private var previous: Map<Any, MappedItem> = emptyMap()

    fun map(sources: ItemSources): ImmutableList<ChatItem> {
        val messages = sources.history.messages
        if (messages.isEmpty()) {
            previous = emptyMap()
            return persistentListOf()
        }
        val units = itemUnits(messages)
        val formatter = sources.timeFormatter
        val days = units.map { formatter.day(it.newest.date) }
        val dateLabels = HashMap<LocalDate, UiText>()
        // An unread album starts at its oldest item, where the divider goes.
        val firstUnreadId = sources.firstUnreadId()?.let { id -> units.firstOrNull { it.contains(id) }?.oldest?.id }
        val mapped = HashMap<Any, MappedItem>(units.size * 2)
        val items = ArrayList<ChatItem>(units.size + units.size / 2)
        for (index in units.indices) {
            val unit = units[index]
            val day = days[index]
            val date = dateLabels.getOrPut(day) { formatter.dateLabel(day, sources.today) }
            val older = units.getOrNull(index + 1)
            val newer = units.getOrNull(index - 1)
            val position =
                groupPosition(
                    joinsPrevious =
                        older != null && joins(older.newest, days[index + 1], unit.oldest, day, firstUnreadId),
                    joinsNext = newer != null && joins(unit.newest, day, newer.oldest, days[index - 1], firstUnreadId),
                )
            val inputs = sources.inputsOf(unit, position, date)
            val item = previous[unit.key]?.takeIf { it.inputs == inputs } ?: MappedItem(inputs, inputs.toItem())
            mapped[unit.key] = item
            items += item.item
            if (unit.oldest.id == firstUnreadId) items += ChatItem.UnreadDivider(date)
            if (older == null || days[index + 1] != day) items += ChatItem.DateSeparator(day.toEpochDay(), date)
        }
        previous = mapped
        return items.toImmutableList()
    }

    private class MappedItem(
        val inputs: ItemInputs,
        val item: ChatItem,
    )
}

/**
 * The messages of one item, newest first: one message, or the loaded photos and videos of an album. [key] is the
 * message's id, or the album's for an album, also while only one of its items is loaded.
 */
internal class ItemUnit(
    val messages: List<Message>,
) {
    val newest: Message get() = messages.first()
    val oldest: Message get() = messages.last()
    val isAlbum: Boolean get() = newest.isAlbumItem
    val key: Any get() = if (isAlbum) albumKey(newest.mediaAlbumId) else newest.id

    fun contains(messageId: Long): Boolean = messages.any { it.id == messageId }
}

/** The list key of the album [mediaAlbumId]'s bubble ([ChatItem.Bubble.key]). */
internal fun albumKey(mediaAlbumId: Long): String = "album:$mediaAlbumId"

/**
 * [messages] (newest first) in items: consecutive photos and videos of one album together, every other message on
 * its own. Telegram also groups documents and songs in albums; those stay separate bubbles.
 */
internal fun itemUnits(messages: List<Message>): List<ItemUnit> {
    val units = ArrayList<ItemUnit>(messages.size)
    var album = ArrayList<Message>()
    for (message in messages) {
        if (album.isNotEmpty() && (!message.isAlbumItem || message.mediaAlbumId != album.first().mediaAlbumId)) {
            units += ItemUnit(album)
            album = ArrayList()
        }
        if (message.isAlbumItem) album += message else units += ItemUnit(listOf(message))
    }
    if (album.isNotEmpty()) units += ItemUnit(album)
    return units
}

/** A photo or video of an album, which shares a bubble with the album's other items. */
private val Message.isAlbumItem: Boolean
    get() =
        mediaAlbumId != 0L &&
            (content is MessageContent.Photo || content is MessageContent.Video || content is MessageContent.Animation)

/** The oldest loaded incoming message after [ItemSources.unreadAfterId]; null if none is loaded. */
private fun ItemSources.firstUnreadId(): Long? {
    if (unreadAfterId == NO_UNREAD_DIVIDER) return null
    // Newest first, so the last match is the oldest.
    return history.messages
        .lastOrNull { !it.isOutgoing && it.id > unreadAfterId && it.content !is MessageContent.Service }
        ?.id
}

/**
 * Whether [newer] continues the group of [older], the message before it: both bubbles, from the same sender, on the
 * same day, at most [GROUP_GAP_SECONDS] apart, and no unread divider between them.
 */
private fun joins(
    older: Message,
    olderDay: LocalDate,
    newer: Message,
    newerDay: LocalDate,
    firstUnreadId: Long?,
): Boolean =
    older.content !is MessageContent.Service &&
        newer.content !is MessageContent.Service &&
        older.sender == newer.sender &&
        older.isOutgoing == newer.isOutgoing &&
        olderDay == newerDay &&
        newer.date - older.date <= GROUP_GAP_SECONDS &&
        newer.id != firstUnreadId

private fun groupPosition(
    joinsPrevious: Boolean,
    joinsNext: Boolean,
): GroupPosition =
    when {
        joinsPrevious && joinsNext -> GroupPosition.Middle
        joinsPrevious -> GroupPosition.Last
        joinsNext -> GroupPosition.First
        else -> GroupPosition.Single
    }

/**
 * The chats other than [chatId] that the items of [history] name, and so need looked up: senders that are chats, the
 * origins of forwards and of replies to other chats, and the senders of replied messages ([replies] among them).
 */
internal fun referencedChatIds(
    chatId: Long,
    history: ChatHistory,
    replies: Map<Long, ReplyState>,
): Set<Long> =
    buildSet {
        fun addSender(sender: MessageSender) {
            if (sender is MessageSender.Chat) add(sender.chatId)
        }

        fun addOrigin(origin: MessageOrigin?) {
            when (origin) {
                is MessageOrigin.Chat -> add(origin.chatId)
                is MessageOrigin.Channel -> add(origin.chatId)
                else -> Unit
            }
        }
        for (message in history.messages) {
            addSender(message.sender)
            addOrigin(message.forwardInfo?.origin)
            val replyTo = message.replyTo as? ReplyTo.Message
            addOrigin(replyTo?.origin)
            replyTo?.let { history.messages.findById(it.messageId) }?.let { addSender(it.sender) }
        }
        replies.values.forEach { (it as? ReplyState.Loaded)?.let { loaded -> addSender(loaded.message.sender) } }
        remove(chatId)
    }

/** Message [id] of [this] (newest first, by id), or null. */
internal fun List<Message>.findById(id: Long): Message? = getOrNull(binarySearch { id.compareTo(it.id) })

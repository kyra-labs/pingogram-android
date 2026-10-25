package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A topic of a forum supergroup (`REQUIREMENTS.md` FR-C9, td_api.tl `forumTopic`), for the forum's topic list and the
 * conversation of the topic. Plain data, mapped from TDLib by `core:data`; `toString()` leaves the name and the last
 * message out.
 */
data class ForumTopic(
    val chatId: Long,
    val id: Int,
    val name: String,
    val icon: ForumTopicIcon,
    /** The General topic, where messages without a topic go; it cannot be closed for good or deleted. */
    val isGeneral: Boolean,
    /** Only administrators can write in it. */
    val isClosed: Boolean,
    /** A General topic hidden from the list by the administrators. */
    val isHidden: Boolean,
    val isPinned: Boolean,
    /** Null if the topic has no messages, or TDLib does not know its last one. */
    val lastMessage: Message?,
    /** Where TDLib orders the topic in the forum's list: higher first. */
    val order: Long,
    val unreadCount: Int,
    val unreadMentionCount: Int,
    val lastReadInboxMessageId: Long,
    val lastReadOutboxMessageId: Long,
    /** The current user created the topic, and may edit, close and reopen it without administrator rights. */
    val isOutgoing: Boolean = false,
) {
    /** Where messages of the topic are. */
    val topic: MessageTopic.Forum get() = MessageTopic.Forum(id)

    override fun toString() = "ForumTopic(chatId=$chatId, id=$id, unreadCount=$unreadCount, isClosed=$isClosed, …)"
}

/**
 * A topic's icon: the custom emoji [customEmojiId] (0 for none), else a circle in [color], one of the six colors
 * Telegram gives topics, which the app shows in its own color roles.
 */
data class ForumTopicIcon(
    val color: TopicColor,
    val customEmojiId: Long,
)

/**
 * The colors Telegram gives topic icons (td_api.tl `forumTopicIcon.color`), by the hue they have there; a new topic
 * takes one of them, and keeps it: td_api.tl `editForumTopic` changes the name only.
 */
enum class TopicColor {
    Blue,
    Yellow,
    Violet,
    Green,
    Rose,
    Red,
}

/**
 * The topics of a forum loaded so far, in the forum's order (General first, then pinned topics, then the others by
 * their newest message); [reachedEnd] once every topic is loaded. A General topic the administrators hid is in it
 * too ([ForumTopic.isHidden]): the list shows it only to those who may show it again.
 */
data class ForumTopicList(
    val topics: ImmutableList<ForumTopic>,
    val reachedEnd: Boolean,
)

/** What Telegram accepts for a topic (td_api.tl `createForumTopic`, `editForumTopic`). */
object ForumTopicLimits {
    /** The longest name, in characters (TDLib's `MAX_FORUM_TOPIC_TITLE_LENGTH`). */
    const val MAX_NAME_LENGTH = 128
}

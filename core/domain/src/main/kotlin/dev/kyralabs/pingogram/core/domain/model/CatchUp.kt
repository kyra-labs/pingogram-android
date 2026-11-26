package dev.kyralabs.pingogram.core.domain.model

/**
 * [chats] with unread messages, without [locked] ones and, unless [includeMuted], without muted chats and channels;
 * those that mention the user first, then by their last message, the newest first. Catch-up goes through them in this
 * order (owner decisions 2026-11-15), and the Daily digest (`REQUIREMENTS.md` FR-X23) counts and names the same.
 */
fun catchUpOrder(
    chats: List<Chat>,
    includeMuted: Boolean,
    locked: Set<Long>,
): List<Chat> =
    chats
        .filter { it.isToCatchUp(includeMuted, locked) }
        .sortedWith(
            compareByDescending<Chat> { it.unreadMentionCount > 0 }.thenByDescending { it.lastMessage?.date ?: 0L },
        )

/** Whether this chat is one to catch up on: unread, not [locked], and not muted nor a channel unless [includeMuted]. */
fun Chat.isToCatchUp(
    includeMuted: Boolean,
    locked: Set<Long>,
): Boolean =
    unreadCount > 0 &&
        id !in locked &&
        (includeMuted || (!isMuted && (type as? ChatType.Supergroup)?.isChannel != true))

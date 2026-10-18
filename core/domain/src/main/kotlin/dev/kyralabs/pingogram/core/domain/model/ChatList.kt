package dev.kyralabs.pingogram.core.domain.model

/** A list of chats (TDLib's `ChatList`): the main list, the archive, or a chat folder (`REQUIREMENTS.md` FR-L1–L3). */
sealed interface ChatListType {
    data object Main : ChatListType

    data object Archive : ChatListType

    /** The chats of [ChatFolder] [id]. */
    data class Folder(
        val id: Int,
    ) : ChatListType
}

/**
 * Where a chat is in [list]. Chats are shown sorted by `(order, chat id)`, both descending; pinned chats have the
 * highest orders, so they come first.
 */
data class ChatPosition(
    val list: ChatListType,
    val order: Long,
    val isPinned: Boolean,
)

/**
 * A chat folder the user created (TDLib's `chatFolderInfo`), shown as a tab above the chat list (`REQUIREMENTS.md`
 * FR-L3); its chats are the list [ChatListType.Folder] with this [id].
 *
 * `toString()` leaves the title out.
 */
data class ChatFolder(
    val id: Int,
    /** 1–12 characters; its only entities are custom emoji. */
    val title: FormattedText,
    /**
     * TDLib's name of the folder's icon, chosen or derived from its chats: "All", "Unread", "Unmuted", "Bots",
     * "Channels", "Groups", "Private", "Custom", "Setup", "Cat", "Crown", "Favorite", "Flower", "Game", "Home", "Love",
     * "Mask", "Party", "Sport", "Study", "Trade", "Travel", "Work", "Airplane", "Book", "Light", "Like", "Money",
     * "Note" or "Palette".
     */
    val iconName: String,
    /** Telegram's color for the folder's tag, 0–6; null if none. */
    val colorId: Int?,
) {
    override fun toString() = "ChatFolder(id=$id, iconName=$iconName, colorId=$colorId, …)"
}

/**
 * How many chats of a list are unread (with unread messages or marked as unread), for the badges of folder tabs and
 * the chats destination. "Unmuted" counts leave muted chats out.
 */
data class UnreadChatCount(
    /** All chats in the list; an estimate. */
    val totalCount: Int,
    val unreadCount: Int,
    val unreadUnmutedCount: Int,
    val markedAsUnreadCount: Int,
    val markedAsUnreadUnmutedCount: Int,
) {
    companion object {
        /** Before TDLib counted the list. */
        val None = UnreadChatCount(0, 0, 0, 0, 0)
    }
}

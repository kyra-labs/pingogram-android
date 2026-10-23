package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A chat as TDLib knows it (`ChatRepository`): what the chat list and the conversation header show
 * (`REQUIREMENTS.md` FR-L1–L2, FR-C8). Plain data, mapped from TDLib's `chat` by `core:data`; a new snapshot replaces
 * it on every change, and snapshots of the same state are equal (`docs/PERFORMANCE.md` §3–4).
 *
 * Badges, online dots and names of private chats come from the [User] in [ChatType.Private]; this holds what TDLib
 * reports for the chat itself. Times are Unix timestamps in seconds.
 *
 * `toString()` leaves the title, the last message and the draft out, so a chat can be logged.
 */
data class Chat(
    val id: Long,
    val type: ChatType,
    /** For private and secret chats, the other user's name as the current user sees it. */
    val title: String,
    /** Null if the chat has no photo. */
    val photo: ProfilePhoto?,
    /** Null if the chat has no messages, or TDLib does not know its last message yet. */
    val lastMessage: Message?,
    /** The chat lists the chat is shown in, with its place in each ([positionIn]). */
    val positions: ImmutableList<ChatPosition>,
    /** Unread incoming messages. */
    val unreadCount: Int,
    /** Unread messages that mention or reply to the current user. */
    val unreadMentionCount: Int,
    /** Messages with reactions the current user has not seen. */
    val unreadReactionCount: Int,
    /** The last incoming message the current user read. */
    val lastReadInboxMessageId: Long,
    /** The last outgoing message the other side read: messages up to it get the "read" checkmarks. */
    val lastReadOutboxMessageId: Long,
    /** The user marked the chat as unread; shown like unread messages without a count. */
    val isMarkedAsUnread: Boolean,
    /**
     * No notifications: the chat's own setting, or, where it has none, the default for its kind of chat (private,
     * group, channel).
     */
    val isMuted: Boolean,
    /** A message the user started writing and did not send; null if none. */
    val draft: ChatDraft?,
    /** Messages cannot be saved, forwarded or copied. */
    val hasProtectedContent: Boolean,
    /**
     * Seconds after which new messages are deleted (in secret chats: after they were opened); 0 if they are not.
     */
    val messageAutoDeleteTime: Int,
    /**
     * What members without administrator rights may do; in a private or secret chat, what the current user may (TDLib
     * restricts everything in the read-only service chats).
     */
    val permissions: ChatPermissions,
    /**
     * Who new messages in the chat are sent as, which the user can change (send-as, `REQUIREMENTS.md` FR-C5): the
     * user, a channel they own, or the group itself for an anonymous admin. Null where the user cannot choose.
     */
    val messageSender: MessageSender? = null,
    /**
     * The message whose reply markup is in effect in the chat for the current user (td_api.tl
     * `chat.reply_markup_message_id`, `updateChatReplyMarkup`): a bot's keyboard (`ReplyMarkup.ShowKeyboard`) or its
     * request for a reply (`ReplyMarkup.ForceReply`); 0 for none. TDLib applies the markups' personal flags and
     * removals itself.
     */
    val replyMarkupMessageId: Long = 0,
) {
    /** The chat's position in [list]; null if the chat is not shown there. */
    fun positionIn(list: ChatListType): ChatPosition? = positions.firstOrNull { it.list == list }

    override fun toString() = "Chat(id=$id, type=$type, unreadCount=$unreadCount, isMuted=$isMuted, …)"
}

/** What kind of chat a [Chat] is, with the ids of what it is about. */
sealed interface ChatType {
    /** A one-to-one chat with user [userId]; also the current user's Saved Messages. */
    data class Private(
        val userId: Long,
    ) : ChatType

    /** An end-to-end encrypted chat with user [userId] on this device only. */
    data class Secret(
        val secretChatId: Int,
        val userId: Long,
    ) : ChatType

    /** A small group. */
    data class BasicGroup(
        val basicGroupId: Long,
    ) : ChatType

    /** A large group, or a channel if [isChannel]. */
    data class Supergroup(
        val supergroupId: Long,
        val isChannel: Boolean,
    ) : ChatType
}

/** What members without administrator rights may do in a chat (td_api.tl `chatPermissions`), as far as it is used. */
data class ChatPermissions(
    /** Send text messages (and contacts, locations, venues). */
    val canSendBasicMessages: Boolean,
) {
    companion object {
        /** Everything allowed, as in a private chat with a regular user. */
        val All = ChatPermissions(canSendBasicMessages = true)

        /** Nothing allowed. */
        val None = ChatPermissions(canSendBasicMessages = false)
    }
}

/**
 * An unsent message the user left in a chat, saved by Telegram on every device; [date] is when it was saved.
 * [replyToMessageId] is the message of the same chat it replies to; null if it replies to nothing. A draft may be only
 * a reply, with an empty text.
 */
data class ChatDraft(
    val content: DraftContent,
    val date: Long,
    val replyToMessageId: Long? = null,
)

/** What a [ChatDraft] holds. */
sealed interface DraftContent {
    data class Text(
        val text: FormattedText,
    ) : DraftContent

    /** A recorded voice note, not sent yet. */
    data object VoiceNote : DraftContent

    /** A recorded video note, not sent yet. */
    data object VideoNote : DraftContent

    /** Content Pingogram does not show yet; the chat list still marks the draft. */
    data object Unsupported : DraftContent
}

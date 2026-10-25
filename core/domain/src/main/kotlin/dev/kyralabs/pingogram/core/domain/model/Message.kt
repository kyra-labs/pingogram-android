package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration

/**
 * A message in a chat, for the conversation screen and the chat list preview (`REQUIREMENTS.md` FR-C1–C4). Plain data,
 * mapped from TDLib's `message` by `core:data`; a new snapshot replaces it on every change, and snapshots of the same
 * state are equal, so `StateFlow`s skip them (`docs/PERFORMANCE.md` §3–4).
 *
 * Times are Unix timestamps in seconds.
 */
data class Message(
    val id: Long,
    val chatId: Long,
    val sender: MessageSender,
    val date: Long,
    /** When the message was last edited; 0 if it never was. */
    val editDate: Long,
    val isOutgoing: Boolean,
    /** Null once Telegram has the message; only outgoing messages are ever pending or failed. */
    val sendingState: SendingState?,
    val isPinned: Boolean,
    /** A post in a channel: every message in a channel is one, no other message is. */
    val isChannelPost: Boolean,
    /** The message mentions the user, who has not seen it yet. */
    val containsUnreadMention: Boolean,
    /** Consecutive messages with the same non-zero id form an album, shown as one grid; 0 if not in an album. */
    val mediaAlbumId: Long,
    /** The author of a channel post, or the custom title of an anonymous group admin; empty if none. */
    val authorSignature: String,
    /** The bot the message was sent through (inline mode); 0 if none. */
    val viaBotUserId: Long,
    val replyTo: ReplyTo?,
    /** Where a forwarded message comes from; null if it was not forwarded. */
    val forwardInfo: ForwardInfo?,
    /** Views, forwards, comments and reactions; null if there are none. */
    val interactionInfo: InteractionInfo?,
    /** Null if the message does not self-destruct once opened. */
    val selfDestruct: SelfDestruct?,
    val content: MessageContent,
    /** The forum topic or message thread the message is in; null for neither. */
    val topic: MessageTopic? = null,
    /** The buttons a bot attached to the message, current with every edit; null if there are none. */
    val replyMarkup: ReplyMarkup? = null,
)

/** Who sent a message: a user, or a chat (a channel, or a group's anonymous admins). */
sealed interface MessageSender {
    data class User(
        val userId: Long,
    ) : MessageSender

    data class Chat(
        val chatId: Long,
    ) : MessageSender
}

/** Where an outgoing message is on its way to Telegram. */
sealed interface SendingState {
    data object Pending : SendingState

    /**
     * Sending failed because of [error]. If [canRetry], the message can be sent again, after [retryAfter] when that is
     * not null; it counts from when this state was received.
     */
    data class Failed(
        val error: DomainError,
        val canRetry: Boolean,
        val retryAfter: Duration?,
    ) : SendingState
}

/** What a message replies to. */
sealed interface ReplyTo {
    /**
     * A reply to message [messageId] in chat [chatId]; both are 0 if that chat is unknown. [quote] is the part of the
     * replied message the sender quoted. For a message in another chat or forum topic, [origin] is its sender,
     * [originDate] when it was sent, and [content] its media (TDLib sends no media for text messages, and none of this
     * for the same chat, where the replied message itself is loaded).
     */
    data class Message(
        val chatId: Long,
        val messageId: Long,
        val quote: FormattedText?,
        val origin: MessageOrigin?,
        val originDate: Long,
        val content: MessageContent?,
    ) : ReplyTo

    data class Story(
        val storyPosterChatId: Long,
        val storyId: Int,
    ) : ReplyTo
}

/** Where a forwarded message comes from, and when it was originally sent. */
data class ForwardInfo(
    val origin: MessageOrigin,
    val date: Long,
)

/** Who originally sent a forwarded or replied message. */
sealed interface MessageOrigin {
    data class User(
        val userId: Long,
    ) : MessageOrigin

    /** A user whose privacy settings hide their account; only the [name] is known, and may be empty. */
    data class HiddenUser(
        val name: String,
    ) : MessageOrigin {
        override fun toString() = "HiddenUser(…)"
    }

    /** A chat: a group's anonymous admin, who may have signed with [authorSignature] (empty if not). */
    data class Chat(
        val chatId: Long,
        val authorSignature: String,
    ) : MessageOrigin

    /** Post [messageId] in channel [chatId], signed with [authorSignature] (empty if not). */
    data class Channel(
        val chatId: Long,
        val messageId: Long,
        val authorSignature: String,
    ) : MessageOrigin
}

/** How people interacted with a message. */
data class InteractionInfo(
    val viewCount: Int,
    val forwardCount: Int,
    /**
     * Comments or replies in the message's thread; null if it has no thread (only channel posts with a discussion
     * group and messages in such a group have one).
     */
    val replyCount: Int?,
    /** Reactions in TDLib's order; empty if there are none. */
    val reactions: ImmutableList<Reaction>,
    /**
     * Who added which reaction can be listed ("Reactions", `MessageInteractionRepository.addedReactions`); TDLib says
     * so for groups, not for channels, where reactions are anonymous.
     */
    val canGetAddedReactions: Boolean = false,
)

/** A reaction added [totalCount] times, [isChosen] if the user added it too. */
data class Reaction(
    val type: ReactionType,
    val totalCount: Int,
    val isChosen: Boolean,
)

sealed interface ReactionType {
    data class Emoji(
        val emoji: String,
    ) : ReactionType

    data class CustomEmoji(
        val customEmojiId: Long,
    ) : ReactionType

    /** The paid star reaction of channels. */
    data object Paid : ReactionType
}

/** When a message self-destructs (secret chats, view-once media). */
sealed interface SelfDestruct {
    /** As soon as it was opened once. */
    data object Immediately : SelfDestruct

    /** [seconds] after it was opened. */
    data class Timer(
        val seconds: Int,
    ) : SelfDestruct
}

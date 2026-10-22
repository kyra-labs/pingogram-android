package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreview
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotIntro
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedBar
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * Everything the conversation screen shows (`REQUIREMENTS.md` FR-C1–C3, FR-C5, FR-C8): the header, the messages and
 * the composer.
 *
 * @property header the top bar's chat; null until the chat is known, and for a chat that cannot be found.
 * @property content the messages, or why there are none.
 * @property unreadCount unread incoming messages, for the badge of the jump-to-bottom button; 0 shows none.
 * @property fileStates how far the files of the loaded voice messages, songs and documents are downloaded, by file id
 *   ([downloadableFileId]); a file TDLib has not reported yet is missing.
 * @property playback the voice message or song in the player; null when none is.
 * @property composer what the bottom of the screen shows: the composer, why the user cannot write, or nothing.
 * @property messageActions the actions sheet of a long-pressed message; null while none is open.
 * @property deleteDialog the confirmation before messages are deleted; null while none is shown.
 * @property shortcuts which actions screen readers get on every bubble, besides the sheet.
 * @property uploads the uploads of the photos, videos and files being sent from this device, by message id (each item
 *   of an album by its own); messages not being sent are missing.
 * @property attachPreview the picked photos, videos or files before they are sent; null while there is no preview.
 * @property poll the form of a new poll; null while it is closed.
 * @property sponsoredSheet "About this ad" of a sponsored message, with its report flow; null while it is closed.
 * @property selection selection mode: the selected messages and the toolbar's actions; null outside it.
 * @property report the sheet that reports messages; null while it is closed.
 * @property mediaDownloads whether the photos in the bubbles download by themselves on the current network, and the
 *   ones the user asked to download meanwhile.
 * @property pinnedBar the pinned messages bar over the messages (`REQUIREMENTS.md` FR-C9); null while there is none.
 * @property botIntro what a private chat's bot shows while the chat is empty (`REQUIREMENTS.md` FR-C5); null for other
 *   chats, and bots that show nothing.
 */
@Immutable
data class ChatUiState(
    val header: ChatHeader? = null,
    val content: ChatContent = ChatContent.Loading,
    val unreadCount: Int = 0,
    val fileStates: ImmutableMap<Int, FileState> = persistentMapOf(),
    val playback: MessagePlayback? = null,
    val composer: ComposerUiState = ComposerUiState.Hidden,
    val messageActions: MessageActionsSheet? = null,
    val deleteDialog: DeleteMessagesDialog? = null,
    val shortcuts: MessageShortcuts = MessageShortcuts(),
    val uploads: ImmutableMap<Long, MessageUpload> = persistentMapOf(),
    val attachPreview: AttachPreview? = null,
    val poll: PollSheet? = null,
    val sponsoredSheet: SponsoredSheet? = null,
    val selection: MessageSelection? = null,
    val report: ReportSheet? = null,
    val mediaDownloads: MediaDownloads = MediaDownloads(),
    val pinnedBar: PinnedBar? = null,
    val botIntro: BotIntro? = null,
)

/**
 * How the photos in the bubbles download (`docs/PERFORMANCE.md` §7): by themselves while the automatic download rules
 * of the current network allow [photos]; otherwise each shows a download button, and those in [requested] (by file id)
 * a download in progress, until they show.
 */
@Immutable
data class MediaDownloads(
    val photos: Boolean = true,
    val requested: ImmutableSet<Int> = persistentSetOf(),
)

/**
 * Message [messageId] in the player, playing or paused as [playback] says; every other message shows
 * [Playback.Stopped]. It changes only when the message or whether it plays does: the progress is snapshot state that
 * [Playback.progress] reads, so the position moving re-emits nothing.
 */
@Stable
data class MessagePlayback(
    val messageId: Long,
    val playback: Playback,
)

/**
 * The chat in the top bar: avatar, name and what it is doing.
 *
 * @property chatId the avatar's id, which picks its placeholder.
 * @property title the chat's name; "Saved Messages" and "Deleted Account" come from resources.
 * @property photo the avatar's photo; null shows the placeholder.
 * @property placeholderIcon a Material Symbol on the avatar placeholder instead of initials: the bookmark of Saved
 *   Messages, the person of a deleted account.
 * @property subtitle the second line; null shows none (Saved Messages, a deleted account).
 * @property profileUserId the user whose profile the title opens: the other side of a private or secret chat; null
 *   for groups, channels and Saved Messages.
 * @property opensChatInfo the title opens the chat's info page: basic groups, supergroups and channels.
 * @property secret a secret chat's lock, state and self-destruct timer (`REQUIREMENTS.md` FR-E1); null for other
 *   chats.
 */
@Immutable
data class ChatHeader(
    val chatId: Long,
    val title: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val subtitle: ChatSubtitle?,
    val profileUserId: Long? = null,
    val secret: SecretChatHeader? = null,
    val opensChatInfo: Boolean = false,
) {
    override fun toString() = "ChatHeader(chatId=$chatId, subtitle=$subtitle, secret=$secret, …)"
}

/** The second line of the top bar. */
@Immutable
sealed interface ChatSubtitle {
    val text: UiText

    /** "online", "last seen …", "bot", "12 members", "1,024 subscribers". */
    data class Status(
        override val text: UiText,
    ) : ChatSubtitle

    /** Someone is doing something right now ("typing…"), which replaces the status; shown in `primary`. */
    data class Action(
        override val text: UiText,
    ) : ChatSubtitle
}

/** The conversation itself. */
@Immutable
sealed interface ChatContent {
    /** The chat or its first messages are on their way. */
    data object Loading : ChatContent

    /** The chat has no messages. */
    data object Empty : ChatContent

    /** Finding the chat or loading its first messages failed with [error]: shown with a retry ([ChatEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : ChatContent

    /** There is no such chat, or the user has no access to it (any more). */
    data object NotFound : ChatContent

    /**
     * The messages loaded so far, newest first, as the reverse layout shows them ([ChatItem]). More load near the
     * oldest one until [reachedStart], and near the newest one until [reachedEnd].
     *
     * @property isLoadingOlder older messages are on their way, shown with a progress indicator above the oldest.
     * @property olderError loading older messages failed; shown above the oldest with a retry ([ChatEvent.Retry]).
     * @property reachedEnd the items go up to the chat's newest message; false while the conversation shows older
     *   messages loaded around the first unread or a message jumped to, until newer pages reach it.
     * @property isLoadingNewer newer messages are on their way, shown with a progress indicator below the newest.
     * @property newerError loading newer messages failed; shown below the newest with a retry ([ChatEvent.Retry]).
     * @property anchor where the list shows these items: it moves there once per anchor ([ListAnchor.id]).
     */
    data class Content(
        val items: ImmutableList<ChatItem>,
        val reachedStart: Boolean,
        val isLoadingOlder: Boolean,
        val olderError: DomainError?,
        val reachedEnd: Boolean = true,
        val isLoadingNewer: Boolean = false,
        val newerError: DomainError? = null,
        val anchor: ListAnchor = ListAnchor(),
    ) : ChatContent
}

/**
 * Where the message list goes, once, when the conversation asks for it: when it opens (at the unread divider, a found
 * message or the newest message), at a jump to a message (a tapped quote), and at the jump to the newest message. The
 * list moves to [target] the first time it shows an anchor with a new [id]; it keeps the last id it took across
 * rotations and process death, as the view model keeps its anchor, so neither moves the list again.
 */
@Immutable
data class ListAnchor(
    val id: Int = 0,
    val target: AnchorTarget = AnchorTarget.Unread,
)

/** What a [ListAnchor] shows. */
@Immutable
sealed interface AnchorTarget {
    /** The unread divider near the top, with the unread messages below it, if the items have one; else [Newest]. */
    data object Unread : AnchorTarget

    /** The newest message, at the bottom. */
    data object Newest : AnchorTarget

    /**
     * Message [messageId] in the middle, highlighted for a while if [highlight]; where it would be (the next older
     * message) when it is not among the items.
     */
    data class Message(
        val messageId: Long,
        val highlight: Boolean = true,
    ) : AnchorTarget
}

/**
 * One entry of the message list, mapped once in the ViewModel (`docs/PERFORMANCE.md` §4): the list only resolves
 * texts. Equal items are equal, and items whose message did not change are reused as they are.
 */
@Immutable
sealed interface ChatItem {
    /** The list's key: the message id, or a string for the other entries. Saveable in a `Bundle`. */
    val key: Any

    /** The day the item belongs to ("Today", "March 17"), for the date pill that floats over the list. */
    val date: UiText

    /**
     * A message in a bubble. Grouped messages (same sender, a few minutes apart, the same day) share a stack: the
     * name on the first, the avatar, the time and the state on the last ([position]).
     *
     * @property sender who sent it, for screen readers ("You" for outgoing messages); shown as the name on the first
     *   bubble of a group in group chats ([showsSenderName]).
     * @property senderId the sender's user or chat id, which colors its name and picks its avatar placeholder.
     * @property avatar the sender's avatar beside the last bubble of a group; null elsewhere.
     * @property hasAvatarColumn the bubble leaves room for an avatar: incoming messages in group chats.
     * @property time when it was sent, formatted for the list; screen readers always hear it.
     * @property showsTime the time and the state show: on the last bubble of a group and on a message alone, as Google
     *   Messages shows them, and on a bubble whose time was tapped to show ([ChatEvent.ToggleTime]).
     * @property status where an outgoing message is; null for incoming ones. In channels and Saved Messages, where
     *   nobody else reads them, only while it is sent or when sending failed.
     * @property canResend sending failed and TDLib can send it again (an album: one of its items): its "Not sent" label
     *   offers to retry ([ChatEvent.Resend]).
     * @property forwardedFrom "Forwarded from X"; null if it was not forwarded.
     * @property reply the quote of the message it replies to; null if it replies to nothing.
     * @property content what the message shows, for [MessageContentBody].
     * @property contentLabel what kind of message it is, in one line ("Photo", "Voice message"), until the media
     *   components show the media itself; null for text messages.
     * @property text the text or caption with its formatting; empty if there is none.
     * @property spoilersRevealed the text's spoilers were tapped and show ([ChatEvent.RevealSpoilers]).
     * @property linkPreview the preview of a link in the text; null if none.
     * @property reactions the reactions in TDLib's order; empty if there are none.
     * @property album the photos and videos when the message is one of an album, shown as one bubble; null otherwise.
     *   The bubble is then the album's: [id] is the message whose caption it shows (the newest message when none has
     *   one), the time, state and reactions are the newest message's, and its [key] is the album's, so it stays as
     *   older pages bring in more of the album.
     * @property isEmojiOnly the message is a text of 1 to 3 emoji and nothing else ([isEmojiOnly]), without formatting,
     *   link preview, reply or forward: shown large, without a bubble.
     * @property senderUserId the user who sent an incoming message in a group chat, whose profile their avatar and name
     *   open ([ChatEvent.OpenProfile]); null for other messages, and for senders that are chats.
     * @property selfDestructSeconds how long after it was read the message deletes itself (a secret chat's timer when
     *   it was sent), shown with the time; null if it does not. TDLib gives no update as the time runs down once it is
     *   read, so the bubble shows the timer itself, not a countdown.
     * @property thread the link to the message's thread under the bubble (`REQUIREMENTS.md` FR-C9): a channel post's
     *   comments, or the replies to a group's message once there are any; null for other messages, and in the thread.
     */
    data class Bubble(
        val id: Long,
        val isOutgoing: Boolean,
        val position: GroupPosition,
        val sender: UiText,
        val senderId: Long,
        val showsSenderName: Boolean,
        val avatar: SenderAvatar?,
        val hasAvatarColumn: Boolean,
        val time: String,
        val isEdited: Boolean,
        val status: OutgoingStatus?,
        val forwardedFrom: UiText?,
        val reply: ReplyQuote?,
        val content: MessageContent,
        val contentLabel: UiText?,
        val text: FormattedText,
        val spoilersRevealed: Boolean,
        val linkPreview: LinkPreviewCard?,
        val reactions: ImmutableList<ReactionChip>,
        override val date: UiText,
        val album: MessageAlbum? = null,
        val isEmojiOnly: Boolean = false,
        val showsTime: Boolean = true,
        val canResend: Boolean = false,
        val senderUserId: Long? = null,
        val selfDestructSeconds: Int? = null,
        val thread: ThreadLink? = null,
    ) : ChatItem {
        override val key: Any get() = album?.let { albumKey(it.id) } ?: id

        /** The message whose reactions the bubble shows, and which its reaction chips change: an album's newest. */
        val reactionsMessageId: Long get() = album?.messageIds?.lastOrNull() ?: id

        override fun toString() = "Bubble(id=$id, isOutgoing=$isOutgoing, position=$position, status=$status, …)"
    }

    /** A service message ("Alice joined the group"), centred in a pill. */
    data class Service(
        val id: Long,
        val text: UiText,
        override val date: UiText,
    ) : ChatItem {
        override val key: Any get() = id

        override fun toString() = "Service(id=$id, …)"
    }

    /** The first message of a day follows this: "Today", "Yesterday", "Monday, March 17". */
    data class DateSeparator(
        val epochDay: Long,
        override val date: UiText,
    ) : ChatItem {
        override val key: Any get() = "date:$epochDay"
    }

    /** The first message that was unread when the chat was opened follows this. */
    data class UnreadDivider(
        override val date: UiText,
    ) : ChatItem {
        override val key: Any get() = UNREAD_DIVIDER_KEY
    }

    /**
     * A sponsored message of a channel or bot (`REQUIREMENTS.md` §2), a card among the messages, never one of them:
     * after the newest message, or after the one it was placed below ([withSponsored]). [date] is that message's.
     */
    data class Sponsored(
        val card: SponsoredCard,
        override val date: UiText,
    ) : ChatItem {
        override val key: Any get() = sponsoredKey(card.id)
    }

    companion object {
        const val UNREAD_DIVIDER_KEY = "unread"
    }
}

/**
 * The items of an album (`Message.mediaAlbumId` [id]), oldest first, as sent: their message ids and their photos and
 * videos, for `MediaAlbum`.
 */
@Immutable
data class MessageAlbum(
    val id: Long,
    val messageIds: ImmutableList<Long>,
    val items: ImmutableList<MessageContent>,
)

/** Where a bubble is in its group, from top to bottom: its shape and what it shows follow from it. */
enum class GroupPosition {
    Single,
    First,
    Middle,
    Last,
    ;

    /** The message above belongs to the same group. */
    val joinsPrevious: Boolean get() = this == Middle || this == Last

    /** The message below belongs to the same group. */
    val joinsNext: Boolean get() = this == First || this == Middle
}

/** The avatar of a message's sender, beside the last bubble of a group in group chats. */
@Immutable
data class SenderAvatar(
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
) {
    override fun toString() = "SenderAvatar(id=$id, …)"
}

/** Where an outgoing message is. */
enum class OutgoingStatus {
    /** On its way to Telegram: a clock. */
    Pending,

    /** Sending failed: a "Not sent" label under the bubble. */
    Failed,

    /** Telegram has it; nobody read it yet: one check. */
    Sent,

    /** The other side read it: two checks. */
    Read,
}

/**
 * The quote of the replied message at the top of a bubble: [sender]'s name, colored by [senderId], over one line of
 * [text]. Without a sender while the replied message loads, when it was deleted, or for a story.
 *
 * @property messageId the replied message of this chat, which tapping the quote shows ([ChatEvent.OpenReply]); null
 *   for a story or a message of another chat, which it does not open.
 */
@Immutable
data class ReplyQuote(
    val sender: UiText?,
    val senderId: Long?,
    val text: UiText,
    val messageId: Long? = null,
) {
    override fun toString() = "ReplyQuote(senderId=$senderId, messageId=$messageId, …)"
}

/**
 * The button under a bubble that opens the thread of message [messageId]: "12 comments" ([isComments], a channel post;
 * "Leave a comment" while [count] is 0) or "3 replies".
 */
@Immutable
data class ThreadLink(
    val messageId: Long,
    val count: Int,
    val isComments: Boolean,
)

/** The preview of a link in a message's text; each text may be empty. */
@Immutable
data class LinkPreviewCard(
    val siteName: String,
    val title: String,
    val description: String,
) {
    override fun toString() = "LinkPreviewCard(…)"
}

/** One reaction under a bubble: [label] added [count] times, [isChosen] if the user added it too. */
@Immutable
data class ReactionChip(
    val label: ReactionLabel,
    val count: Int,
    val isChosen: Boolean,
)

/** What a [ReactionChip] shows. */
@Immutable
sealed interface ReactionLabel {
    data class Emoji(
        val emoji: String,
    ) : ReactionLabel

    /** Custom emoji [customEmojiId], drawn as a placeholder until custom emoji are rendered. */
    data class CustomEmoji(
        val customEmojiId: Long,
    ) : ReactionLabel

    /** The paid star reaction of channels. */
    data object Paid : ReactionLabel
}

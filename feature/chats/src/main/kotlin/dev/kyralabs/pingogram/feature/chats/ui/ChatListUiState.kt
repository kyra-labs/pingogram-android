package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything the chat list shows (`REQUIREMENTS.md` FR-L1–L5): the chats of the selected list, the connection pill
 * and the folder tabs.
 *
 * @property connection [ConnectionStatus.Ready] hides the connection pill.
 * @property folderTabs the tabs above the list, "All chats" among the user's folders; empty without folders, which
 *   shows no tabs.
 * @property selectedFolderId the folder whose chats are shown; null for the main list ("All chats").
 */
@Immutable
data class ChatListUiState(
    val content: ChatListContent = ChatListContent.Loading,
    val connection: ConnectionStatus = ConnectionStatus.Ready,
    val folderTabs: ImmutableList<FolderTab> = persistentListOf(),
    val selectedFolderId: Int? = null,
)

/** The list itself. */
@Immutable
sealed interface ChatListContent {
    /** No chats yet, and the first page is on its way. */
    data object Loading : ChatListContent

    /** The list has no chats at all; [isFolder] for a folder, otherwise the main list. */
    data class Empty(
        val isFolder: Boolean,
    ) : ChatListContent

    /** No chats, and loading them failed with [error]: shown with a retry ([ChatListEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : ChatListContent

    /** The chats loaded so far, in TDLib's order (pinned first); more load near the end of the list. */
    data class Content(
        val items: ImmutableList<ChatListItemModel>,
    ) : ChatListContent
}

/** A tab above the chat list. */
@Immutable
sealed interface FolderTab {
    /** Unmuted unread chats in the tab's list, for its badge; 0 shows none. */
    val unreadCount: Int

    /** The main list. */
    data class AllChats(
        override val unreadCount: Int,
    ) : FolderTab

    /** Chat folder [id], [title] as plain text (its custom emoji show as their fallback emoji). */
    data class Folder(
        val id: Int,
        val title: String,
        override val unreadCount: Int,
    ) : FolderTab {
        override fun toString() = "Folder(id=$id, unreadCount=$unreadCount, …)"
    }
}

/**
 * One row of the chat list, mapped once in the ViewModel (`docs/PERFORMANCE.md` §4): the row only resolves its texts.
 * Equal rows are equal, and rows whose chat did not change are reused as they are, so the list skips them.
 *
 * @property id the chat's id, the row's key.
 * @property title the chat's name; "Saved Messages" and "Deleted Account" come from resources.
 * @property photo the avatar's photo; null shows the placeholder.
 * @property placeholderIcon a Material Symbol on the avatar placeholder instead of initials: the bookmark of Saved
 *   Messages, the person of a deleted account.
 * @property isOnline a private or secret chat whose user is online now (not bots, not the current user).
 * @property isSecret an end-to-end encrypted chat, shown with a lock.
 * @property isPinned pinned in the list shown.
 * @property unreadCount unread messages; 0 shows no count.
 * @property hasUnreadMention unread messages that mention the current user.
 * @property hasUnreadReactions reactions to the current user's messages that they have not seen.
 * @property isMarkedAsUnread marked as unread by the user, with nothing unread.
 * @property time when the last message was sent, formatted for the list; empty without a last message.
 * @property outgoingStatus the state of the last message if the current user sent it; null otherwise, in Saved
 *   Messages and in channels.
 */
@Immutable
data class ChatListItemModel(
    val id: Long,
    val title: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val isOnline: Boolean,
    val badge: TitleBadge?,
    val isSecret: Boolean,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val unreadCount: Int,
    val hasUnreadMention: Boolean,
    val hasUnreadReactions: Boolean,
    val isMarkedAsUnread: Boolean,
    val time: String,
    val outgoingStatus: OutgoingStatus?,
    val preview: ChatPreview,
) {
    /** Unread messages, or marked as unread: the title is emphasized. */
    val isUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

    override fun toString() = "ChatListItemModel(id=$id, unreadCount=$unreadCount, …)"
}

/** A badge after a chat's title, from Telegram's verification of the user, group or channel. */
enum class TitleBadge {
    Verified,

    /** Many users reported it as a scam. */
    Scam,

    /** Many users reported it as impersonating someone. */
    Fake,
}

/** Where the current user's last message in a chat is. */
enum class OutgoingStatus {
    /** On its way to Telegram. */
    Pending,

    /** Sending failed; the preview is shown in the error color. */
    Failed,

    /** Telegram has it; nobody read it yet. */
    Sent,

    /** The other side read it. */
    Read,
}

/** What the second line of a row says, by precedence: typing, a draft, the last message. */
@Immutable
sealed interface ChatPreview {
    /** Someone is doing something in the chat right now ("typing…"); shown in `primary`. */
    data class Action(
        val text: UiText,
    ) : ChatPreview

    /** A message the user started and did not send, after "Draft:" in `tertiary`. */
    data class Draft(
        val text: UiText,
    ) : ChatPreview

    /**
     * The last message: [text], after [sender] in groups ("You", or the sender's first name); none in private chats,
     * channels, and for anonymous senders and service messages.
     */
    data class Message(
        val sender: UiText?,
        val text: UiText,
    ) : ChatPreview

    /** The chat has no messages, or TDLib does not know its last message yet. */
    data object None : ChatPreview
}

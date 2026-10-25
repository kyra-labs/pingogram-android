package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything a chat list shows (`REQUIREMENTS.md` FR-L1–L5, FR-L8, FR-L9): the chats of the selected list, the
 * connection pill, the folder tabs, the archive row, the selection and the delete confirmation.
 *
 * @property kind the Chats tab or the archive.
 * @property connection [ConnectionStatus.Ready] hides the connection pill.
 * @property folderTabs the tabs above the list, "All chats" among the user's folders; empty without folders, which
 *   shows no tabs, and in the archive.
 * @property selectedFolderId the folder whose chats are shown; null for the main list ("All chats").
 * @property archive the "Archived chats" row at the top of the main list; null hides it: in folders, in the archive,
 *   and while the archive is empty.
 * @property selection the selected chats; null outside selection mode.
 * @property deleteDialog the confirmation of a delete the user asked for; null while none is shown.
 */
@Immutable
data class ChatListUiState(
    val kind: ChatListKind = ChatListKind.Chats,
    val content: ChatListContent = ChatListContent.Loading,
    val connection: ConnectionStatus = ConnectionStatus.Ready,
    val folderTabs: ImmutableList<FolderTab> = persistentListOf(),
    val selectedFolderId: Int? = null,
    val archive: ArchiveEntry? = null,
    val hasLockedChats: Boolean = false,
    val selection: ChatSelection? = null,
    val deleteDialog: DeleteDialog? = null,
)

/** Which chats a chat list shows. */
enum class ChatListKind {
    /** The Chats tab: the main list or a folder, with the folder tabs and the archive row. */
    Chats,

    /** The archived chats, pushed above the Chats tab (`REQUIREMENTS.md` FR-L9). */
    Archive,
}

/** The list itself. */
@Immutable
sealed interface ChatListContent {
    /** No chats yet, and the first page is on its way. */
    data object Loading : ChatListContent

    /** The list has no chats at all; [isFolder] for a folder, otherwise the main list or the archive. */
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
 * @property isSavedMessages the current user's own chat, Saved Messages.
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
 * @property actions what the user can do to the chat, in the order screen readers list them ([rowActions]); the row's
 *   swipes are two of them ([startSwipeAction], [endSwipeAction]).
 */
@Immutable
data class ChatListItemModel(
    val id: Long,
    val title: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val isSavedMessages: Boolean,
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
    val actions: ImmutableList<ChatRowAction>,
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

    /**
     * A secret chat that cannot be written to: "Waiting for Ada to come online", "Secret chat ended"
     * (`REQUIREMENTS.md` FR-E1), in place of the draft and the last message.
     */
    data class SecretChat(
        val text: UiText,
    ) : ChatPreview

    /** The chat has no messages, or TDLib does not know its last message yet. */
    data object None : ChatPreview
}

/**
 * The "Archived chats" row at the top of the main list (`REQUIREMENTS.md` FR-L3), which opens the archive.
 *
 * @property chatCount the chats in the archive, as TDLib estimates them.
 * @property titles the names of the first archived chats TDLib has loaded, for the row's second line; empty until it
 *   loaded any, and the row shows [chatCount] instead.
 * @property unreadCount unread chats in the archive, for a badge that is always in the muted style; 0 shows none.
 */
@Immutable
data class ArchiveEntry(
    val chatCount: Int,
    val titles: ImmutableList<String>,
    val unreadCount: Int,
) {
    override fun toString() = "ArchiveEntry(chatCount=$chatCount, unreadCount=$unreadCount, …)"
}

/**
 * Selection mode (`REQUIREMENTS.md` FR-L8): the selected chats among those shown, and what the selection toolbar does
 * to them ([selectionActions]).
 */
@Immutable
data class ChatSelection(
    val chatIds: ImmutableSet<Long>,
    val actions: ImmutableList<ChatRowAction>,
)

/** The confirmation before chats are deleted or left; confirming sends [ChatListEvent.ConfirmDelete]. */
@Immutable
sealed interface DeleteDialog {
    /**
     * One chat, [title] as its row shows it: what deleting it does ([kind]) and, where the user may also delete it for
     * everyone, what that deletes ([forEveryone]).
     */
    data class Single(
        val chatId: Long,
        val title: UiText,
        val kind: DeleteKind,
        val forEveryone: DeleteForEveryone?,
    ) : DeleteDialog

    /** Several selected chats, each deleted or left for the current user only. */
    data class Bulk(
        val chatIds: ImmutableList<Long>,
    ) : DeleteDialog
}

/** What deleting a chat does for the current user, for the dialog's title and its confirm button. */
enum class DeleteKind {
    DeleteChat,
    LeaveGroup,
    LeaveChannel,
}

/** What deleting a chat for everyone deletes, for the label of the dialog's check box. */
enum class DeleteForEveryone {
    /** The conversation for its other user too, named by the chat's title. */
    OtherUser,

    /** The group, for all of its members; offered to its owner. */
    AllMembers,

    /** The channel, for all of its subscribers; offered to its owner. */
    AllSubscribers,
}

package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpChats
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
 *   while the archive is empty, and while the user hid it ([hiddenArchive]).
 * @property hiddenArchive the archive while the user hid its row, on the main list: the top bar's archive button
 *   opens it then; null otherwise.
 * @property selection the selected chats; null outside selection mode.
 * @property deleteDialog the confirmation of a delete the user asked for; null while none is shown.
 */
@Immutable
data class ChatListUiState(
    val kind: ChatListKind = ChatListKind.Chats,
    val content: ChatListContent = ChatListContent.Loading,
    val connection: ConnectionStatus = ConnectionStatus.Ready,
    val folderTabs: ImmutableList<FolderTab> = persistentListOf(),
    val feedTab: FeedTab? = null,
    val selectedFolderId: Int? = null,
    val archive: ArchiveEntry? = null,
    val hasLockedChats: Boolean = false,
    val hiddenArchive: ArchiveEntry? = null,
    val selection: ChatSelection? = null,
    val deleteDialog: DeleteDialog? = null,
)

/**
 * What the top bar shows of a [ChatListUiState] ([ChatListScreenTopBar]): equal while only the rows change, so the bar
 * skips then (`docs/PERFORMANCE.md`).
 */
@Immutable
internal data class ChatListTopBarState(
    val kind: ChatListKind,
    val selectedCount: Int?,
    val connection: ConnectionStatus,
    val hiddenArchive: ArchiveEntry?,
)

internal val ChatListUiState.topBar: ChatListTopBarState
    get() = ChatListTopBarState(kind, selection?.chatIds?.size, connection, hiddenArchive)

/** What the [FolderBar] shows of a [ChatListUiState]: equal while only the rows change, so the bar skips then. */
@Immutable
internal data class FolderBarState(
    val tabs: ImmutableList<FolderTab>,
    val selectedFolderId: Int?,
    val isSelecting: Boolean,
    val feedTab: FeedTab?,
)

internal val ChatListUiState.folderBar: FolderBarState
    get() = FolderBarState(folderTabs, selectedFolderId, isSelecting = selection != null, feedTab)

/**
 * What the Chats tab's header says (`docs/DESIGN_SYSTEM.md` §6 "Chat list top bar"; owner decision 2026-11-06), kept
 * apart from [ChatListUiState] so its changes recompose the header alone ([ChatListViewModel.summary]).
 *
 * @property unreadChats the main list's unread unmuted chats, as TDLib counts them (the "All chats" tab's badge).
 * @property mentionChats the main list's chats with unread mentions of the user, among those loaded; locked chats
 *   never count.
 * @property account the signed-in user, for the header's avatar; null until TDLib named them.
 */
@Immutable
data class ChatListSummary(
    val unreadChats: Int = 0,
    val mentionChats: Int = 0,
    val account: HeaderAccount? = null,
    /** The chats Catch-up would go through (`CatchUpChats`). */
    val catchUpChats: Int = 0,
) {
    /** The status line shows: something is unread. */
    val hasStatus: Boolean get() = unreadChats > 0 || mentionChats > 0

    /** The header offers Catch-up: from `CatchUpChats.MIN_CHATS` chats to catch up on (owner decision 2026-11-15). */
    val offersCatchUp: Boolean get() = catchUpChats >= CatchUpChats.MIN_CHATS
}

/** The signed-in user as the header's avatar shows them. */
@Immutable
data class HeaderAccount(
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
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
        /**
         * How many of [items], from the first, the list pins (TDLib orders them first): one group under a "Pinned"
         * header (`docs/DESIGN_SYSTEM.md` §6 "Chat list row"; owner decision 2026-11-06). 0 shows neither.
         */
        val pinnedCount: Int = 0,
        /**
         * The communities whose chats show as one entry (`docs/DESIGN_SYSTEM.md` §6 "Community entry"), in the order
         * of their places: each stands before the row of [items] at [CommunityEntry.before], and its chats are not in
         * [items]. Empty outside the main list.
         */
        val communities: ImmutableList<CommunityEntry> = persistentListOf(),
    ) : ChatListContent {
        /** Every chat of the list: [items], then the chats of [communities]. */
        val allRows: List<ChatListItemModel>
            get() = if (communities.isEmpty()) items else items + communities.flatMap { it.chats }
    }
}

/**
 * A community the user joined whose chats show as one entry in the main list (td_api.tl
 * `community.are_chats_collapsed`; the owner's board 19b), in the place of its newest chat.
 *
 * @property name the community's name, as plain text.
 * @property chats its chats in the main list, in TDLib's order, mapped as any row; pinned chats stay pinned instead.
 * @property unreadChatCount how many of [chats] have unread messages (or are marked as unread), which the entry's badge
 *   counts as Telegram's apps do (owner decision 2026-11-15); 0 shows none.
 * @property isMuted the community's notifications are muted: the badge takes the muted style.
 * @property isExpanded its chats show below it, inset; kept on this device.
 * @property before the index in [ChatListContent.Content.items] of the row the entry stands before.
 */
@Immutable
data class CommunityEntry(
    val id: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val chats: ImmutableList<ChatListItemModel>,
    val unreadChatCount: Int,
    val isMuted: Boolean,
    val isExpanded: Boolean,
    val before: Int,
) {
    override fun toString() = "CommunityEntry(id=$id, chats=${chats.size}, isExpanded=$isExpanded, …)"
}

/**
 * The Channel feed's tab (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"), first in the folder row while the user follows
 * [channelCount] channels, 2 or more: their [unreadCount] unread posts. It opens the feed rather than a list.
 */
@Immutable
data class FeedTab(
    val unreadCount: Int,
    val channelCount: Int,
)

/** The folder id of the "Keyword alerts" tab ([FolderTab.KeywordAlerts]), which no TDLib folder has. */
internal const val KEYWORD_ALERTS_FOLDER_ID = Int.MIN_VALUE

/** A tab above the chat list. */
@Immutable
sealed interface FolderTab {
    /** Unmuted unread chats in the tab's list, for its badge; 0 shows none. */
    val unreadCount: Int

    /** Any chat of the tab's list is unread, muted ones included: its menu offers "Mark all as read". */
    val hasUnread: Boolean

    /** The folder the tab selects: null for "All chats", [KEYWORD_ALERTS_FOLDER_ID] for "Keyword alerts". */
    val folderId: Int?

    /** The main list. */
    data class AllChats(
        override val unreadCount: Int,
        override val hasUnread: Boolean = unreadCount > 0,
    ) : FolderTab {
        override val folderId: Int? get() = null
    }

    /**
     * "Keyword alerts" (board 30, owner decision 2026-11-15): the [chatCount] chats with a match of the user's keyword
     * alerts in the last week, last among the tabs while there are any and the user asked for the tab. No menu.
     */
    data class KeywordAlerts(
        val chatCount: Int,
    ) : FolderTab {
        override val unreadCount: Int get() = chatCount
        override val hasUnread: Boolean get() = false
        override val folderId: Int get() = KEYWORD_ALERTS_FOLDER_ID
    }

    /**
     * Chat folder [id], [title] as plain text (its custom emoji show as their fallback emoji), and TDLib's name of its
     * icon ([iconName], empty for none), which the tabs show where Appearance → Chat list → "Folder tabs" asks.
     */
    data class Folder(
        val id: Int,
        val title: String,
        override val unreadCount: Int,
        override val hasUnread: Boolean = unreadCount > 0,
        val iconName: String = "",
    ) : FolderTab {
        override val folderId: Int get() = id

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
 * @property unreadTopicCount a forum's topics with unread messages, which its badge counts instead of the messages, as
 *   Telegram's apps do (owner decision 2026-11-15); null where it is no forum, or before its topics are counted.
 * @property hasUnreadMention unread messages that mention the current user.
 * @property hasUnreadReactions reactions to the current user's messages that they have not seen.
 * @property isMarkedAsUnread marked as unread by the user, with nothing unread.
 * @property time when the last message was sent, formatted for the list; empty without a last message.
 * @property outgoingStatus the state of the last message if the current user sent it; null otherwise, in Saved
 *   Messages and in channels.
 * @property actions what the user can do to the chat, in the order screen readers list them ([rowActions]); the row's
 *   swipes are two of them ([startSwipeAction], [endSwipeAction]).
 * @property avatarShape the avatar's shape: a rounded square for a forum, a circle otherwise ([AvatarShape.ofChat]).
 * @property topics a forum's topics, the newest message first, which its row names on a line of its own above the
 *   preview, as Telegram's apps do (owner decision 2026-11-15); null where it is no forum, or before they are loaded.
 * @property keyword the word of the user's keyword alerts the chat matched, as a chip, on the "Keyword alerts" tab
 *   only (board 30); null elsewhere.
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
    val avatarShape: AvatarShape = AvatarShape.Circle,
    val unreadTopicCount: Int? = null,
    val topics: ImmutableList<RowTopic>? = null,
    val keyword: String? = null,
) {
    /** Unread messages, or marked as unread: the title is emphasized. */
    val isUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

    override fun toString() = "ChatListItemModel(id=$id, unreadCount=$unreadCount, …)"
}

/**
 * A topic a forum's row names: its [name], the [color] of its icon (null for General, which has a symbol of its own),
 * and whether it has unread messages ([isUnread]).
 */
@Immutable
data class RowTopic(
    val id: Int,
    val name: String,
    val color: TopicColor?,
    val isUnread: Boolean,
)

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
     * [contentIcon] is what kind of message it is, shown before it (`docs/DESIGN_SYSTEM.md` §13.3): a photo, video,
     * GIF, voice message, song, file, place, poll or link; null for a plain text.
     */
    data class Message(
        val sender: UiText?,
        val text: UiText,
        @param:DrawableRes val contentIcon: Int? = null,
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
 * @property isCompact the row shows as one line, without the names ([ArchiveRowStyle.Compact]).
 */
@Immutable
data class ArchiveEntry(
    val chatCount: Int,
    val titles: ImmutableList<String>,
    val unreadCount: Int,
    val isCompact: Boolean = false,
) {
    override fun toString() = "ArchiveEntry(chatCount=$chatCount, unreadCount=$unreadCount, isCompact=$isCompact, …)"
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

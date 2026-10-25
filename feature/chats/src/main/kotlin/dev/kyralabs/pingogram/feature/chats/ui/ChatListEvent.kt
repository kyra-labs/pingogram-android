package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import kotlinx.collections.immutable.ImmutableList

/** What the user does on a chat list, sent to [ChatListViewModel.onEvent]. */
sealed interface ChatListEvent {
    /** A folder tab was selected; null selects "All chats". Selecting another list clears the selection. */
    data class SelectFolder(
        val folderId: Int?,
    ) : ChatListEvent

    /**
     * Whether the list shows its end or is within about a page of it; reported on every change, and false when the
     * list leaves the screen. While true, the next chats load, page after page, until all are loaded.
     */
    data class NearEndChanged(
        val isNearEnd: Boolean,
    ) : ChatListEvent

    /** "Try again" after loading the chats failed. */
    data object Retry : ChatListEvent

    /** A row was tapped: selects or deselects the chat in selection mode; otherwise opens its conversation. */
    data class ChatClicked(
        val chatId: Long,
    ) : ChatListEvent

    /** A row was long-pressed: starts selection mode with the chat, or selects or deselects it in selection mode. */
    data class ChatLongClicked(
        val chatId: Long,
    ) : ChatListEvent

    /** The close button or back in selection mode: deselects every chat. */
    data object ClearSelection : ChatListEvent

    /** [action] on one chat: a swipe, or a screen reader's action on its row. */
    data class RowAction(
        val chatId: Long,
        val action: ChatRowAction,
    ) : ChatListEvent

    /** [action] from the selection toolbar, on the chats it applies to among the selected ones. */
    data class SelectionAction(
        val action: ChatRowAction,
    ) : ChatListEvent

    /** "Undo" on the snackbar of [ChatListEffect.ChatsArchived]: moves [chatIds] back to the main list. */
    data class UndoArchive(
        val chatIds: ImmutableList<Long>,
    ) : ChatListEvent

    /** The archive row was tapped, or the top bar's archive button while the row is hidden. */
    data object OpenArchive : ChatListEvent

    /** The archive's top bar asked for its settings. */
    data object OpenArchiveSettings : ChatListEvent

    /**
     * The archive row's menu, swipe or screen reader's action: shows the row in [style]. Hiding it offers to undo
     * ([ChatListEffect.ArchiveRowHidden]).
     */
    data class SetArchiveRowStyle(
        val style: ArchiveRowStyle,
    ) : ChatListEvent

    /** "Mark all as read" in a folder tab's menu: every chat of folder [folderId], or of the main list for null. */
    data class MarkAllAsRead(
        val folderId: Int?,
    ) : ChatListEvent

    /** The "New chat" button, or the one of the empty Chats tab, was tapped. */
    data object NewChat : ChatListEvent

    /**
     * The delete dialog was confirmed; [forEveryone] if its check box was checked, which only one chat offers
     * ([DeleteDialog.Single.forEveryone]).
     */
    data class ConfirmDelete(
        val forEveryone: Boolean,
    ) : ChatListEvent

    /** The delete dialog was cancelled or dismissed. */
    data object DismissDelete : ChatListEvent
}

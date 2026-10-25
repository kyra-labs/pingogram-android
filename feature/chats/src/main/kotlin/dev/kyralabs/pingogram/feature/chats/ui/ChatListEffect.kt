package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import kotlinx.collections.immutable.ImmutableList

/** One-off effects of [ChatListViewModel], handled by the screen once each (`docs/ARCHITECTURE.md`, ViewModels). */
sealed interface ChatListEffect {
    /** [chatIds] moved to the archive: a snackbar says so, with "Undo" ([ChatListEvent.UndoArchive]). */
    data class ChatsArchived(
        val chatIds: ImmutableList<Long>,
    ) : ChatListEffect

    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. Errors are never dialogs. */
    data class ShowError(
        val error: DomainError,
    ) : ChatListEffect

    /**
     * The archive row was hidden: a snackbar says where the archive is now, with "Undo", which shows the row as
     * [previous] again ([ChatListEvent.SetArchiveRowStyle]).
     */
    data class ArchiveRowHidden(
        val previous: ArchiveRowStyle,
    ) : ChatListEffect

    /** Opens the archived chats. */
    data object OpenArchive : ChatListEffect

    /** Opens the archive's settings. */
    data object OpenArchiveSettings : ChatListEffect

    /** Opens the New chat screen. */
    data object OpenNewChat : ChatListEffect

    /** Opens the conversation of chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : ChatListEffect
}

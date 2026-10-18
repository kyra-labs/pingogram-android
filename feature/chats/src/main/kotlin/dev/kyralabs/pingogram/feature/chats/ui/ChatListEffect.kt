package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
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

    /** Opens the archived chats. */
    data object OpenArchive : ChatListEffect
}

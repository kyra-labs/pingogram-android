package dev.kyralabs.pingogram.feature.chats.ui.drafts

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import kotlinx.collections.immutable.ImmutableList

/** The Drafts page (`REQUIREMENTS.md` FR-X16): the chats with a message the user started and has not sent. */
@Immutable
sealed interface DraftsUiState {
    /** The chats are still loading and none of those loaded has a draft yet. */
    data object Loading : DraftsUiState

    /** No chat, main or archived, has a draft. */
    data object Empty : DraftsUiState

    /** The [drafts] as global search's message rows, newest first, one per chat. */
    data class Content(
        val drafts: ImmutableList<SearchMessageItem>,
    ) : DraftsUiState {
        override fun toString() = "Content(drafts=${drafts.size})"
    }
}

/** What the user does on the Drafts page, sent to [DraftsViewModel.onEvent]. */
sealed interface DraftsEvent {
    /** A row was tapped: chat [chatId] opens, its draft in the message box. */
    data class Open(
        val chatId: Long,
    ) : DraftsEvent
}

/** One-off effects of [DraftsViewModel], handled by the page once each. */
sealed interface DraftsEffect {
    /** Opens chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : DraftsEffect
}

package dev.kyralabs.pingogram.feature.chats.ui.mentions

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import kotlinx.collections.immutable.ImmutableList

/** The Mentions & replies page (`REQUIREMENTS.md` FR-X1): the messages that mention the user or reply to them. */
@Immutable
sealed interface MentionsUiState {
    /** The messages are on their way. */
    data object Loading : MentionsUiState

    /** Nothing mentions the user or replies to them in the chats searched. */
    data object Empty : MentionsUiState

    /** Reading them failed with [error]: shown with a retry ([MentionsEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : MentionsUiState

    /**
     * The messages as global search's message rows: [unread] ones, still unread where they were sent, then [earlier]
     * ones; each group newest first, and one of them may be empty.
     */
    data class Content(
        val unread: ImmutableList<SearchMessageItem>,
        val earlier: ImmutableList<SearchMessageItem>,
    ) : MentionsUiState {
        override fun toString() = "Content(unread=${unread.size}, earlier=${earlier.size})"
    }
}

/** What the user does on the Mentions & replies page, sent to [MentionsViewModel.onEvent]. */
sealed interface MentionsEvent {
    /** A row was tapped: its chat opens at message [messageId]. */
    data class Open(
        val chatId: Long,
        val messageId: Long,
    ) : MentionsEvent

    /** "Try again" after reading them failed. */
    data object Retry : MentionsEvent

    /** The page shows again, back from a chat: reads them again, as one opened there may be read now. */
    data object Refresh : MentionsEvent
}

/** One-off effects of [MentionsViewModel], handled by the page once each. */
sealed interface MentionsEffect {
    /** Opens chat [chatId] at message [messageId]. */
    data class OpenMessage(
        val chatId: Long,
        val messageId: Long,
    ) : MentionsEffect
}

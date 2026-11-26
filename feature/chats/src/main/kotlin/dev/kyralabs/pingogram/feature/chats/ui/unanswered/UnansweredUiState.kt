package dev.kyralabs.pingogram.feature.chats.ui.unanswered

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import kotlinx.collections.immutable.ImmutableList

/** The Unanswered page (`REQUIREMENTS.md` FR-X22): the private chats waiting for the user's answer. */
@Immutable
sealed interface UnansweredUiState {
    /** The chats are still loading and none of those loaded is unanswered yet. */
    data object Loading : UnansweredUiState

    /** No chat is waiting for an answer. */
    data object Empty : UnansweredUiState

    /** The [chats] as global search's message rows (their last message), the longest waiting first. */
    data class Content(
        val chats: ImmutableList<SearchMessageItem>,
    ) : UnansweredUiState {
        override fun toString() = "Content(chats=${chats.size})"
    }
}

/** What the user does on the Unanswered page, sent to [UnansweredViewModel.onEvent]. */
sealed interface UnansweredEvent {
    /** "Reply": chat [chatId] opens, its message box focused. */
    data class Reply(
        val chatId: Long,
    ) : UnansweredEvent

    /** "Dismiss": chat [chatId] leaves the page until a message newer than [messageId] comes. */
    data class Dismiss(
        val chatId: Long,
        val messageId: Long,
    ) : UnansweredEvent
}

/** One-off effects of [UnansweredViewModel], handled by the page once each. */
sealed interface UnansweredEffect {
    /** Opens chat [chatId] with its message box focused. */
    data class Reply(
        val chatId: Long,
    ) : UnansweredEffect

    /** A dismissal was not saved. */
    data class ShowError(
        val error: DomainError,
    ) : UnansweredEffect
}

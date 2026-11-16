package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.suggested.SuggestedPostsViewModel

/**
 * What an administrator does with a post suggested in a channel's direct messages, handled by
 * [SuggestedPostsViewModel] rather than the conversation's view model: the route hands these events to it.
 */
sealed interface SuggestedPostEvent : ChatEvent {
    /** "Approve" ([approve]) or "Decline" on message [messageId]'s card: the question before answering opens. */
    data class Answer(
        val messageId: Long,
        val approve: Boolean,
    ) : SuggestedPostEvent

    /** The question's "Publish" or "Decline": the answer goes to Telegram. */
    data object Confirm : SuggestedPostEvent

    /** The question closes without answering. */
    data object Dismiss : SuggestedPostEvent
}

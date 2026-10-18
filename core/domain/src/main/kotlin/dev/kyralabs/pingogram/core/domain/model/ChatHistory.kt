package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The loaded part of a chat's history, for the conversation screen (`REQUIREMENTS.md` FR-C1): from the chat's newest
 * message back to the oldest one loaded so far, without gaps. A snapshot, replaced on every change.
 */
data class ChatHistory(
    /**
     * Newest first, as the conversation's reverse layout shows them: by id, which TDLib assigns in increasing order
     * (a message being sent has a temporary id, larger than every sent one, until it gets its final id).
     */
    val messages: ImmutableList<Message>,
    /** [messages] go back to the chat's first message: there is nothing older to load. */
    val reachedStart: Boolean,
)

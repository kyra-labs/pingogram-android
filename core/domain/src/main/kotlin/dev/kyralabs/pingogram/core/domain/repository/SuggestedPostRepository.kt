package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * What a channel's administrators do with the posts subscribers suggest through its direct messages (td_api.tl
 * `approveSuggestedPost`, `declineSuggestedPost`). A suggestion is sent as a message with
 * `SendOptions.suggestAsPost`; TDLib then reports its state with `updateMessageSuggestedPostInfo`.
 */
interface SuggestedPostRepository {
    /** Publishes the post suggested by message [messageId] of direct messages chat [chatId], at the time it asked. */
    suspend fun approve(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /** Declines the post suggested by message [messageId], with [comment] for its author (empty for none). */
    suspend fun decline(
        chatId: Long,
        messageId: Long,
        comment: String,
    ): Outcome<Unit>
}

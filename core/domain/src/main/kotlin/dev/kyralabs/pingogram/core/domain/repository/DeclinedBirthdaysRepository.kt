package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The birthday suggestions (td_api.tl `messageSuggestBirthdate`, the owner's board 16b) the user declined, kept on the
 * device: Telegram has no answer for declining, so the card would otherwise offer "Accept" again.
 */
interface DeclinedBirthdaysRepository {
    /** The messages of chat [chatId] whose suggestion the user declined, then every change. */
    fun declined(chatId: Long): Flow<Set<Long>>

    /** Declines the suggestion of message [messageId] in chat [chatId], for good on this device. */
    suspend fun decline(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>
}

package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The messages a chat holds back until their time (`REQUIREMENTS.md` FR-C5: scheduled messages), for the conversation's
 * "3 scheduled messages" bar and the list of them. Scheduling a new message is sending it with a time
 * (`SendOptions.scheduledAt`); editing one's text or caption is `MessageActionRepository.editText` / `editCaption`, as
 * for any message.
 */
interface ScheduledMessageRepository {
    /**
     * The scheduled messages of chat [chatId], oldest send time first, once they are loaded, then every change for as
     * long as it is collected: one scheduled on any device, edited, rescheduled, deleted, or sent by Telegram at its
     * time (it then leaves the list, and joins the chat's history as a new message). Each collection loads them again;
     * a load that fails emits its failure, and nothing after it.
     */
    fun scheduledMessages(chatId: Long): Flow<Outcome<ImmutableList<ScheduledMessage>>>

    /** Sends scheduled message [messageId] of chat [chatId] now; it then leaves the scheduled messages. */
    suspend fun sendNow(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /**
     * Moves scheduled message [messageId] of chat [chatId] to [sendDate] (Unix time, seconds, in the future), repeating
     * every [repeatPeriod] seconds after it where that is more than 0 (pass the message's own to keep it).
     */
    suspend fun reschedule(
        chatId: Long,
        messageId: Long,
        sendDate: Long,
        repeatPeriod: Int = 0,
    ): Outcome<Unit>

    /** Deletes scheduled messages [messageIds] of chat [chatId]: nobody ever gets them. */
    suspend fun delete(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit>
}

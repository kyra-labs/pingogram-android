package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * An account's message reminders (the owner's board 29), kept on this device only: never sent to Telegram or anywhere
 * else. A message has at most one. Logging out deletes them, as does the account leaving the device.
 */
interface ReminderRepository {
    /** Every reminder, the soonest first, then every change. */
    val reminders: Flow<ImmutableList<MessageReminder>>

    /** The reminders on chat [chatId]'s messages, by message id, then every change. */
    fun reminders(chatId: Long): Flow<ImmutableMap<Long, MessageReminder>>

    /** Keeps [reminder], replacing the message's earlier one; fails with `DomainError.Unknown` if it cannot. */
    suspend fun set(reminder: MessageReminder): Outcome<Unit>

    /** Deletes the reminder on message [messageId] of chat [chatId], if there is one. */
    suspend fun delete(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>
}

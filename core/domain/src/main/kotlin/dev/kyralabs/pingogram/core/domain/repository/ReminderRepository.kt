package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Reminder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The account's reminders of messages (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29), kept on
 * this device only: nothing reaches Telegram. Gone when the account logs out or leaves the device.
 */
interface ReminderRepository {
    /** Every reminder, the soonest first. */
    val reminders: Flow<ImmutableList<Reminder>>

    /** The reminder of message [messageId] of chat [chatId], or null. */
    fun reminder(
        chatId: Long,
        messageId: Long,
    ): Flow<Reminder?>

    /** Sets [reminder], replacing the message's earlier one. */
    suspend fun save(reminder: Reminder): Outcome<Unit>

    /** Deletes the reminder of message [messageId] of chat [chatId]. */
    suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit>

    /** Marks the reminder of message [messageId] of chat [chatId] done, or due again. */
    suspend fun setDone(
        chatId: Long,
        messageId: Long,
        done: Boolean,
    ): Outcome<Unit>
}

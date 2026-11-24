package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The birthday reminders the user set (`REQUIREMENTS.md` FR-X18), one per contact, kept on the phone only: nothing
 * sets one but the user.
 */
interface BirthdayReminderRepository {
    /** Every reminder, by user id, then every change. */
    val reminders: Flow<ImmutableList<BirthdayReminder>>

    /** Keeps [reminder], replacing the user's earlier one; fails with `DomainError.Unknown` if it cannot. */
    suspend fun set(reminder: BirthdayReminder): Outcome<Unit>

    /** Deletes the reminder for user [userId], if there is one. */
    suspend fun delete(userId: Long): Outcome<Unit>
}

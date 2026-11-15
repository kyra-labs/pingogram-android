package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Rings a reminder ([ReminderAlarms] schedules it at its time), or snoozes it from its notification:
 *
 * - **Ring** posts the reminder's notification ([ReminderNotificationBuilder]) in its own channel, so it rings also for
 *   a muted chat, and marks the reminder done. Nothing rings for a reminder deleted, done or moved later meanwhile.
 * - **Snooze** moves the reminder [SNOOZE] later, due again, schedules it and removes the notification.
 *
 * The chat's name and photo come from TDLib, which the process may be starting for this ([awaitLoggedIn]); without
 * them the notification still rings, with what the message said when the reminder was set.
 */
@HiltWorker
class ReminderWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val accounts: suspend (slot: Int) -> AccountReminders?,
        private val notifications: ReminderNotificationBuilder,
        private val clock: WallClock,
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            graphs: AccountGraphs,
            notifications: ReminderNotificationBuilder,
            clock: WallClock,
        ) : this(
            context = context,
            parameters = parameters,
            accounts = { slot ->
                graphs.accounts
                    .first()
                    .firstOrNull { it.slot == slot }
                    ?.repositories
                    ?.let { AccountReminders(it.authRepository(), it.chatRepository(), it.reminderRepository()) }
            },
            notifications = notifications,
            clock = clock,
        )

        override suspend fun doWork(): Result {
            val slot = inputData.getInt(KEY_SLOT, -1)
            val chatId = inputData.getLong(KEY_CHAT, 0)
            val messageId = inputData.getLong(KEY_MESSAGE, 0)
            val account = accounts(slot)
            val reminder = account?.reminders?.reminder(chatId, messageId)?.first()
            // Nothing to do for an account that left the device or a reminder deleted meanwhile.
            if (account != null && reminder != null) {
                when (inputData.getString(KEY_ACTION)) {
                    ACTION_RING -> ring(slot, account, reminder)
                    ACTION_SNOOZE -> snooze(slot, account, reminder)
                }
            }
            return Result.success()
        }

        private suspend fun ring(
            slot: Int,
            account: AccountReminders,
            reminder: Reminder,
        ) {
            val early = reminder.at * MILLIS_PER_SECOND > clock.now().toEpochMilli() + EARLY_TOLERANCE_MILLIS
            if (reminder.done || early) return
            notifications.post(slot, reminder, account.chatOf(reminder.chatId))
            account.reminders.setDone(reminder.chatId, reminder.messageId, done = true)
        }

        private suspend fun snooze(
            slot: Int,
            account: AccountReminders,
            reminder: Reminder,
        ) {
            val at = clock.now().epochSecond + SNOOZE.inWholeSeconds
            val snoozed = reminder.copy(at = at, done = false)
            if (account.reminders.save(snoozed) is Outcome.Success) {
                WorkManagerReminderWork(applicationContext).schedule(slot, snoozed, SNOOZE.inWholeMilliseconds)
            }
            notifications.cancel(slot, reminder)
        }

        private suspend fun AccountReminders.chatOf(chatId: Long): Chat? =
            chats.chat(chatId).first()
                ?: withTimeoutOrNull(LOGIN_TIMEOUT) {
                    auth.awaitLoggedIn()
                    (chats.getChat(chatId) as? Outcome.Success)?.value
                }

        internal companion object {
            /** How much later a snooze moves a reminder. */
            val SNOOZE = 1.hours

            /** How long TDLib may take to open its database and report the login. */
            private val LOGIN_TIMEOUT = 20.seconds

            // WorkManager may start the work a little early; more than this means the reminder moved later.
            private const val EARLY_TOLERANCE_MILLIS = 60_000L
            private const val MILLIS_PER_SECOND = 1_000L

            private const val KEY_ACTION = "action"
            private const val KEY_SLOT = "slot"
            private const val KEY_CHAT = "chat"
            private const val KEY_MESSAGE = "message"
            private const val ACTION_RING = "ring"
            private const val ACTION_SNOOZE = "snooze"

            /** The input of the work that rings the reminder of message [messageId] of chat [chatId]. */
            fun ring(
                slot: Int,
                chatId: Long,
                messageId: Long,
            ): Data = input(ACTION_RING, slot, chatId, messageId)

            /** The input of the work that snoozes that reminder. */
            fun snooze(
                slot: Int,
                chatId: Long,
                messageId: Long,
            ): Data = input(ACTION_SNOOZE, slot, chatId, messageId)

            private fun input(
                action: String,
                slot: Int,
                chatId: Long,
                messageId: Long,
            ): Data = workDataOf(KEY_ACTION to action, KEY_SLOT to slot, KEY_CHAT to chatId, KEY_MESSAGE to messageId)
        }
    }

/** What a reminder needs of its account: its login, to wait for TDLib, its chats and its reminders. */
internal class AccountReminders(
    val auth: AuthRepository,
    val chats: ChatRepository,
    val reminders: ReminderRepository,
)

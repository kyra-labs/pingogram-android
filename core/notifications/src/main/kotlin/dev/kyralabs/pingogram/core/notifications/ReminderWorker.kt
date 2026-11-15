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
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Rings a message reminder ([ReminderAlarms]): once TDLib is ready, if the reminder still stands as it was scheduled
 * (not deleted, done or moved meanwhile), posts its notification ([ReminderNotificationBuilder]) with the chat's name,
 * the message as the chat list previews it, read from TDLib, and the user's note. Nothing is retried: a reminder that
 * cannot ring (its account gone, TDLib not ready in time) stays in the reminders list.
 */
@HiltWorker
class ReminderWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val accounts: suspend (slot: Int) -> ReminderAccountParts?,
        private val notifications: ReminderNotificationBuilder,
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            graphs: AccountGraphs,
            notifications: ReminderNotificationBuilder,
        ) : this(
            context = context,
            parameters = parameters,
            accounts = { slot ->
                graphs.accounts
                    .first()
                    .firstOrNull { it.slot == slot }
                    ?.repositories
                    ?.let(::ReminderAccountParts)
            },
            notifications = notifications,
        )

        override suspend fun doWork(): Result {
            ring(
                slot = inputData.getInt(KEY_SLOT, -1),
                chatId = inputData.getLong(KEY_CHAT, 0),
                messageId = inputData.getLong(KEY_MESSAGE, 0),
                at = inputData.getLong(KEY_AT, 0),
            )
            return Result.success()
        }

        /** Posts the reminder's notification if it still stands as scheduled ([at], in epoch seconds). */
        private suspend fun ring(
            slot: Int,
            chatId: Long,
            messageId: Long,
            at: Long,
        ) {
            val account = accounts(slot) ?: return
            val reminder =
                account.reminders.reminders
                    .first()
                    .firstOrNull { it.chatId == chatId && it.messageId == messageId }
                    ?.takeIf { !it.isDone && it.at.epochSecond == at }
                    ?: return
            val ready = withTimeoutOrNull(LOGIN_TIMEOUT) { account.auth.awaitLoggedIn() } == true
            if (ready) notifications.post(slot, reminder, chatTitle(account, chatId), preview(account, reminder))
        }

        private suspend fun chatTitle(
            account: ReminderAccountParts,
            chatId: Long,
        ): String? = (account.chats.getChat(chatId) as? Outcome.Success)?.value?.title

        /** The message as the chat list previews it; null where TDLib no longer has it. */
        private suspend fun preview(
            account: ReminderAccountParts,
            reminder: MessageReminder,
        ): String? {
            val message =
                (account.messages.message(reminder.chatId, reminder.messageId) as? Outcome.Success)?.value
                    ?: return null
            return messagePreviewText(message, myUserId = null, name = { null }).resolve(applicationContext.resources)
        }

        internal companion object {
            /** How long TDLib may take to open its database and report the login. */
            val LOGIN_TIMEOUT = 30.seconds

            private const val KEY_SLOT = "slot"
            private const val KEY_CHAT = "chat"
            private const val KEY_MESSAGE = "message"
            private const val KEY_AT = "at"

            /** The input of the work that rings [reminder] of the account in [slot]: ids and time, never the note. */
            fun inputOf(
                slot: Int,
                reminder: MessageReminder,
            ): Data =
                workDataOf(
                    KEY_SLOT to slot,
                    KEY_CHAT to reminder.chatId,
                    KEY_MESSAGE to reminder.messageId,
                    KEY_AT to reminder.at.epochSecond,
                )
        }
    }

/** What ringing a reminder needs of its account: its reminders, its login, its chats and its messages. */
internal class ReminderAccountParts(
    val reminders: ReminderRepository,
    val auth: AuthRepository,
    val chats: ChatRepository,
    val messages: MessageRepository,
) {
    constructor(repositories: AccountRepositories) : this(
        reminders = repositories.reminderRepository(),
        auth = repositories.authRepository(),
        chats = repositories.chatRepository(),
        messages = repositories.messageRepository(),
    )
}

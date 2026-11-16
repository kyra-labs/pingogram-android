package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.content.res.Resources
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Rings a message reminder ([ReminderAlarms]): once TDLib is ready, if the reminder still stands as it was scheduled
 * (not deleted, done or moved meanwhile), posts its notification ([ReminderNotificationBuilder]) with the chat's name
 * and avatar, the message as the chat list previews it (after its sender in groups), read from TDLib, and the user's
 * note. A locked chat's reminder rings without its name, picture, message or note (`docs/adr/0029-locked-chats.md`).
 * Nothing is retried: a reminder that cannot ring (its account gone, TDLib not ready in time) stays in the reminders
 * list.
 */
@HiltWorker
class ReminderWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val accounts: suspend (slot: Int) -> ReminderAccountParts?,
        private val notifications: ReminderNotificationBuilder,
        private val iconOf: suspend (slot: Int, chat: Chat) -> IconCompat? = { _, _ -> null },
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            graphs: AccountGraphs,
            notifications: ReminderNotificationBuilder,
            avatars: AvatarIcons,
        ) : this(
            context = context,
            parameters = parameters,
            accounts = { slot ->
                graphs.accounts
                    .first()
                    .firstOrNull { it.slot == slot }
                    ?.repositories
                    ?.let { ReminderAccountParts(it, context.resources) }
            },
            notifications = notifications,
            // Another account's photos are not the shown account's files: its avatars are initials.
            iconOf = { slot, chat ->
                avatars.icon(chat.photo.takeIf { slot == graphs.activeSlot.value }, chat.id, chat.title)
            },
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
            when {
                !ready -> {
                    // TDLib did not report the login in time: the reminder stays in the list, unrung.
                }

                chatId in account.lockedChatIds() -> {
                    notifications.postLocked(slot, reminder)
                }

                else -> {
                    val chat = (account.chats.getChat(chatId) as? Outcome.Success)?.value
                    notifications.post(
                        slot = slot,
                        reminder = reminder,
                        chatTitle = chat?.title,
                        preview = preview(account, reminder, chat),
                        icon = chat?.let { iconOf(slot, it) },
                    )
                }
            }
        }

        /**
         * The message as the chat list previews it, after its sender's name in a group (a private chat's or a
         * channel's name already says who wrote); null where TDLib no longer has it.
         */
        private suspend fun preview(
            account: ReminderAccountParts,
            reminder: MessageReminder,
            chat: Chat?,
        ): String? {
            val message =
                (account.messages.message(reminder.chatId, reminder.messageId) as? Outcome.Success)?.value
                    ?: return null
            val sender = message.sender
            val senderName =
                when (sender) {
                    is MessageSender.User -> account.userName(sender.userId)
                    is MessageSender.Chat -> chat?.title?.takeIf { sender.chatId == chat.id }
                }
            val name: (MessageSender) -> UiText? = { who -> senderName?.takeIf { who == sender }?.let(UiText::Plain) }
            val text = messagePreviewText(message, myUserId = null, name = name).resolve(applicationContext.resources)
            val shownSender = senderName?.takeIf { chat?.type.isGroup() }
            return if (shownSender == null) text else "${shownSender.bidiIsolated()}: ${text.bidiIsolated()}"
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

/**
 * What ringing a reminder needs of its account: its reminders, its login, its chats, its messages, its locked chats
 * (whether open or not) and its users' names.
 */
internal class ReminderAccountParts(
    val reminders: ReminderRepository,
    val auth: AuthRepository,
    val chats: ChatRepository,
    val messages: MessageRepository,
    val lockedChatIds: suspend () -> Set<Long> = { emptySet() },
    val userName: suspend (userId: Long) -> String? = { null },
) {
    constructor(repositories: AccountRepositories, resources: Resources) : this(
        reminders = repositories.reminderRepository(),
        auth = repositories.authRepository(),
        chats = repositories.chatRepository(),
        messages = repositories.messageRepository(),
        lockedChatIds = { repositories.chatLockRepository().lockedChatIds.first() },
        userName = { id ->
            (repositories.userRepository().getUser(id) as? Outcome.Success)?.value?.fullName()?.resolve(resources)
        },
    )
}

private fun ChatType?.isGroup(): Boolean = this is ChatType.BasicGroup || (this is ChatType.Supergroup && !isChannel)

package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.awaitLoggedIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Carries out a [NotificationAction] once TDLib is ready: the process may have been started for this, and TDLib then
 * opens its database first ([awaitLoggedIn]).
 *
 * - **Reply** sends the text, keeping the chat's draft, then marks the messages of the notification as read, as
 *   answering them in the chat would; TDLib then removes the notification.
 * - **Mark as read** reads them; TDLib removes the notification.
 * - **Dismiss** tells TDLib that the user swiped the notification away, so it does not come back.
 *
 * TDLib accepts these offline and carries them out once connected, also after a restart, so nothing is retried here.
 * When a reply or a read fails (logged out meanwhile, a chat the user may no longer write to), the notification is
 * posted again as it was, so it stops showing the reply in progress ([NotificationRenderer.refresh]).
 */
@HiltWorker
class NotificationActionWorker
    @AssistedInject
    internal constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val authRepository: AuthRepository,
        private val notificationRepository: NotificationRepository,
        private val renderer: NotificationRenderer,
        private val builder: ConversationNotificationBuilder,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val action = NotificationAction.fromData(inputData) ?: return Result.failure()
            val loggedIn = withTimeoutOrNull(LOGIN_TIMEOUT) { authRepository.awaitLoggedIn() } == true
            val done = loggedIn && perform(action) is Outcome.Success
            if (!done && action !is NotificationAction.Dismiss) renderer.refresh(action.groupId)
            return if (done) Result.success() else Result.failure()
        }

        private suspend fun perform(action: NotificationAction): Outcome<Unit> =
            when (action) {
                is NotificationAction.Reply -> {
                    when (
                        val sent =
                            notificationRepository.reply(
                                action.chatId,
                                action.text,
                                action.replyToMessageId,
                            )
                    ) {
                        is Outcome.Success -> markAsRead(action.chatId, action.messageIds)
                        is Outcome.Failure -> sent
                    }
                }

                is NotificationAction.MarkAsRead -> {
                    markAsRead(action.chatId, action.messageIds)
                }

                is NotificationAction.Dismiss -> {
                    notificationRepository.dismiss(action.groupId, action.maxNotificationId)
                }
            }

        private suspend fun markAsRead(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> =
            if (messageIds.isEmpty()) Outcome.Success(Unit) else notificationRepository.markAsRead(chatId, messageIds)

        /** Shown only before Android 12, where expedited work runs as a foreground service. */
        override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(FOREGROUND_ID, builder.progress())

        private companion object {
            /** How long TDLib may take to open its database and report the login. */
            val LOGIN_TIMEOUT = 30.seconds

            /** The id of [getForegroundInfo]'s notification; group notifications are tagged, this one is not. */
            const val FOREGROUND_ID = 1
        }
    }

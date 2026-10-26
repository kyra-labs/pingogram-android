package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * The notifications of a call (`REQUIREMENTS.md` FR-V1, `docs/adr/0018-calls-and-telecom.md`), as Android's call
 * style ([NotificationCompat.CallStyle]): the system shows them as calls, with the caller's name and avatar.
 *
 * - **Incoming** ([incoming]): "Answer" and "Decline", in [PingogramChannel.Calls], which rings with the ringtone until
 *   it is answered, declined or gone (`FLAG_INSISTENT`). Its full-screen intent ([fullScreen]) opens the call screen
 *   over the lock screen; where Android does not let Pingogram use full-screen intents (Android 14+, the user's
 *   choice), the caller leaves it out and the notification shows as a heads-up notification.
 * - **Ongoing** ([ongoing]): the call the user is in, outgoing ringing included, with "Hang up" and the call's timer
 *   once connected, in the silent [PingogramChannel.OngoingCalls]; the foreground service's notification.
 * - **Voice chat** ([voiceChat], `docs/adr/0023-group-voice-chats-and-live-streams.md`): the voice chat or live stream
 *   the user is in, as an ongoing call with the chat as its person and "Leave", in the same channel.
 *
 * `feature:calls` hands in the intents: this module does not know its activity and receiver.
 */
@Suppress("LongParameterList") // Injected: the shown account's parts, and every account's for their calls.
class CallNotificationBuilder
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository,
        private val avatars: AvatarIcons,
        // A call of another account names and draws its caller from that account (docs/adr/0031-multiple-accounts.md).
        private val graphs: AccountGraphs,
        private val appearance: AppearanceRepository,
        @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val accountAvatars = ConcurrentHashMap<Int, AvatarIcons>()

        @Suppress("LongParameterList") // The caller, their account, and the notification's four intents.
        suspend fun incoming(
            userId: Long,
            content: PendingIntent,
            answer: PendingIntent,
            decline: PendingIntent,
            fullScreen: Boolean,
            slot: Int? = null,
        ): Notification {
            val notification =
                base(PingogramChannel.Calls, content)
                    .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(userId, slot), decline, answer))
                    .setContentText(context.getString(R.string.call_notification_incoming))
                    .apply { if (fullScreen) setFullScreenIntent(content, true) }
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .build()
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
            return notification
        }

        /**
         * The call the user is in: [status] says where it is ("Ringing…", "Connecting…"); [connectedAtWallMillis], once
         * connected, starts the timer. With [stopSharing], while the user shares the screen, "Stop sharing" beside
         * "Hang up" (`docs/adr/0026-screen-sharing.md`).
         */
        @Suppress("LongParameterList") // The call, what it says, its timer and the notification's three actions.
        suspend fun ongoing(
            userId: Long,
            status: String,
            connectedAtWallMillis: Long?,
            content: PendingIntent,
            hangUp: PendingIntent,
            stopSharing: PendingIntent? = null,
            slot: Int? = null,
        ): Notification =
            base(PingogramChannel.OngoingCalls, content)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(userId, slot), hangUp))
                .apply {
                    if (stopSharing != null) {
                        addAction(
                            NotificationCompat.Action
                                .Builder(
                                    R.drawable.ic_stop_screen_share,
                                    context.getString(R.string.call_notification_stop_sharing),
                                    stopSharing,
                                ).build(),
                        )
                    }
                }.setContentText(status)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .apply {
                    if (connectedAtWallMillis != null) {
                        setWhen(connectedAtWallMillis)
                        setUsesChronometer(true)
                        setShowWhen(true)
                    } else {
                        setShowWhen(false)
                    }
                }.build()

        /** The voice chat of chat [chatId] the user is in: [status] says where it is; the timer once connected. */
        suspend fun voiceChat(
            chatId: Long,
            status: String,
            connectedAtWallMillis: Long?,
            content: PendingIntent,
            leave: PendingIntent,
        ): Notification =
            base(PingogramChannel.OngoingCalls, content)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(chatPerson(chatId), leave))
                .setContentText(status)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .apply {
                    if (connectedAtWallMillis != null) {
                        setWhen(connectedAtWallMillis)
                        setUsesChronometer(true)
                        setShowWhen(true)
                    } else {
                        setShowWhen(false)
                    }
                }.build()

        private fun base(
            channel: PingogramChannel,
            content: PendingIntent,
        ) = NotificationCompat
            .Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)

        /** A voice chat's chat, as the call style's person: its title and photo. */
        private suspend fun chatPerson(chatId: Long): Person {
            val chat = chatRepository.chat(chatId).first()
            val name =
                chat?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.call_notification_voice_chat)
            return Person
                .Builder()
                .setName(name)
                .setIcon(avatars.icon(chat?.photo, chatId, name))
                .setImportant(true)
                .build()
        }

        /** User [userId] of the account in [slot] (null: the shown account), as the call style's person. */
        private suspend fun person(
            userId: Long,
            slot: Int?,
        ): Person {
            val account = slot?.let(graphs::repositories)
            val userRepository = account?.userRepository() ?: userRepository
            val avatars =
                if (account == null) {
                    avatars
                } else {
                    accountAvatars.getOrPut(slot) {
                        AvatarIcons(context, account.fileRepository(), appearance, ioDispatcher)
                    }
                }
            val user = userRepository.user(userId).first() ?: userRepository.getUser(userId).getOrNull()
            val name =
                user?.fullName()?.resolve(context.resources)?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notification_someone)
            return Person
                .Builder()
                .setName(name)
                .setIcon(avatars.icon(user?.profilePhoto, userId, name))
                .setImportant(true)
                .build()
        }
    }

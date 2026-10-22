package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.flow.first
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
 *
 * `feature:calls` hands in the intents: this module does not know its activity and receiver.
 */
class CallNotificationBuilder
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
        private val avatars: AvatarIcons,
    ) {
        suspend fun incoming(
            userId: Long,
            content: PendingIntent,
            answer: PendingIntent,
            decline: PendingIntent,
            fullScreen: Boolean,
        ): Notification {
            val notification =
                base(PingogramChannel.Calls, content)
                    .setStyle(NotificationCompat.CallStyle.forIncomingCall(person(userId), decline, answer))
                    .setContentText(context.getString(R.string.call_notification_incoming))
                    .apply { if (fullScreen) setFullScreenIntent(content, true) }
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .build()
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
            return notification
        }

        /**
         * The call the user is in: [status] says where it is ("Ringing…", "Connecting…"); [connectedAtWallMillis], once
         * connected, starts the timer.
         */
        suspend fun ongoing(
            userId: Long,
            status: String,
            connectedAtWallMillis: Long?,
            content: PendingIntent,
            hangUp: PendingIntent,
        ): Notification =
            base(PingogramChannel.OngoingCalls, content)
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(userId), hangUp))
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

        private suspend fun person(userId: Long): Person {
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

package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A message reminder's notification (the owner's board 29), in the [PingogramChannel.Reminders] channel, so it rings
 * whatever the chat's notifications are set to: "Reminder · Ada" over the message as the chat list previews it (or that
 * it was deleted) and the user's note, a tap opening the chat in its account; "Open" does the same, "Snooze 1 h" moves
 * the reminder an hour on ([ReminderActionReceiver]). One per message, tagged apart from the chats' notifications.
 */
@Singleton
class ReminderNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Posts [reminder]'s notification for the account in [slot], with the permission to post (Android 13+). */
        @SuppressLint("MissingPermission")
        fun post(
            slot: Int,
            reminder: MessageReminder,
            chatTitle: String?,
            preview: String?,
        ) {
            if (!canPost()) return
            val notification = build(slot, reminder, chatTitle, preview)
            NotificationManagerCompat.from(context).notify(TAG, idOf(slot, reminder), notification)
        }

        /** Removes the notification of the reminder on message [messageId] of chat [chatId], account [slot]. */
        fun cancel(
            slot: Int,
            chatId: Long,
            messageId: Long,
        ) {
            NotificationManagerCompat.from(context).cancel(TAG, idOf(slot, chatId, messageId))
        }

        internal fun build(
            slot: Int,
            reminder: MessageReminder,
            chatTitle: String?,
            preview: String?,
        ): Notification {
            val title =
                context.getString(
                    R.string.reminder_notification_title,
                    chatTitle
                        .orEmpty()
                        .ifBlank {
                            context.getString(
                                R.string.notification_unknown_chat,
                            )
                        }.bidiIsolated(),
                )
            val message = preview ?: context.getString(R.string.reminder_notification_deleted)
            val text = listOf(message, reminder.note).filter { it.isNotBlank() }.joinToString("\n")
            val open = NotificationIntents.openChatPendingIntent(context, reminder.chatId, slot, reminder.messageId)
            return NotificationCompat
                .Builder(context, PingogramChannel.Reminders.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setWhen(reminder.at.toEpochMilli())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.reminder_action_open), open)
                .addAction(0, context.getString(R.string.reminder_action_snooze), snooze(slot, reminder))
                .build()
        }

        private fun snooze(
            slot: Int,
            reminder: MessageReminder,
        ): PendingIntent {
            val intent =
                Intent(context, ReminderActionReceiver::class.java)
                    .setAction(ReminderActionReceiver.ACTION_SNOOZE)
                    .putExtra(ReminderActionReceiver.EXTRA_SLOT, slot)
                    .putExtra(ReminderActionReceiver.EXTRA_CHAT, reminder.chatId)
                    .putExtra(ReminderActionReceiver.EXTRA_MESSAGE, reminder.messageId)
            return PendingIntent.getBroadcast(
                context,
                idOf(slot, reminder),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun canPost(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        internal companion object {
            const val TAG = "reminder"

            fun idOf(
                slot: Int,
                reminder: MessageReminder,
            ): Int = idOf(slot, reminder.chatId, reminder.messageId)

            fun idOf(
                slot: Int,
                chatId: Long,
                messageId: Long,
            ): Int = listOf(slot.toLong(), chatId, messageId).hashCode()
        }
    }

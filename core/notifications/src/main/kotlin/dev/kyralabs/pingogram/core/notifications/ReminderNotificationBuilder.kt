package dev.kyralabs.pingogram.core.notifications

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Reminder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A reminder's notification (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29d), in the
 * "Reminders" channel ([PingogramChannel.Reminders]): "Reminder" as its subtext, the chat's avatar and name, who sent
 * the message and what it said, the note below, and "Open" (the chat at the message) and "Snooze 1 hour". One per
 * reminder, tagged [TAG]; a tap opens the message too.
 */
@Singleton
class ReminderNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val avatars: AvatarIcons,
    ) {
        /** Posts [reminder]'s notification for account [slot], with its [chat] where TDLib knows it. */
        @SuppressLint("MissingPermission")
        suspend fun post(
            slot: Int,
            reminder: Reminder,
            chat: Chat?,
        ) {
            if (!canPost()) return
            val id = id(slot, reminder)
            val message =
                listOf(reminder.preview.sender, reminder.preview.text)
                    .filter { it.isNotBlank() }
                    .joinToString(": ")
                    .ifEmpty { context.getString(R.string.reminder_notification_message) }
            val text = listOf(message, reminder.note).filter { it.isNotBlank() }.joinToString("\n")
            val open =
                NotificationIntents.openChatPendingIntent(context, reminder.chatId, slot, reminder.messageId)
            val notification =
                NotificationCompat
                    .Builder(context, PingogramChannel.Reminders.id)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSubText(context.getString(R.string.reminder_notification_subtext))
                    .setContentTitle(chat?.title ?: context.getString(R.string.reminder_notification_subtext))
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setLargeIcon(chat?.let { avatars.icon(it.photo, it.id, it.title).toIcon(context) })
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(
                        NotificationCompat.Action
                            .Builder(null, context.getString(R.string.reminder_action_open), open)
                            .setShowsUserInterface(true)
                            .build(),
                    ).addAction(
                        NotificationCompat.Action
                            .Builder(
                                null,
                                context.getString(R.string.reminder_action_snooze),
                                ReminderActionReceiver.snoozeIntent(
                                    context,
                                    slot,
                                    reminder.chatId,
                                    reminder.messageId,
                                    id,
                                ),
                            ).setShowsUserInterface(false)
                            .build(),
                    ).build()
            NotificationManagerCompat.from(context).notify(TAG, id, notification)
        }

        /** Removes [reminder]'s notification of account [slot]. */
        fun cancel(
            slot: Int,
            reminder: Reminder,
        ) {
            NotificationManagerCompat.from(context).cancel(TAG, id(slot, reminder))
        }

        private fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun id(
            slot: Int,
            reminder: Reminder,
        ): Int = "$slot:${reminder.key}".hashCode()

        internal companion object {
            const val TAG = "reminder"
        }
    }

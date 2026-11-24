package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A birthday reminder's notification (`REQUIREMENTS.md` FR-X18), in the [PingogramChannel.Reminders] channel, so it
 * rings as the message reminders do: "Ada's birthday" with their avatar over "Today is their birthday. Send them a
 * message?", a tap opening the chat with them in its account. A locked chat's ([locked]) names nothing of it and opens
 * the locked chats behind the passcode (`docs/adr/0029-locked-chats.md`). One per contact, tagged apart.
 */
@Singleton
class BirthdayReminderNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Posts user [userId]'s birthday for account [slot], with the permission to post (Android 13+). */
        @SuppressLint("MissingPermission")
        fun post(
            slot: Int,
            userId: Long,
            name: String?,
            locked: Boolean,
            at: Instant,
            icon: IconCompat? = null,
        ) {
            val canPost =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!canPost) return
            val notification = build(slot, userId, name, locked, at, icon)
            NotificationManagerCompat.from(context).notify(TAG, idOf(slot, userId), notification)
        }

        internal fun build(
            slot: Int,
            userId: Long,
            name: String?,
            locked: Boolean,
            at: Instant,
            icon: IconCompat? = null,
        ): Notification {
            val shownName = name?.takeIf { it.isNotBlank() && !locked }
            val title =
                shownName?.let { context.getString(R.string.birthday_notification_title, it.bidiIsolated()) }
                    ?: context.getString(R.string.birthday_notification_title_unnamed)
            // Naming no one, it may show on the lock screen.
            val visibility =
                if (shownName == null) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE
            // A private chat's id is its user's.
            val open =
                if (locked) {
                    NotificationIntents.openLockedChatsPendingIntent(context, slot)
                } else {
                    NotificationIntents.openChatPendingIntent(context, userId, slot)
                }
            return NotificationCompat
                .Builder(context, PingogramChannel.Reminders.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(at.toEpochMilli())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.birthday_notification_text))
                .setContentIntent(open)
                .setVisibility(visibility)
                .apply { if (shownName != null) icon?.let { setLargeIcon(it.toIcon(context)) } }
                .build()
        }

        internal companion object {
            const val TAG = "birthday_reminder"

            fun idOf(
                slot: Int,
                userId: Long,
            ): Int = listOf(slot.toLong(), userId).hashCode()
        }
    }

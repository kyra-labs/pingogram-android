package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The notifications of a chat export (`docs/adr/0021-chat-export.md`), in the "Transfers" channel like the files the
 * user sends and downloads: while it runs, "Exporting Ada", how many messages were read and the progress, with
 * "Cancel" ([ChatExportWorker]'s foreground service notification, [running]); once it ended, a silent note that it is
 * done or why it failed ([finished]), which opens the app. Tapping opens the app. Silent, low importance.
 */
class ChatExportNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** The running export of [chatTitle], with [cancel] as its "Cancel". */
        fun running(
            chatTitle: String,
            progress: ChatExportProgress,
            cancel: PendingIntent,
        ): Notification {
            val fraction = progress.fraction
            val permille = fraction?.let { (it * PROGRESS_MAX).roundToInt() } ?: 0
            val waiting = progress.floodWaitSeconds
            val text =
                if (waiting != null) {
                    context.getString(R.string.chat_export_notification_waiting, waiting)
                } else {
                    messages(progress.messages)
                }
            val builder =
                base()
                    .setContentTitle(context.getString(R.string.chat_export_notification_title, title(chatTitle)))
                    .setContentText(text)
                    .addAction(0, context.getString(R.string.chat_export_notification_cancel), cancel)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                builder.setStyle(
                    NotificationCompat
                        .ProgressStyle()
                        .addProgressSegment(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX))
                        .setProgress(permille)
                        .setProgressIndeterminate(fraction == null),
                )
            } else {
                builder.setProgress(PROGRESS_MAX, permille, fraction == null)
            }
            return builder.build()
        }

        /** Shows [notification] of the running export as [id], with the permission to post (Android 13+). */
        @SuppressLint("MissingPermission")
        fun update(
            id: Int,
            notification: Notification,
        ) {
            if (canPost()) NotificationManagerCompat.from(context).notify(id, notification)
        }

        /**
         * Says that the export of [chatTitle] ended: [result] when it is done, else that it failed. Under its own tag,
         * so ending the foreground service, which removes the running notification, leaves it.
         */
        @SuppressLint("MissingPermission")
        fun finished(
            id: Int,
            chatTitle: String,
            result: ChatExportResult?,
        ) {
            if (!canPost()) return
            val notification =
                base()
                    .setContentTitle(
                        context.getString(
                            if (result != null) {
                                R.string.chat_export_notification_done
                            } else {
                                R.string.chat_export_notification_failed
                            },
                            title(chatTitle),
                        ),
                    ).setContentText(result?.let { messages(it.messageCount) })
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(FINISHED_TAG, id, notification)
        }

        private fun base(): NotificationCompat.Builder =
            NotificationCompat
                .Builder(context, PingogramChannel.Transfers.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentIntent(NotificationIntents.openAppPendingIntent(context))
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)

        private fun canPost(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun title(chatTitle: String): String =
            chatTitle.ifBlank { context.getString(R.string.notification_unknown_chat) }.bidiIsolated()

        private fun messages(count: Int): String =
            context.resources.getQuantityString(
                R.plurals.chat_export_notification_messages,
                count,
                NumberFormat.getIntegerInstance().format(count),
            )

        internal companion object {
            /** The progress bar's steps, as the transfers'. */
            const val PROGRESS_MAX = 1000

            /** The tag of the notification that an export ended. */
            const val FINISHED_TAG = "chat_export"

            /**
             * The notification id of chat [chatId]'s export: one per chat, so exports of several chats show apart,
             * never [TransferNotificationBuilder.NOTIFICATION_ID] or the actions' (1, 2).
             */
            fun notificationId(chatId: Long): Int =
                ID_BASE + ((chatId xor (chatId ushr Int.SIZE_BITS)).toInt() and ID_MASK)

            private const val ID_BASE = 0x1000
            private const val ID_MASK = 0x0FFF_FFFF
        }
    }

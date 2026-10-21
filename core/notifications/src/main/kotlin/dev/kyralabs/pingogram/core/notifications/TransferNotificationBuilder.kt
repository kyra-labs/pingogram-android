package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.Transfers
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The notification of the transfers the user started (`REQUIREMENTS.md` FR-N4), shown by [TransfersWorker] while
 * they run: how many files are on their way, how much of them, and "Cancel" ([TransferCancelReceiver]). Silent, in
 * the low-importance [PingogramChannel.Transfers]; tapping it opens the app.
 *
 * - Android 16+: [NotificationCompat.ProgressStyle], which asks to be promoted to a Live Update with the percentage as
 *   its status bar text. Android promotes it for apps that hold `POST_PROMOTED_NOTIFICATIONS` (`:app`'s manifest),
 *   unless the user turned Live Updates off for Pingogram.
 * - Before: the standard progress bar.
 * - Android 12+ shows a foreground service's notification only after a few seconds, so a transfer that ends sooner
 *   shows none.
 */
class TransferNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun build(transfers: Transfers): Notification {
            val progress = transfers.progress
            val permille = progress?.let { (it * PROGRESS_MAX).roundToInt() } ?: 0
            val builder =
                NotificationCompat
                    .Builder(context, PingogramChannel.Transfers.id)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                    .setContentTitle(title(transfers))
                    .setContentText(sizes(transfers))
                    .setContentIntent(NotificationIntents.openAppPendingIntent(context))
                    .addAction(0, context.getString(R.string.transfer_cancel), cancelIntent())
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                builder
                    .setStyle(
                        NotificationCompat
                            .ProgressStyle()
                            .addProgressSegment(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX))
                            .setProgress(permille)
                            .setProgressIndeterminate(progress == null),
                    ).setRequestPromotedOngoing(true)
                progress?.let { builder.setShortCriticalText(percent(it)) }
            } else {
                builder.setProgress(PROGRESS_MAX, permille, progress == null)
            }
            return builder.build()
        }

        /**
         * Replaces the shown notification with that of [transfers]; on Android 13+ only with the permission to post,
         * checked right here (hence the suppressed lint check).
         */
        @SuppressLint("MissingPermission")
        fun update(transfers: Transfers) {
            val canPost =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (canPost) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(transfers))
        }

        private fun title(transfers: Transfers): String {
            val plural =
                when {
                    transfers.downloads == 0 -> R.plurals.transfer_sending
                    transfers.uploads == 0 -> R.plurals.transfer_downloading
                    else -> R.plurals.transfer_sending_and_downloading
                }
            return context.resources.getQuantityString(plural, transfers.count, transfers.count)
        }

        /** "1.2 MB of 4.5 MB"; null while TDLib knows no size. */
        private fun sizes(transfers: Transfers): String? =
            transfers.takeIf { it.totalSize > 0 }?.let {
                context.getString(
                    R.string.transfer_progress,
                    Formatter.formatShortFileSize(context, it.transferredSize),
                    Formatter.formatShortFileSize(context, it.totalSize),
                )
            }

        private fun percent(progress: Float): String =
            context.getString(R.string.transfer_percent, (progress * PERCENT).toInt())

        private fun cancelIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TransferCancelReceiver::class.java).setAction(TransferCancelReceiver.ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        internal companion object {
            /**
             * The notification's id. WorkManager's foreground service shows it beside [NotificationActionWorker]'s
             * (1); TDLib's notification groups are tagged.
             */
            const val NOTIFICATION_ID = 2

            /** The progress bar's steps: one per mille, finer than a pixel of the bar. */
            const val PROGRESS_MAX = 1000

            private const val PERCENT = 100
        }
    }

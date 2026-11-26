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
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Daily digest's notification (`REQUIREMENTS.md` FR-X23), one for every account, in the quiet
 * [PingogramChannel.DailyDigest] channel: its counts ("12 unread chats · 3 mention you · 1 reminder today") over up to
 * five chat names, or "Tap to catch up" when it names none, a tap opening Catch-up. Naming no chat, it may show on the
 * lock screen. Each digest replaces the one before.
 */
@Singleton
class DailyDigestNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** Posts [digest], unless it is empty, with the permission to post (Android 13+). */
        @SuppressLint("MissingPermission")
        internal fun post(
            digest: DailyDigest,
            at: Instant,
        ) {
            val canPost =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!canPost || digest.isEmpty) return
            NotificationManagerCompat.from(context).notify(TAG, ID, build(digest, at))
        }

        internal fun build(
            digest: DailyDigest,
            at: Instant,
        ): Notification {
            val names = namesOf(digest)
            return NotificationCompat
                .Builder(context, PingogramChannel.DailyDigest.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setWhen(at.toEpochMilli())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentTitle(countsOf(digest))
                .setContentText(names ?: context.getString(R.string.daily_digest_text))
                .setContentIntent(CatchUpIntents.openCatchUpPendingIntent(context))
                .setVisibility(
                    if (names == null) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE,
                ).build()
        }

        /** The digest's counts, the empty ones left out, joined with " · ". */
        private fun countsOf(digest: DailyDigest): String =
            listOf(
                R.plurals.daily_digest_unread to digest.unreadChats,
                R.plurals.daily_digest_mentions to digest.mentionChats,
                R.plurals.daily_digest_reminders to digest.reminders,
            ).filter { (_, count) -> count > 0 }
                .joinToString(SEPARATOR) { (plural, count) ->
                    context.resources.getQuantityString(plural, count, count)
                }

        /** Up to five names, and how many chats more; null when the digest names none. */
        private fun namesOf(digest: DailyDigest): String? {
            // Bidi-isolated, so a right-to-left name keeps the commas where they belong.
            val shown =
                digest.names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.bidiIsolated() } ?: return null
            val more = digest.unreadChats - digest.names.size
            return if (more > 0) {
                context.resources.getQuantityString(R.plurals.daily_digest_more, more, shown, more)
            } else {
                shown
            }
        }

        internal companion object {
            const val TAG = "daily_digest"
            const val ID = 1
            private const val SEPARATOR = " · "
        }
    }

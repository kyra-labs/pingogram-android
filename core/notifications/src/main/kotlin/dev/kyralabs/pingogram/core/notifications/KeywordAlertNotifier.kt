package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.KeywordAlert
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What the keyword alerts of the account in [slot] need from its repositories. */
internal class KeywordAlertAccount(
    val slot: Int,
    val alerts: Flow<KeywordAlert>,
    val hiddenChatIds: () -> Set<Long>,
    val openChatIds: suspend () -> Set<Long>,
    val chat: (Long) -> Flow<Chat?>,
) {
    companion object {
        fun of(
            slot: Int,
            repositories: AccountRepositories,
        ) = KeywordAlertAccount(
            slot = slot,
            alerts = repositories.keywordHitRepository().alerts,
            hiddenChatIds = {
                repositories
                    .chatLockRepository()
                    .hiddenChatIds.value
                    .orEmpty()
            },
            openChatIds = {
                repositories
                    .notificationRepository()
                    .notifications
                    .first()
                    .openChatIds
            },
            chat = { repositories.chatRepository().chat(it) },
        )
    }
}

/**
 * Keyword alerts' notifications (board 30, `docs/DESIGN_SYSTEM.md` §13.13): each account's new matches (its
 * `KeywordHitRepository.alerts`) from a muted chat, while "Alert even when the chat is muted" is on, post a
 * notification in the [PingogramChannel.KeywordAlerts] channel: the chat's name and
 * avatar, "Keyword: launch" as its subtext, the message with the word in bold, a tap or "Open" opening the chat in its
 * account, and "Mark as read". Several group under "Keyword alerts". A match in a chat that is not muted posts nothing
 * more: the chat's own notification already alerts (owner decision to confirm, 2026-11-15). Locked chats and chats on
 * screen post nothing; a notification goes once its message is read.
 *
 * `PingogramApplication` calls [start] once per process, after TDLib. Matching runs on this phone only, on what TDLib
 * announces while Pingogram is connected.
 */
@Singleton
class KeywordAlertNotifier
    internal constructor(
        private val context: Context,
        private val accounts: Flow<List<KeywordAlertAccount>>,
        private val settings: KeywordAlertsRepository,
        private val iconOf: suspend (slot: Int, chat: Chat) -> IconCompat?,
        private val scope: CoroutineScope,
    ) {
        @Inject
        internal constructor(
            @ApplicationContext context: Context,
            graphs: AccountGraphs,
            settings: KeywordAlertsRepository,
            avatars: AvatarIcons,
            @ApplicationScope scope: CoroutineScope,
        ) : this(
            context = context,
            accounts = graphs.accounts.map { list -> list.map { KeywordAlertAccount.of(it.slot, it.repositories) } },
            settings = settings,
            // Another account's photos are not the shown account's files: its avatars are initials.
            iconOf = { slot, chat ->
                avatars.icon(chat.photo.takeIf { slot == graphs.activeSlot.value }, chat.id, chat.title)
            },
            scope = scope,
        )

        private val running = ConcurrentHashMap<Int, Job>()
        private val reads = ConcurrentHashMap<Int, Job>()

        /** Follows every account's matches; call once per process. Returns at once. */
        fun start() {
            scope.launch {
                accounts.collect { list ->
                    val slots = list.mapTo(HashSet()) { it.slot }
                    running.keys.filter { it !in slots }.forEach { slot -> running.remove(slot)?.cancel() }
                    list.filterNot { running.containsKey(it.slot) }.forEach { account ->
                        running[account.slot] = scope.launch { account.alerts.collect { consider(account, it) } }
                    }
                }
            }
        }

        private suspend fun consider(
            account: KeywordAlertAccount,
            alert: KeywordAlert,
        ) {
            val chatId = alert.hit.chatId
            val shows =
                alert.isMuted &&
                    settings.settings.first().alertWhenMuted &&
                    chatId !in account.hiddenChatIds() &&
                    chatId !in account.openChatIds()
            val chat = if (shows) account.chat(chatId).firstOrNull() else null
            if (chat == null || !canPost()) return
            val id = idOf(account.slot, alert)
            post(account.slot, chat, alert, iconOf(account.slot, chat))
            reads.remove(id)?.cancel()
            reads[id] =
                scope.launch {
                    account.chat(chatId).first { it == null || it.lastReadInboxMessageId >= alert.hit.messageId }
                    cancel(account.slot, alert)
                }
        }

        @SuppressLint("MissingPermission") // canPost
        private fun post(
            slot: Int,
            chat: Chat,
            alert: KeywordAlert,
            icon: IconCompat?,
        ) {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(TAG, idOf(slot, alert), build(slot, chat, alert, icon))
            val shown = manager.activeNotifications.count { it.tag == TAG && it.id != SUMMARY_ID }
            if (shown > 1) manager.notify(TAG, SUMMARY_ID, summary(shown))
        }

        private fun cancel(
            slot: Int,
            alert: KeywordAlert,
        ) {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(TAG, idOf(slot, alert))
            reads.remove(idOf(slot, alert))
            if (manager.activeNotifications.none { it.tag == TAG && it.id != SUMMARY_ID }) {
                manager.cancel(
                    TAG,
                    SUMMARY_ID,
                )
            }
        }

        internal fun build(
            slot: Int,
            chat: Chat,
            alert: KeywordAlert,
            icon: IconCompat?,
        ): Notification {
            val open =
                NotificationIntents.openChatPendingIntent(context, chat.id, slot, messageId = alert.hit.messageId)
            val text = highlighted(alert)
            val markAsRead =
                NotificationAction.markAsReadIntent(
                    context = context,
                    slot = slot,
                    chatId = chat.id,
                    messageIds = listOf(alert.hit.messageId),
                    requestCode = idOf(slot, alert),
                )
            return NotificationCompat
                .Builder(context, PingogramChannel.KeywordAlerts.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(chat.title.ifBlank { context.getString(R.string.notification_unknown_chat) })
                .setSubText(context.getString(R.string.keyword_alert_subtext, alert.hit.word.bidiIsolated()))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .apply { icon?.let { setLargeIcon(it.toIcon(context)) } }
                .setWhen(alert.hit.date * MILLIS)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.keyword_alert_open), open)
                .addAction(0, context.getString(R.string.keyword_alert_mark_as_read), markAsRead)
                .build()
        }

        private fun summary(count: Int): Notification =
            NotificationCompat
                .Builder(context, PingogramChannel.KeywordAlerts.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(context.getString(R.string.keyword_alert_group))
                .setContentText(context.resources.getQuantityString(R.plurals.keyword_alert_group_count, count, count))
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()

        private fun canPost(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        internal companion object {
            const val TAG = "keyword_alert"
            const val GROUP = "dev.kyralabs.pingogram.KEYWORD_ALERTS"
            const val SUMMARY_ID = 0
            private const val MILLIS = 1_000L

            fun idOf(
                slot: Int,
                alert: KeywordAlert,
            ): Int =
                listOf(slot.toLong(), alert.hit.chatId, alert.hit.messageId).hashCode().takeIf { it != SUMMARY_ID } ?: 1

            /** The message with its word in bold, so the notification names it by more than its place. */
            fun highlighted(alert: KeywordAlert): CharSequence {
                val text = SpannableString(alert.text)
                if (alert.start in 0..<alert.end && alert.end <= alert.text.length) {
                    text.setSpan(StyleSpan(Typeface.BOLD), alert.start, alert.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return text
            }
        }
    }

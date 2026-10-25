package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Draws [ConversationNotification]s the way Android shows conversations (`REQUIREMENTS.md` FR-N2):
 * `NotificationCompat.MessagingStyle` with a [Person] per sender, linked to the chat's conversation shortcut, with
 * the inline Reply and Mark as read actions (both with their semantic actions, so Android Auto, Wear OS and the
 * system's smart replies understand them), all in one bundle under a summary ([summary]).
 *
 * **Alerting.** Each notification of a chat is posted again whenever its group changes, and only new messages may make
 * a sound. A notification that must not alert (an update, a message sent silently, a chat whose sound is off) sets
 * `GROUP_ALERT_SUMMARY`, and the summary `GROUP_ALERT_CHILDREN`: Android then silences that child (no sound, vibration
 * or pop-up; `Notification.suppressAlertingDueToGrouping`), and the summary never alerts itself. This keeps the
 * channel's sound for the notifications that do alert, unlike a separate silent channel, which users would have to
 * set up twice, and unlike `setOnlyAlertOnce`, which would silence every later message of the chat too.
 */
@Singleton
internal class ConversationNotificationBuilder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val me: Person by lazy {
            Person
                .Builder()
                .setKey(ME_KEY)
                .setName(context.getString(R.string.notification_you))
                .build()
        }

        /** The notification of [conversation], making a sound only if [alert], linked to shortcut [shortcutId]. */
        fun build(
            conversation: ConversationNotification,
            alert: Boolean,
            shortcutId: String?,
        ): Notification {
            val style =
                NotificationCompat
                    .MessagingStyle(me)
                    .setGroupConversation(conversation.isGroupConversation)
                    .setConversationTitle(conversation.title.takeIf { conversation.isGroupConversation })
            conversation.messages.forEach { message ->
                style.addMessage(
                    NotificationCompat.MessagingStyle.Message(message.text, message.timeMillis, message.sender),
                )
            }
            val count = conversation.totalCount
            return NotificationCompat
                .Builder(context, conversation.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setStyle(style)
                .setCategory(
                    if (conversation.isCall) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE,
                ).setWhen(conversation.messages.lastOrNull()?.timeMillis ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setNumber(count)
                .setAutoCancel(true)
                .setContentIntent(NotificationIntents.openChatPendingIntent(context, conversation.chatId))
                .setDeleteIntent(NotificationAction.dismissIntent(context, conversation))
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(
                    if (alert) NotificationCompat.GROUP_ALERT_ALL else NotificationCompat.GROUP_ALERT_SUMMARY,
                ).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(conversation.channel, count))
                .apply {
                    if (shortcutId != null) {
                        setShortcutId(shortcutId)
                        setLocusId(LocusIdCompat(shortcutId))
                    }
                    if (conversation.canReply) addAction(replyAction(conversation))
                    if (conversation.messageIds.isNotEmpty()) addAction(markAsReadAction(conversation))
                }.build()
        }

        /**
         * The one notification of every locked chat (`docs/adr/0029-locked-chats.md`): "New message", without the
         * chat's name, sender, picture or text, in the Private chats channel whatever the chat (a channel would tell
         * its kind), with no conversation shortcut and no actions. Its tap opens the locked chats behind the passcode.
         * It alerts as a conversation's notification does, only if [alert].
         */
        fun locked(alert: Boolean): Notification =
            NotificationCompat
                .Builder(context, PingogramChannel.PrivateChats.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentText(context.getString(R.string.notification_new_message))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(NotificationIntents.openLockedChatsPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setGroupAlertBehavior(
                    if (alert) NotificationCompat.GROUP_ALERT_ALL else NotificationCompat.GROUP_ALERT_SUMMARY,
                ).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

        /**
         * The summary of the bundle, which Android shows in place of the conversations on surfaces without room for
         * them (a watch, a collapsed group); it opens the app. It never alerts.
         */
        fun summary(totalCount: Int): Notification =
            NotificationCompat
                .Builder(context, PingogramChannel.Other.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentText(newMessages(totalCount))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(NotificationIntents.openAppPendingIntent(context))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()

        /**
         * What [NotificationActionWorker] shows while it runs as a foreground service (expedited work before
         * Android 12); silent.
         */
        fun progress(): Notification =
            NotificationCompat
                .Builder(context, PingogramChannel.Other.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentText(context.getString(R.string.notification_working))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSilent(true)
                .setOngoing(true)
                .build()

        /** The lock screen's version when the user hides sensitive content there: how many messages, from whom not. */
        private fun publicVersion(
            channel: PingogramChannel,
            count: Int,
        ): Notification =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentText(newMessages(count))
                .build()

        private fun newMessages(count: Int): String =
            context.resources.getQuantityString(R.plurals.notification_new_messages, count, count)

        private fun replyAction(conversation: ConversationNotification): NotificationCompat.Action {
            val input =
                RemoteInput
                    .Builder(NotificationAction.KEY_TEXT)
                    .setLabel(context.getString(R.string.notification_reply_label))
                    .build()
            return NotificationCompat.Action
                .Builder(
                    IconCompat.createWithResource(context, UiR.drawable.ic_reply),
                    context.getString(R.string.notification_reply),
                    NotificationAction.replyIntent(context, conversation),
                ).addRemoteInput(input)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .setAllowGeneratedReplies(true)
                .build()
        }

        private fun markAsReadAction(conversation: ConversationNotification): NotificationCompat.Action =
            NotificationCompat.Action
                .Builder(
                    IconCompat.createWithResource(context, UiR.drawable.ic_mark_chat_read),
                    context.getString(R.string.notification_mark_as_read),
                    NotificationAction.markAsReadIntent(context, conversation),
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()

        private companion object {
            /** The one bundle of every chat's notification. */
            const val GROUP_KEY = "dev.kyralabs.pingogram.MESSAGES"

            const val ME_KEY = "me"
        }
    }

package dev.kyralabs.pingogram.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.Data

/**
 * What the user does to a notification from outside the app: [Reply] and [MarkAsRead] (its actions), [Dismiss] (its
 * delete intent). Each travels from its pending intent ([replyIntent], [markAsReadIntent], [dismissIntent]) to
 * [NotificationActionReceiver] as an intent ([fromIntent]), then to [NotificationActionWorker] as work data ([toData],
 * [fromData]). `toString()` leaves the reply's text out.
 */
internal sealed interface NotificationAction {
    /** The TDLib notification group acted on, whose notification shows it. */
    val groupId: Int

    /** Sends [text] to chat [chatId], quoting [replyToMessageId] if not null, then reads [messageIds]. */
    data class Reply(
        override val groupId: Int,
        val chatId: Long,
        val messageIds: List<Long>,
        val replyToMessageId: Long?,
        val text: String,
    ) : NotificationAction {
        override fun toString() = "Reply(groupId=$groupId, chatId=$chatId, ${messageIds.size} messages)"
    }

    /** Reads [messageIds] of chat [chatId]. */
    data class MarkAsRead(
        override val groupId: Int,
        val chatId: Long,
        val messageIds: List<Long>,
    ) : NotificationAction

    /** The user swiped the notification away, up to notification [maxNotificationId]. */
    data class Dismiss(
        override val groupId: Int,
        val maxNotificationId: Int,
    ) : NotificationAction

    /** The action as work data for [NotificationActionWorker]. */
    fun toData(): Data =
        Data
            .Builder()
            .putInt(KEY_GROUP_ID, groupId)
            .apply {
                when (val action = this@NotificationAction) {
                    is Reply -> {
                        putString(KEY_ACTION, ACTION_REPLY)
                        putLong(KEY_CHAT_ID, action.chatId)
                        putLongArray(KEY_MESSAGE_IDS, action.messageIds.toLongArray())
                        putLong(KEY_REPLY_TO, action.replyToMessageId ?: NONE)
                        putString(KEY_TEXT, action.text)
                    }

                    is MarkAsRead -> {
                        putString(KEY_ACTION, ACTION_MARK_AS_READ)
                        putLong(KEY_CHAT_ID, action.chatId)
                        putLongArray(KEY_MESSAGE_IDS, action.messageIds.toLongArray())
                    }

                    is Dismiss -> {
                        putString(KEY_ACTION, ACTION_DISMISS)
                        putInt(KEY_MAX_NOTIFICATION_ID, action.maxNotificationId)
                    }
                }
            }.build()

    companion object {
        /** The key of the reply's text in the reply action's `RemoteInput`. */
        const val KEY_TEXT = "text"

        /** The action in [data]; null for data of no action, or a reply without text. */
        fun fromData(data: Data): NotificationAction? =
            read(
                action = data.getString(KEY_ACTION),
                groupId = data.getInt(KEY_GROUP_ID, NONE.toInt()),
                chatId = data.getLong(KEY_CHAT_ID, NONE),
                messageIds = data.getLongArray(KEY_MESSAGE_IDS),
                replyTo = data.getLong(KEY_REPLY_TO, NONE),
                maxNotificationId = data.getInt(KEY_MAX_NOTIFICATION_ID, NONE.toInt()),
                text = data.getString(KEY_TEXT),
            )

        /** The action of a broadcast from one of the pending intents, with the text the user typed for a reply. */
        fun fromIntent(intent: Intent): NotificationAction? =
            read(
                action = intent.action?.removePrefix(INTENT_ACTION_PREFIX),
                groupId = intent.getIntExtra(KEY_GROUP_ID, NONE.toInt()),
                chatId = intent.getLongExtra(KEY_CHAT_ID, NONE),
                messageIds = intent.getLongArrayExtra(KEY_MESSAGE_IDS),
                replyTo = intent.getLongExtra(KEY_REPLY_TO, NONE),
                maxNotificationId = intent.getIntExtra(KEY_MAX_NOTIFICATION_ID, NONE.toInt()),
                text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString(),
            )

        /**
         * The broadcast of the reply action of [conversation]'s notification. Mutable: the notification's `RemoteInput`
         * adds the text the user typed ([KEY_TEXT]).
         */
        fun replyIntent(
            context: Context,
            conversation: ConversationNotification,
        ): PendingIntent =
            broadcast(context, ACTION_REPLY, conversation.groupId, PendingIntent.FLAG_MUTABLE) {
                putExtra(KEY_CHAT_ID, conversation.chatId)
                putExtra(KEY_MESSAGE_IDS, conversation.messageIds.toLongArray())
                putExtra(KEY_REPLY_TO, conversation.replyToMessageId ?: NONE)
            }

        /** The broadcast of the "Mark as read" action of [conversation]'s notification. */
        fun markAsReadIntent(
            context: Context,
            conversation: ConversationNotification,
        ): PendingIntent =
            broadcast(context, ACTION_MARK_AS_READ, conversation.groupId, PendingIntent.FLAG_IMMUTABLE) {
                putExtra(KEY_CHAT_ID, conversation.chatId)
                putExtra(KEY_MESSAGE_IDS, conversation.messageIds.toLongArray())
            }

        /** The broadcast when the user swipes [conversation]'s notification away. */
        fun dismissIntent(
            context: Context,
            conversation: ConversationNotification,
        ): PendingIntent =
            broadcast(context, ACTION_DISMISS, conversation.groupId, PendingIntent.FLAG_IMMUTABLE) {
                putExtra(KEY_MAX_NOTIFICATION_ID, conversation.maxNotificationId)
            }

        /**
         * An explicit broadcast to [NotificationActionReceiver]; one per action and group (the request code), kept up
         * to date with the notification.
         */
        private fun broadcast(
            context: Context,
            action: String,
            groupId: Int,
            mutability: Int,
            extras: Intent.() -> Unit,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(INTENT_ACTION_PREFIX + action)
                    .putExtra(KEY_GROUP_ID, groupId)
                    .apply(extras)
            return PendingIntent.getBroadcast(context, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
        }

        @Suppress("LongParameterList") // Every field of every action, read from an intent or work data alike.
        private fun read(
            action: String?,
            groupId: Int,
            chatId: Long,
            messageIds: LongArray?,
            replyTo: Long,
            maxNotificationId: Int,
            text: String?,
        ): NotificationAction? {
            val ids = messageIds?.toList().orEmpty()
            return when (action) {
                ACTION_REPLY -> {
                    text?.takeIf { it.isNotBlank() }?.let {
                        Reply(groupId, chatId, ids, replyTo.takeIf { id -> id != NONE }, it)
                    }
                }

                ACTION_MARK_AS_READ -> {
                    MarkAsRead(groupId, chatId, ids)
                }

                ACTION_DISMISS -> {
                    Dismiss(groupId, maxNotificationId)
                }

                else -> {
                    null
                }
            }
        }

        private const val INTENT_ACTION_PREFIX = "dev.kyralabs.pingogram.notification."
        private const val ACTION_REPLY = "REPLY"
        private const val ACTION_MARK_AS_READ = "MARK_AS_READ"
        private const val ACTION_DISMISS = "DISMISS"

        private const val KEY_ACTION = "action"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_MESSAGE_IDS = "message_ids"
        private const val KEY_REPLY_TO = "reply_to"
        private const val KEY_MAX_NOTIFICATION_ID = "max_notification_id"

        // TDLib's ids of chats, messages and notifications are never 0.
        private const val NONE = 0L
    }
}

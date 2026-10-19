package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The notifications to show now (`NotificationRepository`, `REQUIREMENTS.md` FR-N1–N2): what TDLib's notification
 * engine keeps active, after it applied mutes, exceptions and read messages (`docs/ARCHITECTURE.md`, Notifications).
 * Plain data; a new snapshot replaces it on every change.
 */
data class ActiveNotifications(
    /** The active groups by [NotificationGroup.id]; a group with no notification left is not here. */
    val groups: ImmutableMap<Int, NotificationGroup> = persistentMapOf(),
    /**
     * The chats whose conversation is on screen (`ChatRepository.openChat`): their notifications must not alert, the
     * user sees the messages arrive.
     */
    val openChatIds: ImmutableSet<Long> = persistentSetOf(),
    /**
     * TDLib has reported the notifications that stay active from before the app started (or a change since), so a
     * notification still on screen without a group here is stale and must go.
     */
    val isRestored: Boolean = false,
)

/**
 * A group of notifications of one chat (td_api.tl `notificationGroup`), shown as one notification: the messages of a
 * chat, the mentions of the current user in it, a new secret chat, or calls.
 */
data class NotificationGroup(
    /** TDLib's id of the group, unique among the active groups. */
    val id: Int,
    val type: NotificationGroupType,
    val chatId: Long,
    /** Unread notifications in the group; can be more than [notifications] holds. */
    val totalCount: Int,
    /** Oldest first; never empty. */
    val notifications: ImmutableList<ChatNotification>,
)

/** What a [NotificationGroup] is about (td_api.tl `NotificationGroupType`). */
enum class NotificationGroupType {
    /** New messages of a chat, other than mentions. */
    Messages,

    /** Messages that mention or reply to the current user, and pinned messages. */
    Mentions,

    /** A secret chat was created. */
    SecretChat,

    /** Calls. */
    Calls,
}

/**
 * One notification of a [NotificationGroup] (td_api.tl `notification`); [date] is a Unix timestamp in seconds.
 *
 * [alerts] says whether it arrived just now and may play a sound, vibrate and pop up: TDLib announced it as new, it
 * was not sent silently, and the chat's notification sound is not off. Notifications shown again after the app
 * started, and changes of shown ones, never alert.
 */
data class ChatNotification(
    val id: Int,
    val date: Long,
    val alerts: Boolean,
    val content: NotificationContent,
)

/** What a [ChatNotification] reports (td_api.tl `NotificationType`). */
sealed interface NotificationContent {
    /** A new [message]; its content may be shown only if [showPreview] (the chat's notification setting). */
    data class NewMessage(
        val message: Message,
        val showPreview: Boolean,
    ) : NotificationContent

    /** A secret chat was created. */
    data object NewSecretChat : NotificationContent

    /** A call, by TDLib's id of it. */
    data class NewCall(
        val callId: Int,
    ) : NotificationContent

    /**
     * A message known only from a push notification, not loaded yet: message [messageId], from [sender] (null if
     * unknown) named [senderName], with its [text] or caption if it has one and TDLib may show it, else null.
     */
    data class NewPushMessage(
        val messageId: Long,
        val sender: MessageSender?,
        val senderName: String,
        val isOutgoing: Boolean,
        val text: String?,
    ) : NotificationContent {
        override fun toString() = "NewPushMessage(messageId=$messageId, sender=$sender, isOutgoing=$isOutgoing, …)"
    }
}

/** The ids of the messages that [this] group's notifications report, oldest first. */
val NotificationGroup.messageIds: List<Long>
    get() =
        notifications.mapNotNull { notification ->
            when (val content = notification.content) {
                is NotificationContent.NewMessage -> content.message.id
                is NotificationContent.NewPushMessage -> content.messageId
                else -> null
            }
        }

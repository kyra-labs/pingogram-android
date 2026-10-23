package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatNotification
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * The group, with notifications that never alert ([ChatNotification.alerts]): TDLib reports active groups only when
 * they were shown before. Null for a group type added by a newer TDLib, or a group without a notification Pingogram
 * can show.
 */
internal fun TdApi.NotificationGroup.toNotificationGroup(): NotificationGroup? {
    val type = type.toNotificationGroupType()
    val notifications =
        notifications
            .mapNotNull { it.toChatNotification(alerts = false) }
            .sortedBy { it.id }
            .toImmutableList()
    if (type == null || notifications.isEmpty()) return null
    return NotificationGroup(id, type, chatId, totalCount, notifications)
}

/** Null for a type added by a newer TDLib. */
internal fun TdApi.NotificationGroupType.toNotificationGroupType(): NotificationGroupType? =
    when (this) {
        is TdApi.NotificationGroupTypeMessages -> NotificationGroupType.Messages
        is TdApi.NotificationGroupTypeMentions -> NotificationGroupType.Mentions
        is TdApi.NotificationGroupTypeSecretChat -> NotificationGroupType.SecretChat
        is TdApi.NotificationGroupTypeCalls -> NotificationGroupType.Calls
        else -> null
    }

/** The notification, alerting only if [alerts] and it was not sent silently; null for a type of a newer TDLib. */
internal fun TdApi.Notification.toChatNotification(alerts: Boolean): ChatNotification? {
    val content = type.toNotificationContent() ?: return null
    return ChatNotification(id = id, date = date.toLong(), alerts = alerts && !isSilent, content = content)
}

private fun TdApi.NotificationType.toNotificationContent(): NotificationContent? =
    when (this) {
        is TdApi.NotificationTypeNewMessage -> {
            NotificationContent.NewMessage(message.toMessage(), showPreview)
        }

        is TdApi.NotificationTypeNewSecretChat -> {
            NotificationContent.NewSecretChat
        }

        is TdApi.NotificationTypeNewCall -> {
            NotificationContent.NewCall(callId)
        }

        is TdApi.NotificationTypeNewPushMessage -> {
            NotificationContent.NewPushMessage(
                messageId = messageId,
                sender = senderId?.toMessageSender(),
                senderName = senderName,
                isOutgoing = isOutgoing,
                text = content.text(),
            )
        }

        else -> {
            null
        }
    }

/**
 * The text of a push message: a text message's text, or a caption. Null for everything else, which shows as "New
 * message" until the `gms` flavor's push notifications (`REQUIREMENTS.md` FR-N3) name the kinds of media.
 */
private fun TdApi.PushMessageContent.text(): String? =
    when (this) {
        is TdApi.PushMessageContentText -> text
        is TdApi.PushMessageContentPhoto -> caption
        is TdApi.PushMessageContentVideo -> caption
        is TdApi.PushMessageContentAnimation -> caption
        else -> null
    }?.takeIf { it.isNotBlank() }

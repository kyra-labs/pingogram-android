package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/**
 * TdApi objects of TDLib's notification engine, for the tests of notifications: group 7 of chat 100 by default, with
 * messages from [TdApiFixtures.message]. Dates are Unix seconds.
 */
object NotificationFixtures {
    /** A notification of a new [message] (incoming text in chat 100 by default), with sound unless [isSilent]. */
    fun messageNotification(
        id: Int = 1,
        message: TdApi.Message = TdApiFixtures.message(),
        showPreview: Boolean = true,
        isSilent: Boolean = false,
        date: Int = message.date,
    ) = TdApi.Notification(id, date, isSilent, TdApi.NotificationTypeNewMessage(message, showPreview))

    /**
     * Notifications [added] to group [groupId] and [removed] from it; [soundId] 0 means the chat's sound is off.
     * [totalCount] defaults to what the group holds after the update when it held nothing before.
     */
    fun updateNotificationGroup(
        groupId: Int = 7,
        chatId: Long = 100,
        type: TdApi.NotificationGroupType = TdApi.NotificationGroupTypeMessages(),
        added: List<TdApi.Notification> = emptyList(),
        removed: List<Int> = emptyList(),
        soundId: Long = 1,
        totalCount: Int = added.size,
    ) = TdApi.UpdateNotificationGroup(
        groupId,
        type,
        chatId,
        chatId,
        soundId,
        totalCount,
        added.toTypedArray(),
        removed.toIntArray(),
    )

    /** An active group as `updateActiveNotifications` reports it after a start. */
    fun notificationGroup(
        groupId: Int = 7,
        chatId: Long = 100,
        type: TdApi.NotificationGroupType = TdApi.NotificationGroupTypeMessages(),
        notifications: List<TdApi.Notification> = listOf(messageNotification()),
    ) = TdApi.NotificationGroup(groupId, type, chatId, notifications.size, notifications.toTypedArray())

    fun updateActiveNotifications(vararg groups: TdApi.NotificationGroup) = TdApi.UpdateActiveNotifications(groups)
}

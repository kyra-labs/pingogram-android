package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import kotlinx.coroutines.flow.Flow

/**
 * The notifications TDLib wants shown (`REQUIREMENTS.md` FR-N1–N2, `docs/ARCHITECTURE.md` Notifications), and what
 * the user does to them from outside the app: reply, mark as read, dismiss. TDLib's notification engine decides what
 * to show (it applies mutes, exceptions and read messages, and removes what was read anywhere); `core:notifications`
 * only renders [notifications]. Everything is dropped when the user logs out.
 *
 * Every action fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time,
 * `NotFound` for a chat or message TDLib does not know, or another one when TDLib refuses it.
 */
interface NotificationRepository {
    /** The active notifications, then every change. */
    val notifications: Flow<ActiveNotifications>

    /**
     * Whether TDLib still has notification updates to send (delayed ones, or ones it has not received yet), then every
     * change: a process woken for a push notification keeps running until this is false.
     */
    val hasPendingNotifications: Flow<Boolean>

    /**
     * Sends [text] to chat [chatId] as a reply to message [replyToMessageId] (none if null), keeping the chat's draft.
     * Succeeds once TDLib accepted the message; it goes out when TDLib is connected, also after a restart.
     */
    suspend fun reply(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
    ): Outcome<Unit>

    /**
     * Marks messages [messageIds] of chat [chatId], and every message before them, as read, though the chat is not
     * open; TDLib then removes their notifications.
     */
    suspend fun markAsRead(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit>

    /**
     * Tells TDLib that the user dismissed group [groupId] up to notification [maxNotificationId], so it is not shown
     * again; newer notifications still are.
     */
    suspend fun dismiss(
        groupId: Int,
        maxNotificationId: Int,
    ): Outcome<Unit>
}

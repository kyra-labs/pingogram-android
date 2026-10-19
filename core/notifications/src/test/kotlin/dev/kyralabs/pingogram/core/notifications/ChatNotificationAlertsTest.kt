package dev.kyralabs.pingogram.core.notifications

import dev.kyralabs.pingogram.core.domain.model.ChatNotification
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatNotificationAlertsTest {
    private fun notification(
        id: Int,
        alerts: Boolean = true,
    ) = ChatNotification(id, date = 0, alerts = alerts, content = NotificationContent.NewSecretChat)

    private fun group(vararg notifications: ChatNotification) =
        NotificationGroup(
            1,
            NotificationGroupType.Messages,
            100,
            notifications.size,
            notifications.toList().toImmutableList(),
        )

    @Test
    fun `a new group alerts when one of its notifications does`() {
        assertTrue(
            ChatNotificationAlerts.shouldAlert(
                previous = null,
                group(notification(1, alerts = false), notification(2)),
            ),
        )
    }

    @Test
    fun `notifications seen before never alert again`() {
        val shown = group(notification(1))

        assertFalse(ChatNotificationAlerts.shouldAlert(shown, shown))
        assertFalse(ChatNotificationAlerts.shouldAlert(group(notification(1), notification(2)), group(notification(2))))
    }

    @Test
    fun `a new notification alerts only if it may`() {
        val before = group(notification(1))

        assertTrue(ChatNotificationAlerts.shouldAlert(before, group(notification(1), notification(2))))
        assertFalse(ChatNotificationAlerts.shouldAlert(before, group(notification(1), notification(2, alerts = false))))
    }
}

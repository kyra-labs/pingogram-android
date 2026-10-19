package dev.kyralabs.pingogram.core.notifications

import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationActionTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `every action survives the trip through work data`() {
        val actions =
            listOf(
                NotificationAction.Reply(
                    7,
                    chatId = 100,
                    messageIds = listOf(10, 11),
                    replyToMessageId = 11,
                    text = "Hi",
                ),
                NotificationAction.Reply(
                    7,
                    chatId = 100,
                    messageIds = listOf(10),
                    replyToMessageId = null,
                    text = "Hi",
                ),
                NotificationAction.MarkAsRead(7, chatId = 100, messageIds = listOf(10, 11)),
                NotificationAction.Dismiss(7, maxNotificationId = 3),
            )

        actions.forEach { assertEquals(it, NotificationAction.fromData(it.toData())) }
    }

    @Test
    fun `work data of no action reads as none`() {
        assertNull(NotificationAction.fromData(Data.EMPTY))
    }

    @Test
    fun `the reply broadcast carries the conversation and the text the user typed`() {
        val conversation = conversation(replyToMessageId = 11)
        val broadcast = shadowOf(NotificationAction.replyIntent(context, conversation)).savedIntent
        val typed = Intent(broadcast)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(NotificationAction.KEY_TEXT).build()),
            typed,
            Bundle().apply { putCharSequence(NotificationAction.KEY_TEXT, "On my way") },
        )

        assertEquals(NotificationActionReceiver::class.java.name, broadcast.component?.className)
        assertEquals(
            NotificationAction.Reply(
                7,
                chatId = 100,
                messageIds = listOf(10, 11),
                replyToMessageId = 11,
                text = "On my way",
            ),
            NotificationAction.fromIntent(typed),
        )
    }

    @Test
    fun `a reply without text is no action`() {
        val broadcast = shadowOf(NotificationAction.replyIntent(context, conversation())).savedIntent

        assertNull(NotificationAction.fromIntent(broadcast))
    }

    @Test
    fun `mark as read and dismiss broadcasts carry their group`() {
        val conversation = conversation()

        assertEquals(
            NotificationAction.MarkAsRead(7, chatId = 100, messageIds = listOf(10, 11)),
            NotificationAction.fromIntent(
                shadowOf(NotificationAction.markAsReadIntent(context, conversation)).savedIntent,
            ),
        )
        assertEquals(
            NotificationAction.Dismiss(7, maxNotificationId = 2),
            NotificationAction.fromIntent(
                shadowOf(NotificationAction.dismissIntent(context, conversation)).savedIntent,
            ),
        )
    }

    private fun conversation(replyToMessageId: Long? = null) =
        ConversationNotification(
            groupId = 7,
            chatId = 100,
            channel = PingogramChannel.PrivateChats,
            title = "Ada",
            isGroupConversation = false,
            isCall = false,
            messages = emptyList(),
            totalCount = 2,
            canReply = true,
            replyToMessageId = replyToMessageId,
            messageIds = listOf(10, 11),
            maxNotificationId = 2,
            chatIcon =
                androidx.core.graphics.drawable.IconCompat.createWithResource(
                    context,
                    R.drawable.ic_notification,
                ),
            chatPerson = null,
        )
}

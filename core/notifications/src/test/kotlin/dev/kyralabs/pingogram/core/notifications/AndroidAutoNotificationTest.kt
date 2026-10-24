package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The conversation notifications against what Android Auto (and Wear OS, which bridges the same notifications) asks of
 * a messaging app (docs/ANDROID_AUTO.md): a `MessagingStyle` with a named user and a named sender for every message, a
 * Reply action with a `RemoteInput`, the reply semantic action and no user interface of its own, and a Mark as read
 * action with its semantic action and no user interface, both handled by a broadcast rather than an activity.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidAutoNotificationTest {
    private suspend fun TestScope.notification(): Notification {
        val harness = NotificationsHarness(this)
        harness.client.authState.value = AuthState.Ready
        harness.renderer.start()
        runCurrent()
        harness.client.emit(updateUser(user(id = 42, firstName = "Ada", lastName = "Lovelace")))
        harness.client.emit(updateNewChat(chat(id = 100, title = "Ada Lovelace")))
        harness.client.emit(
            updateNotificationGroup(
                added =
                    listOf(
                        messageNotification(id = 1, message = message(id = 10)),
                        messageNotification(id = 2, message = message(id = 11)),
                    ),
            ),
        )
        runCurrent()
        return checkNotNull(harness.posted(7))
    }

    private fun Notification.action(semanticAction: Int): NotificationCompat.Action =
        (0 until NotificationCompat.getActionCount(this))
            .mapNotNull { NotificationCompat.getAction(this, it) }
            .single { it.semanticAction == semanticAction }

    @Test
    fun `the style names the user and the sender of every message`() =
        runTest {
            val notification = notification()
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)!!

            assertFalse(style.user.name.isNullOrBlank())
            assertEquals(2, style.messages.size)
            assertTrue(style.messages.all { !it.person?.name.isNullOrBlank() })
        }

    @Test
    fun `reply takes a voice or typed answer, in the background`() =
        runTest {
            val reply = notification().action(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)

            assertEquals(NotificationAction.KEY_TEXT, reply.remoteInputs!!.single().resultKey)
            assertFalse(reply.showsUserInterface)
            assertTrue(shadowOf(reply.actionIntent).isBroadcastIntent)
        }

    @Test
    fun `mark as read works in the background`() =
        runTest {
            val markAsRead = notification().action(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)

            assertFalse(markAsRead.showsUserInterface)
            assertTrue(shadowOf(markAsRead.actionIntent).isBroadcastIntent)
        }
}

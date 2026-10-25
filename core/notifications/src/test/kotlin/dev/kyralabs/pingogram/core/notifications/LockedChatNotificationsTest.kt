package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import androidx.core.content.pm.ShortcutManagerCompat
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Locked chats' notifications (`docs/adr/0029-locked-chats.md`): one "New message" for all of them, which names no
 * chat, sender or text, has no conversation shortcut, and replaces a notification that showed before the chat was
 * locked, unlocked or not.
 */
@RunWith(RobolectricTestRunner::class)
class LockedChatNotificationsTest {
    /** A logged-in account with Ada (user 42) and her private chat 100. */
    private suspend fun TestScope.started(): NotificationsHarness {
        val harness = NotificationsHarness(this)
        harness.client.authState.value = AuthState.Ready
        harness.renderer.start()
        runCurrent()
        harness.client.emit(updateUser(user(id = 42, firstName = "Ada", lastName = "Lovelace")))
        harness.client.emit(updateNewChat(chat(id = 100, title = "Ada Lovelace")))
        runCurrent()
        return harness
    }

    private suspend fun NotificationsHarness.newMessage(id: Int = 1) {
        client.emit(
            updateNotificationGroup(
                added =
                    listOf(
                        messageNotification(
                            id = id,
                            message = message(id = 10L + id, content = messageText("Meet at 6")),
                        ),
                    ),
            ),
        )
    }

    /** Everything a notification says, in every text and extra Android keeps. */
    private fun Notification.everything(): String =
        extras.keySet().joinToString(" ") { key -> extras.get(key).toString() } + " $tickerText"

    private fun NotificationsHarness.assertNothingNamed() {
        all().forEach { notification ->
            val text = notification.everything()
            assertTrue(text, "Ada" !in text && "Lovelace" !in text && "Meet at 6" !in text)
            assertNull(notification.shortcutId)
        }
    }

    @Test
    fun `a locked chat's message shows only as new message, alerting, without a conversation`() =
        runTest {
            val harness = started()
            harness.chatLock.lock(setOf(100L))
            runCurrent()

            harness.newMessage()
            runCurrent()

            assertNull(harness.posted(7))
            val locked = checkNotNull(harness.lockedNotification())
            assertEquals("New message", locked.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(PingogramChannel.PrivateChats.id, locked.channelId)
            assertEquals(Notification.GROUP_ALERT_ALL, locked.groupAlertBehavior)
            assertEquals(0, locked.actions?.size ?: 0)
            harness.assertNothingNamed()
        }

    @Test
    fun `unlocked chats still notify without their name`() =
        runTest {
            val harness = started()
            harness.chatLock.lock(setOf(100L))
            harness.chatLock.setUnlocked(true)
            runCurrent()

            harness.newMessage()
            runCurrent()

            assertNull(harness.posted(7))
            assertNotNull(harness.lockedNotification())
            harness.assertNothingNamed()
        }

    @Test
    fun `locking a chat replaces its notification and removes its conversation shortcut`() =
        runTest {
            val harness = started()
            harness.newMessage()
            runCurrent()
            assertNotNull(harness.posted(7))

            harness.chatLock.lock(setOf(100L))
            runCurrent()

            assertNull(harness.posted(7))
            assertNotNull(harness.lockedNotification())
            harness.assertNothingNamed()
            val flags = ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
            assertTrue(
                ShortcutManagerCompat.getShortcuts(harness.context, flags).none {
                    it.id == NotificationIntents.shortcutId(100)
                },
            )
        }

    @Test
    fun `the notification goes once the locked chats' messages are read, and an update does not alert`() =
        runTest {
            val harness = started()
            harness.chatLock.lock(setOf(100L))
            harness.newMessage(id = 1)
            harness.newMessage(id = 2)
            runCurrent()

            harness.client.emit(updateNotificationGroup(removed = listOf(1)))
            runCurrent()
            assertEquals(Notification.GROUP_ALERT_SUMMARY, harness.lockedNotification()!!.groupAlertBehavior)

            harness.client.emit(updateNotificationGroup(removed = listOf(2)))
            runCurrent()
            assertNull(harness.lockedNotification())
        }

    @Test
    fun `its tap opens the locked chats, naming no chat`() =
        runTest {
            val harness = started()
            harness.chatLock.lock(setOf(100L))
            harness.newMessage()
            runCurrent()

            val intent = shadowOf(harness.lockedNotification()!!.contentIntent).savedIntent
            assertTrue(NotificationIntents.opensLockedChats(intent))
            assertNull(NotificationIntents.chatIdOf(intent))
        }
}

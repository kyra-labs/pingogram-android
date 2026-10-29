package dev.kyralabs.pingogram.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.myIdOption
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The notifications of several accounts (`docs/adr/0031-multiple-accounts.md`): each account's under its own tags,
 * channels and bundle, named by the account's first name while several are signed in, gone with the account.
 */
@RunWith(RobolectricTestRunner::class)
class AccountNotificationsTest {
    /** Ada's account (user 1) in slot 0 and Grace's (user 2) in slot 1, each with a private chat 100. */
    private suspend fun TestScope.twoAccounts(): Pair<NotificationsHarness, NotificationsHarness> {
        val first = NotificationsHarness(this)
        val second = NotificationsHarness(this, slot = 1)
        first.accounts.value = listOf(first.account, second.account)
        first.renderer.start()
        runCurrent()
        first.signIn(id = 1, firstName = "Ada", lastName = "Lovelace")
        second.signIn(id = 2, firstName = "Grace", lastName = "Hopper")
        runCurrent()
        return first to second
    }

    private suspend fun NotificationsHarness.signIn(
        id: Long,
        firstName: String,
        lastName: String,
    ) {
        client.emitAuthorizationState(TdApi.AuthorizationStateReady())
        client.emit(updateUser(user(id = id, firstName = firstName, lastName = lastName)))
        client.emit(myIdOption(id))
        client.emit(updateUser(user(id = 42, firstName = "Alan", lastName = "Turing")))
        client.emit(updateNewChat(chat(id = 100, title = "Alan Turing")))
    }

    private suspend fun NotificationsHarness.newMessage() =
        client.emit(updateNotificationGroup(added = listOf(messageNotification(message = message(id = 10)))))

    private val NotificationsHarness.manager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `each account's notifications have their own tags, channels and bundle, named by the account`() =
        runTest {
            val (first, second) = twoAccounts()

            first.newMessage()
            second.newMessage()
            runCurrent()

            val ada = checkNotNull(first.posted(7, slot = 0))
            val grace = checkNotNull(first.posted(7, slot = 1))
            assertEquals(PingogramChannel.PrivateChats.id, ada.channelId)
            assertEquals("private_chats_1", grace.channelId)
            assertEquals("Ada", NotificationCompat.getSubText(ada))
            assertEquals("Grace", NotificationCompat.getSubText(grace))
            assertTrue(ada.group != grace.group)
            assertEquals(NotificationIntents.shortcutId(100, slot = 1), grace.shortcutId)
            assertEquals("Grace", NotificationCompat.getSubText(checkNotNull(first.summary(slot = 1))))
        }

    @Test
    fun `while several accounts are signed in, each account's channels are in a group named after it`() =
        runTest {
            val (first, _) = twoAccounts()

            val groups = first.manager.notificationChannelGroups.associate { it.id to it.name.toString() }
            assertEquals(mapOf("account_0" to "Ada Lovelace", "account_1" to "Grace Hopper"), groups)
            assertEquals("account_1", first.manager.getNotificationChannel("private_chats_1").group)
            assertEquals("account_0", first.manager.getNotificationChannel(PingogramChannel.PrivateChats.id).group)
        }

    @Test
    fun `with one account signed in, its notifications do not name it`() =
        runTest {
            val first = NotificationsHarness(this)
            first.renderer.start()
            runCurrent()
            first.signIn(id = 1, firstName = "Ada", lastName = "Lovelace")
            runCurrent()

            first.newMessage()
            runCurrent()

            assertNull(NotificationCompat.getSubText(checkNotNull(first.posted(7))))
            assertTrue(first.manager.notificationChannelGroups.isEmpty())
        }

    @Test
    fun `a second account signing in names the first account's notifications, quietly`() =
        runTest {
            val first = NotificationsHarness(this)
            val second = NotificationsHarness(this, slot = 1)
            first.accounts.value = listOf(first.account, second.account)
            first.renderer.start()
            runCurrent()
            first.signIn(id = 1, firstName = "Ada", lastName = "Lovelace")
            runCurrent()
            first.newMessage()
            runCurrent()
            assertNull(NotificationCompat.getSubText(checkNotNull(first.posted(7))))

            second.signIn(id = 2, firstName = "Grace", lastName = "Hopper")
            runCurrent()

            val renamed = checkNotNull(first.posted(7))
            assertEquals("Ada", NotificationCompat.getSubText(renamed))
            assertEquals(android.app.Notification.GROUP_ALERT_SUMMARY, renamed.groupAlertBehavior)
        }

    @Test
    fun `an account that leaves the device takes its notifications and channels with it`() =
        runTest {
            val (first, second) = twoAccounts()
            first.newMessage()
            second.newMessage()
            runCurrent()

            first.accounts.value = listOf(first.account)
            runCurrent()

            assertNull(first.posted(7, slot = 1))
            assertNull(first.summary(slot = 1))
            assertNotNull(first.posted(7, slot = 0))
            assertNull(first.manager.getNotificationChannel("private_chats_1"))
        }

    @Test
    fun `one account logging out cancels only its own notifications`() =
        runTest {
            val (first, second) = twoAccounts()
            first.newMessage()
            second.newMessage()
            runCurrent()

            second.client.emitAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber())
            runCurrent()

            assertNull(first.posted(7, slot = 1))
            assertNotNull(first.posted(7, slot = 0))
        }

    @Test
    fun `with notifications from the shown account only, the others' go, and come back when turned on again`() =
        runTest {
            val (first, second) = twoAccounts()
            first.newMessage()
            second.newMessage()
            runCurrent()

            first.shown.value = setOf(0)
            runCurrent()
            assertNull(first.posted(7, slot = 1))
            assertNotNull(first.posted(7, slot = 0))

            first.shown.value = null
            runCurrent()
            assertEquals(android.app.Notification.GROUP_ALERT_SUMMARY, first.posted(7, slot = 1)?.groupAlertBehavior)
        }
}

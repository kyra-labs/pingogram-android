package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.notificationGroup
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateActiveNotifications
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messagePhoto
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.supergroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateSupergroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** From TDLib's notification updates to the notifications Android shows ([NotificationRenderer]). */
@RunWith(RobolectricTestRunner::class)
class NotificationRendererTest {
    /** A logged-in account with Ada (user 42) and her private chat 100, and a channel, chat 300. */
    private suspend fun TestScope.started(): NotificationsHarness {
        val harness = NotificationsHarness(this)
        harness.client.authState.value = AuthState.Ready
        harness.renderer.start()
        runCurrent()
        harness.client.emit(updateUser(user(id = 42, firstName = "Ada", lastName = "Lovelace")))
        harness.client.emit(updateNewChat(chat(id = 100, title = "Ada Lovelace")))
        harness.client.emit(updateSupergroup(supergroup(id = 6, isChannel = true)))
        harness.client.emit(
            updateNewChat(
                chat(id = 300, type = TdApi.ChatTypeSupergroup(6, true), title = "News", positions = emptyArray()),
            ),
        )
        runCurrent()
        return harness
    }

    private fun Notification.messages() =
        checkNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(this)).messages

    @Test
    fun `a new message shows as a conversation that alerts, with reply and mark as read`() =
        runTest {
            val harness = started()

            harness.client.emit(
                updateNotificationGroup(added = listOf(messageNotification(message = message(id = 10)))),
            )
            runCurrent()

            val notification = checkNotNull(harness.posted(7))
            assertEquals(PingogramChannel.PrivateChats.id, notification.channelId)
            assertEquals(Notification.GROUP_ALERT_ALL, notification.groupAlertBehavior)
            val message = notification.messages().single()
            assertEquals("Hello", message.text)
            assertEquals("Ada Lovelace", message.person?.name)
            assertEquals(
                listOf(
                    NotificationCompat.Action.SEMANTIC_ACTION_REPLY,
                    NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ,
                ),
                (0 until NotificationCompat.getActionCount(notification)).map {
                    NotificationCompat.getAction(notification, it)!!.semanticAction
                },
            )
            assertEquals(ConversationShortcuts.shortcutId(100), notification.shortcutId)
            assertNotNull(notification.contentIntent)
            assertNotNull(harness.summary())
        }

    @Test
    fun `media shows as its kind, as in the chat list, and a hidden preview as new message`() =
        runTest {
            val harness = started()

            harness.client.emit(
                updateNotificationGroup(
                    added =
                        listOf(
                            messageNotification(id = 1, message = message(id = 10, content = messagePhoto())),
                            messageNotification(
                                id = 2,
                                message = message(id = 11, content = messageText("Secret")),
                                showPreview = false,
                            ),
                        ),
                ),
            )
            runCurrent()

            assertEquals(listOf("Photo", "New message"), harness.posted(7)!!.messages().map { it.text.toString() })
        }

    @Test
    fun `an update without new messages does not alert again, nor does a silent message`() =
        runTest {
            val harness = started()
            harness.client.emit(
                updateNotificationGroup(added = listOf(messageNotification(id = 1), messageNotification(id = 2))),
            )
            runCurrent()

            harness.client.emit(updateNotificationGroup(removed = listOf(1)))
            runCurrent()
            assertEquals(Notification.GROUP_ALERT_SUMMARY, harness.posted(7)!!.groupAlertBehavior)

            harness.client.emit(updateNotificationGroup(added = listOf(messageNotification(id = 3, isSilent = true))))
            runCurrent()
            assertEquals(Notification.GROUP_ALERT_SUMMARY, harness.posted(7)!!.groupAlertBehavior)
            assertEquals(Notification.GROUP_ALERT_CHILDREN, harness.summary()!!.groupAlertBehavior)
        }

    @Test
    fun `a group TDLib removes is cancelled, and the summary with the last one`() =
        runTest {
            val harness = started()
            harness.client.emit(updateNotificationGroup(added = listOf(messageNotification(id = 1))))
            runCurrent()

            harness.client.emit(updateNotificationGroup(removed = listOf(1)))
            runCurrent()

            assertNull(harness.posted(7))
            assertNull(harness.summary())
        }

    @Test
    fun `channel posts go to the channels channel, without reply where the user cannot post`() =
        runTest {
            val harness = started()

            harness.client.emit(
                updateNotificationGroup(
                    groupId = 8,
                    chatId = 300,
                    added =
                        listOf(
                            messageNotification(
                                message =
                                    message(
                                        chatId = 300,
                                        sender = TdApi.MessageSenderChat(300),
                                        isChannelPost = true,
                                    ),
                            ),
                        ),
                ),
            )
            runCurrent()

            val notification = checkNotNull(harness.posted(8))
            assertEquals(PingogramChannel.Channels.id, notification.channelId)
            assertEquals(
                "News",
                notification
                    .messages()
                    .single()
                    .person
                    ?.name,
            )
            assertEquals(
                listOf(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ),
                (0 until NotificationCompat.getActionCount(notification)).map {
                    NotificationCompat.getAction(notification, it)!!.semanticAction
                },
            )
        }

    @Test
    fun `restored notifications show silently, and stale ones of an earlier run go`() =
        runTest {
            val harness = started()
            harness.postStale(groupId = 99)

            harness.client.emit(updateActiveNotifications(notificationGroup(groupId = 7, chatId = 100)))
            runCurrent()

            assertEquals(Notification.GROUP_ALERT_SUMMARY, harness.posted(7)!!.groupAlertBehavior)
            assertNull(harness.posted(99))
        }

    @Test
    fun `the open chat's notifications stay away until it closes, then show silently`() =
        runTest {
            val harness = started()
            harness.openChats.open(100)
            runCurrent()

            harness.client.emit(updateNotificationGroup(added = listOf(messageNotification(id = 1))))
            runCurrent()
            assertNull(harness.posted(7))

            harness.openChats.close(100)
            runCurrent()
            assertEquals(Notification.GROUP_ALERT_SUMMARY, harness.posted(7)!!.groupAlertBehavior)
        }

    @Test
    fun `logging out cancels everything`() =
        runTest {
            val harness = started()
            harness.client.emit(updateNotificationGroup(added = listOf(messageNotification(id = 1))))
            runCurrent()

            harness.client.emitAuthorizationState(TdApiFixtures.loggingOut())
            runCurrent()

            assertEquals(0, harness.count())
        }

    @Test
    fun `the channels exist once rendering starts, never named after Telegram`() =
        runTest {
            val harness = started()

            val channels =
                androidx.core.app.NotificationManagerCompat
                    .from(harness.context)
                    .notificationChannelsCompat
            assertEquals(PingogramChannel.entries.map { it.id }.toSet(), channels.map { it.id }.toSet())
            assertFalse(channels.any { it.name.toString().contains("Telegram", ignoreCase = true) })
            assertTrue(channels.first { it.id == PingogramChannel.PrivateChats.id }.importance >= 4)
        }
}

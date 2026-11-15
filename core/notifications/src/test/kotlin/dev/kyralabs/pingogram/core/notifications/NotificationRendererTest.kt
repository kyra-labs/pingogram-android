package dev.kyralabs.pingogram.core.notifications

import android.app.Notification
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.notificationGroup
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateActiveNotifications
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.basicGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.formattedText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messagePhoto
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.supergroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateBasicGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateSupergroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** From TDLib's notification updates to the notifications Android shows ([NotificationRenderer]). */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRendererTest {
    /** A logged-in account with Ada (user 42) and her private chat 100, and a channel, chat 300. */
    private suspend fun TestScope.started(
        harness: NotificationsHarness = NotificationsHarness(this),
    ): NotificationsHarness {
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
            assertEquals("Hello", message.text.toString())
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
            assertEquals(NotificationIntents.shortcutId(100), notification.shortcutId)
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
    fun `a group shows each message on its own line with its sender, under the group's title`() =
        runTest {
            val harness = started()
            harness.client.emit(updateUser(user(id = 43, firstName = "Grace", lastName = "Hopper")))
            harness.client.emit(updateBasicGroup(basicGroup(id = 5)))
            harness.client.emit(updateNewChat(chat(id = 200, type = TdApi.ChatTypeBasicGroup(5), title = "Team")))
            runCurrent()

            harness.client.emit(
                updateNotificationGroup(
                    groupId = 9,
                    chatId = 200,
                    added =
                        listOf(
                            messageNotification(
                                id = 1,
                                message = message(id = 10, chatId = 200, content = messageText("Hi")),
                            ),
                            messageNotification(
                                id = 2,
                                message =
                                    message(
                                        id = 11,
                                        chatId = 200,
                                        sender = TdApi.MessageSenderUser(43),
                                        content = messageText("Line one\nLine two"),
                                    ),
                            ),
                            messageNotification(
                                id = 3,
                                message = message(id = 12, chatId = 200, content = messageText("Bye")),
                            ),
                        ),
                ),
            )
            runCurrent()

            val notification = checkNotNull(harness.posted(9))
            val style =
                checkNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
            assertTrue(style.isGroupConversation)
            assertEquals("Team", style.conversationTitle.toString())
            assertEquals(
                listOf("Ada" to "Hi", "Grace" to "Line one\nLine two", "Ada" to "Bye"),
                style.messages.map {
                    it.person
                        ?.name
                        .toString()
                        .substringBefore(' ') to it.text.toString()
                },
            )
        }

    @Test
    fun `a private chat's messages each keep their lines, not one paragraph`() =
        runTest {
            val harness = started()
            val release = "Pingogram 2.2.0\nArchitecture: x86_64\n\nBuild: GMS\nSize: 58 MB"

            harness.client.emit(
                updateNotificationGroup(
                    added =
                        listOf(
                            messageNotification(id = 1, message = message(id = 10, content = messageText("First"))),
                            messageNotification(
                                id = 2,
                                message = message(id = 11, content = messagePhoto(caption = formattedText(release))),
                            ),
                        ),
                ),
            )
            runCurrent()

            val texts = harness.posted(7)!!.messages().map { it.text.toString() }
            assertEquals(listOf("First", "Photo · \u2068$release\u2069"), texts)
        }

    @Test
    fun `formatting shows in the notification and spoilers stay hidden`() =
        runTest {
            val harness = started()
            val text =
                TdApi.FormattedText(
                    "Bold and secret",
                    arrayOf(
                        TdApi.TextEntity(0, 4, TdApi.TextEntityTypeBold()),
                        TdApi.TextEntity(9, 6, TdApi.TextEntityTypeSpoiler()),
                    ),
                )

            harness.client.emit(
                updateNotificationGroup(
                    added =
                        listOf(
                            messageNotification(
                                message = message(id = 10, content = TdApi.MessageText(text, null, null)),
                            ),
                        ),
                ),
            )
            runCurrent()

            val shown =
                harness
                    .posted(7)!!
                    .messages()
                    .single()
                    .text
            assertEquals("Bold and Spoiler", shown.toString())
            assertFalse("secret" in shown.toString())
            val bold = (shown as Spanned).getSpans(0, shown.length, StyleSpan::class.java).single()
            assertEquals(Typeface.BOLD, bold.style)
            assertEquals(0 to 4, shown.getSpanStart(bold) to shown.getSpanEnd(bold))
        }

    @Test
    fun `a very long text is cut with an ellipsis`() =
        runTest {
            val harness = started()

            harness.client.emit(
                updateNotificationGroup(
                    added =
                        listOf(
                            messageNotification(message = message(id = 10, content = messageText("a".repeat(5_000)))),
                        ),
                ),
            )
            runCurrent()

            val shown =
                harness
                    .posted(7)!!
                    .messages()
                    .single()
                    .text
                    .toString()
            assertEquals(NOTIFICATION_TEXT_MAX_LENGTH + 1, shown.length)
            assertTrue(shown.endsWith("…"))
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
    fun `a restored group Android still shows as it is is not posted again`() =
        runTest {
            val earlier = started()
            earlier.client.emit(updateActiveNotifications(notificationGroup(groupId = 7, chatId = 100)))
            runCurrent()
            val shown = earlier.posted(7)!!

            // The next process: a new renderer, with Android still showing the earlier process's notification.
            val next = started(NotificationsHarness(this))
            next.client.emit(updateActiveNotifications(notificationGroup(groupId = 7, chatId = 100)))
            runCurrent()

            assertSame(shown, next.posted(7))
        }

    @Test
    fun `a restored group that changed while no process ran is posted again`() =
        runTest {
            val earlier = started()
            earlier.client.emit(updateActiveNotifications(notificationGroup(groupId = 7, chatId = 100)))
            runCurrent()
            val shown = earlier.posted(7)!!

            val next = started(NotificationsHarness(this))
            val changed = notificationGroup(groupId = 7, chatId = 100).apply { totalCount = 3 }
            next.client.emit(updateActiveNotifications(changed))
            runCurrent()

            assertNotSame(shown, next.posted(7))
            assertEquals(Notification.GROUP_ALERT_SUMMARY, next.posted(7)!!.groupAlertBehavior)
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

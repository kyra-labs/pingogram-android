package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.messageIds
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.messageNotification
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.notificationGroup
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateActiveNotifications
import dev.kyralabs.pingogram.core.testing.NotificationFixtures.updateNotificationGroup
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibNotificationRepositoryTest {
    private val client = FakeTdlibClient()
    private val openChats = OpenChats()

    private fun TestScope.repository() = TdlibNotificationRepository(client, backgroundScope, openChats)

    private suspend fun TdlibNotificationRepository.current(): ActiveNotifications = notifications.first()

    private fun ok() = TdResult.Success(TdApi.Ok())

    @Test
    fun `turns TDLib's notifications on whenever the account is logged in`() =
        runTest {
            client.on(TdApi.SetOption::class, ok())
            repository()
            runCurrent()
            assertEquals(emptyList<TdApi.SetOption>(), client.sentOf<TdApi.SetOption>())

            client.authState.value = AuthState.Ready
            runCurrent()

            val options =
                client.sentOf<TdApi.SetOption>().associate { it.name to (it.value as TdApi.OptionValueInteger).value }
            assertEquals(mapOf("notification_group_count_max" to 25L, "notification_group_size_max" to 10L), options)
        }

    @Test
    fun `a new group alerts with sound, a silent message does not`() =
        runTest {
            val repository = repository()

            client.emit(
                updateNotificationGroup(
                    added =
                        listOf(
                            messageNotification(id = 1, message = message(id = 10)),
                            messageNotification(id = 2, message = message(id = 11), isSilent = true),
                        ),
                ),
            )

            val group = repository.current().groups.getValue(7)
            assertEquals(NotificationGroupType.Messages, group.type)
            assertEquals(100L, group.chatId)
            assertEquals(listOf(true, false), group.notifications.map { it.alerts })
            assertEquals(listOf(10L, 11L), group.messageIds)
            assertTrue(repository.current().isRestored)
        }

    @Test
    fun `nothing alerts in a chat whose sound is off`() =
        runTest {
            val repository = repository()

            client.emit(updateNotificationGroup(added = listOf(messageNotification()), soundId = 0))

            assertFalse(
                repository
                    .current()
                    .groups
                    .getValue(7)
                    .notifications
                    .single()
                    .alerts,
            )
        }

    @Test
    fun `removals and additions apply, and a group left empty is gone`() =
        runTest {
            val repository = repository()
            val first = listOf(messageNotification(id = 1), messageNotification(id = 2))
            client.emit(updateNotificationGroup(added = first))

            val third = listOf(messageNotification(id = 3))
            client.emit(updateNotificationGroup(added = third, removed = listOf(1), totalCount = 5))
            val group = repository.current().groups.getValue(7)
            assertEquals(listOf(2, 3), group.notifications.map { it.id })
            assertEquals(5, group.totalCount)

            client.emit(updateNotificationGroup(removed = listOf(2, 3), totalCount = 0))
            assertEquals(emptySet<Int>(), repository.current().groups.keys)
        }

    @Test
    fun `restored groups after a start never alert`() =
        runTest {
            val repository = repository()

            client.emit(updateActiveNotifications(notificationGroup(groupId = 3, chatId = 200)))

            val active = repository.current()
            assertTrue(active.isRestored)
            assertFalse(
                active.groups
                    .getValue(3)
                    .notifications
                    .single()
                    .alerts,
            )
        }

    @Test
    fun `an edited message replaces its notification's content, which does not alert again`() =
        runTest {
            val repository = repository()
            client.emit(updateNotificationGroup(added = listOf(messageNotification(id = 1))))

            val edited = messageNotification(id = 1, message = message(content = messageText("Edited")))
            client.emit(TdApi.UpdateNotification(7, edited))

            val notification =
                repository
                    .current()
                    .groups
                    .getValue(7)
                    .notifications
                    .single()
            val content = notification.content as NotificationContent.NewMessage
            assertEquals("Edited", (content.message.content as MessageContent.Text).text.text)
            assertFalse(notification.alerts)
        }

    @Test
    fun `logging out drops every notification at once`() =
        runTest {
            val repository = repository()
            client.emit(updateNotificationGroup(added = listOf(messageNotification())))

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.loggingOut()))

            assertEquals(ActiveNotifications(), repository.current())
        }

    @Test
    fun `open chats come along, so their notifications stay quiet`() =
        runTest {
            val repository = repository()

            repository.notifications.test {
                assertEquals(emptySet<Long>(), awaitItem().openChatIds)
                openChats.open(100)
                assertEquals(setOf(100L), awaitItem().openChatIds)
                openChats.open(100)
                openChats.close(100)
                openChats.close(100)
                assertEquals(emptySet<Long>(), awaitItem().openChatIds)
            }
        }

    @Test
    fun `pending notifications follow TDLib`() =
        runTest {
            val repository = repository()

            client.emit(TdApi.UpdateHavePendingNotifications(false, true))

            assertTrue(repository.hasPendingNotifications.first())
        }

    @Test
    fun `reply sends the text, keeps the draft and quotes the message`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Success(message()))

            val result = repository().reply(chatId = 100, text = "On my way", replyToMessageId = 10)

            val sent = client.sentOf<TdApi.SendMessage>().single()
            val content = sent.inputMessageContent as TdApi.InputMessageText
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(100L, sent.chatId)
            assertEquals("On my way", content.text.text)
            assertFalse(content.clearDraft)
            assertEquals(10L, (sent.replyTo as TdApi.InputMessageReplyToMessage).messageId)
        }

    @Test
    fun `markAsRead views the messages from a notification with force_read`() =
        runTest {
            client.on(TdApi.ViewMessages::class, ok())

            repository().markAsRead(chatId = 100, messageIds = listOf(10, 11))

            val sent = client.sentOf<TdApi.ViewMessages>().single()
            assertEquals(listOf(10L, 11L), sent.messageIds.toList())
            assertTrue(sent.source is TdApi.MessageSourceNotification)
            assertTrue(sent.forceRead)
        }

    @Test
    fun `dismiss removes the group in TDLib, and a refusal fails`() =
        runTest {
            val notFound = TdResult.Failure(TdError(400, "NOTIFICATION_GROUP_NOT_FOUND"))
            client.on(TdApi.RemoveNotificationGroup::class, notFound)

            val result = repository().dismiss(groupId = 7, maxNotificationId = 3)

            val sent = client.sentOf<TdApi.RemoveNotificationGroup>().single()
            assertEquals(7 to 3, sent.notificationGroupId to sent.maxNotificationId)
            assertTrue((result as Outcome.Failure).error !is DomainError.Network)
        }
}

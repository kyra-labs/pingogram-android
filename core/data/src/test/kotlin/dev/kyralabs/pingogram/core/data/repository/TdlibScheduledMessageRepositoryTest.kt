package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.model.ScheduledMessage
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.error
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.failedState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.floodWait
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.pendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateDeleteMessages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageEdited
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendFailed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendSucceeded
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The scheduled messages of a chat: loaded once per collection, then kept current by the chat's updates. */
class TdlibScheduledMessageRepositoryTest {
    private val client = FakeTdlibClient()

    private val repository = TdlibScheduledMessageRepository(client)

    private fun answer(vararg scheduled: TdApi.Message) =
        client.on(TdApi.GetChatScheduledMessages::class, TdResult.Success(messages(*scheduled)))

    private fun scheduled(
        id: Long,
        at: Int = AT,
        repeatPeriod: Int = 0,
        state: TdApi.MessageSchedulingState = TdApi.MessageSchedulingStateSendAtDate(at, repeatPeriod),
    ) = message(id = id, chatId = CHAT, isOutgoing = true, date = 0).apply { schedulingState = state }

    private fun Outcome<ImmutableList<ScheduledMessage>>.ids() = (this as Outcome.Success).value.map { it.message.id }

    private fun Outcome<ImmutableList<ScheduledMessage>>.messages() = (this as Outcome.Success).value

    @Test
    fun `loads the chat's scheduled messages, the earliest first, those sent when online last`() =
        runTest {
            answer(
                scheduled(3, at = AT + 60),
                scheduled(5, state = TdApi.MessageSchedulingStateSendWhenOnline()),
                scheduled(9, at = AT, repeatPeriod = DAY),
                scheduled(7, state = TdApi.MessageSchedulingStateSendWhenVideoProcessed(AT + 30)),
            )

            repository.scheduledMessages(CHAT).test {
                val loaded = awaitItem().messages()
                assertEquals(listOf(9L, 7L, 3L, 5L), loaded.map { it.message.id })
                assertEquals(MessageSchedule.At(AT.toLong(), DAY), loaded[0].schedule)
                assertEquals(MessageSchedule.WhenVideoProcessed(AT + 30L), loaded[1].schedule)
                assertEquals(MessageSchedule.WhenOnline, loaded[3].schedule)
            }
            assertEquals(CHAT, client.sentOf<TdApi.GetChatScheduledMessages>().single().chatId)
        }

    @Test
    fun `a message scheduled later joins the list, and one sent into the history does not`() =
        runTest {
            answer(scheduled(3))

            repository.scheduledMessages(CHAT).test {
                assertEquals(listOf(3L), awaitItem().ids())

                client.emit(updateNewMessage(message(id = 4, chatId = CHAT)))
                client.emit(updateNewMessage(scheduled(2, at = AT - 60)))
                assertEquals(listOf(2L, 3L), awaitItem().ids())
            }
        }

    @Test
    fun `a message scheduled from this device takes its final id once Telegram has it, or shows it failed`() =
        runTest {
            answer()

            repository.scheduledMessages(CHAT).test {
                awaitItem()
                client.emit(updateNewMessage(scheduled(1_001).apply { sendingState = pendingState() }))
                assertEquals(listOf(1_001L), awaitItem().ids())

                client.emit(updateMessageSendSucceeded(scheduled(3), oldMessageId = 1_001))
                assertEquals(listOf(3L), awaitItem().ids())

                client.emit(updateNewMessage(scheduled(1_002).apply { sendingState = pendingState() }))
                awaitItem()
                client.emit(updateMessageSendFailed(scheduled(1_003).apply { sendingState = failedState() }, 1_002))
                val failed = awaitItem().messages()
                assertEquals(listOf(3L, 1_003L), failed.map { it.message.id })
                assertTrue(failed[1].message.sendingState is SendingState.Failed)
            }
        }

    @Test
    fun `a message Telegram sent at its time leaves the list`() =
        runTest {
            answer(scheduled(3), scheduled(5, at = AT + 60))

            repository.scheduledMessages(CHAT).test {
                awaitItem()

                // TDLib deletes the scheduled message and announces the sent one, without a scheduling state.
                client.emit(updateDeleteMessages(CHAT, 3))
                client.emit(updateNewMessage(message(id = 8, chatId = CHAT, isOutgoing = true)))
                assertEquals(listOf(5L), awaitItem().ids())
                expectNoEvents()
            }
        }

    @Test
    fun `edits keep the messages current, and messages only unloaded from TDLib's memory stay`() =
        runTest {
            answer(scheduled(3))

            repository.scheduledMessages(CHAT).test {
                awaitItem()

                client.emit(updateMessageContent(CHAT, 3, messageText("Edited")))
                val edited = awaitItem().messages().single().message
                assertEquals("Edited", (edited.content as MessageContent.Text).text.text)

                client.emit(updateMessageEdited(CHAT, 3, editDate = 1_700_000_100))
                assertEquals(
                    1_700_000_100L,
                    awaitItem()
                        .messages()
                        .single()
                        .message.editDate,
                )

                client.emit(updateDeleteMessages(CHAT, 3, isPermanent = false, fromCache = true))
                client.emit(updateDeleteMessages(OTHER_CHAT, 3))
                expectNoEvents()
            }
        }

    @Test
    fun `applies the updates that arrive while it loads`() =
        runTest {
            val held = CompletableDeferred<TdResult<TdApi.Messages>>()
            client.on(TdApi.GetChatScheduledMessages::class) { held.await() }

            repository.scheduledMessages(CHAT).test {
                client.emit(updateDeleteMessages(CHAT, 3))
                held.complete(TdResult.Success(messages(scheduled(3), scheduled(5, at = AT + 60))))

                assertEquals(listOf(3L, 5L), awaitItem().ids())
                assertEquals(listOf(5L), awaitItem().ids())
            }
        }

    @Test
    fun `a failed load emits its failure and ends`() =
        runTest {
            client.on(TdApi.GetChatScheduledMessages::class, TdResult.Failure(floodWait(7).toTdError()))

            repository.scheduledMessages(CHAT).test {
                assertEquals(Outcome.Failure(DomainError.FloodWait(7)), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `sending now clears the scheduling state, then loads the list again`() =
        runTest {
            answer(scheduled(3))
            client.on(TdApi.EditMessageSchedulingState::class, TdResult.Success(TdApi.Ok()))

            repository.scheduledMessages(CHAT).test {
                awaitItem()
                answer()

                assertEquals(Outcome.Success(Unit), repository.sendNow(CHAT, 3))
                assertEquals(emptyList<Long>(), awaitItem().ids())
            }
            val edit = client.sentOf<TdApi.EditMessageSchedulingState>().single()
            assertEquals(CHAT, edit.chatId)
            assertEquals(3L, edit.messageId)
            assertNull(edit.schedulingState)
        }

    @Test
    fun `rescheduling sets the new time with the repeat period, then loads the new time`() =
        runTest {
            answer(scheduled(3))
            client.on(TdApi.EditMessageSchedulingState::class, TdResult.Success(TdApi.Ok()))

            repository.scheduledMessages(CHAT).test {
                awaitItem()
                answer(scheduled(3, at = AT + 3_600, repeatPeriod = DAY))

                assertEquals(Outcome.Success(Unit), repository.reschedule(CHAT, 3, AT + 3_600L, repeatPeriod = DAY))
                assertEquals(MessageSchedule.At(AT + 3_600L, DAY), awaitItem().messages().single().schedule)
            }
            val state = client.sentOf<TdApi.EditMessageSchedulingState>().single().schedulingState
            state as TdApi.MessageSchedulingStateSendAtDate
            assertEquals(AT + 3_600, state.sendDate)
            assertEquals(DAY, state.repeatPeriod)
        }

    @Test
    fun `deleting deletes the messages for everyone`() =
        runTest {
            client.on(TdApi.DeleteMessages::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.delete(CHAT, listOf(3, 5)))

            val delete = client.sentOf<TdApi.DeleteMessages>().single()
            assertEquals(listOf(3L, 5L), delete.messageIds.toList())
            assertTrue(delete.revoke)
        }

    @Test
    fun `refusals come back as domain errors, and change nothing`() =
        runTest {
            answer(scheduled(3))
            client.on(
                TdApi.EditMessageSchedulingState::class,
                TdResult.Failure(error(400, "MESSAGE_ID_INVALID").toTdError()),
            )
            client.on(TdApi.DeleteMessages::class, TdResult.Failure(floodWait(30).toTdError()))

            repository.scheduledMessages(CHAT).test {
                awaitItem()

                assertTrue(repository.sendNow(CHAT, 3) is Outcome.Failure)
                assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.delete(CHAT, listOf(3)))
                expectNoEvents()
            }
        }

    private fun TdApi.Error.toTdError() = TdError(code, message)

    private companion object {
        const val CHAT = 100L
        const val OTHER_CHAT = 200L
        const val AT = 1_800_000_000
        const val DAY = 86_400
    }
}

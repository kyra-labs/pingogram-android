package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Send-as: who the user can send messages as, choosing one, and the chat's current choice ([TdlibChatRepository]). */
class TdlibSendAsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSendAsRepository(client)

    @Test
    fun `lists the available senders with the ones that need Premium`() =
        runTest {
            client.on(
                TdApi.GetChatAvailableMessageSenders::class,
                TdResult.Success(
                    TdApi.ChatMessageSenders(
                        arrayOf(
                            TdApi.ChatMessageSender(TdApi.MessageSenderUser(ME), false),
                            TdApi.ChatMessageSender(TdApi.MessageSenderChat(CHANNEL), false),
                            TdApi.ChatMessageSender(TdApi.MessageSenderChat(OTHER_CHANNEL), true),
                        ),
                    ),
                ),
            )

            assertEquals(
                Outcome.Success(
                    persistentListOf(
                        AvailableSender(MessageSender.User(ME), needsPremium = false),
                        AvailableSender(MessageSender.Chat(CHANNEL), needsPremium = false),
                        AvailableSender(MessageSender.Chat(OTHER_CHANNEL), needsPremium = true),
                    ),
                ),
                repository.availableSenders(GROUP),
            )
            assertEquals(GROUP, client.sentOf<TdApi.GetChatAvailableMessageSenders>().single().chatId)
        }

    @Test
    fun `a failure to list the senders is a domain error`() =
        runTest {
            client.on(TdApi.GetChatAvailableMessageSenders::class, TdResult.Failure(TdError(400, "CHAT_INVALID")))

            assertEquals(Outcome.Failure(DomainError.Unknown(400, "CHAT_INVALID")), repository.availableSenders(GROUP))
        }

    @Test
    fun `chooses a sender`() =
        runTest {
            client.on(TdApi.SetChatMessageSender::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.setSender(GROUP, MessageSender.Chat(CHANNEL)))
            val sent = client.sentOf<TdApi.SetChatMessageSender>().single()
            assertEquals(GROUP, sent.chatId)
            assertEquals(CHANNEL, (sent.messageSenderId as TdApi.MessageSenderChat).chatId)
        }

    @Test
    fun `the chat comes with its sender and follows updateChatMessageSender`() =
        runTest {
            val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
            client.emit(updateNewChat(chat(id = GROUP, messageSenderId = TdApi.MessageSenderUser(ME))))
            assertEquals(MessageSender.User(ME), chats.chat(GROUP).first()?.messageSender)

            client.emit(TdApi.UpdateChatMessageSender(GROUP, TdApi.MessageSenderChat(CHANNEL)))
            assertEquals(MessageSender.Chat(CHANNEL), chats.chat(GROUP).first()?.messageSender)

            client.emit(TdApi.UpdateChatMessageSender(GROUP, null))
            assertNull(chats.chat(GROUP).first()?.messageSender)
        }

    private companion object {
        const val ME = 1L
        const val GROUP = -100L
        const val CHANNEL = -200L
        const val OTHER_CHANNEL = -300L
    }
}

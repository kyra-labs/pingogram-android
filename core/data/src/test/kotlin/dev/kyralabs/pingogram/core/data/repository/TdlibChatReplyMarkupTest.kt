package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** How [TdlibChatRepository] keeps the message whose bot keyboard is in effect in a chat. */
class TdlibChatReplyMarkupTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())

    private suspend fun TdlibChatRepository.markupMessageId() = checkNotNull(chat(CHAT_ID).first()).replyMarkupMessageId

    /** A chat whose keyboard is that of message [MESSAGE_ID]. */
    private fun keyboardChat() = chat(id = CHAT_ID).also { it.replyMarkupMessageId = MESSAGE_ID }

    @Test
    fun `a new chat comes with the message of its keyboard`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(keyboardChat()))

            assertEquals(MESSAGE_ID, repository.markupMessageId())
        }

    @Test
    fun `updateChatReplyMarkup replaces the message, and none takes the keyboard away`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(keyboardChat()))

            client.emit(TdApi.UpdateChatReplyMarkup(CHAT_ID, message(id = NEWER_ID, chatId = CHAT_ID)))
            assertEquals(NEWER_ID, repository.markupMessageId())

            client.emit(TdApi.UpdateChatReplyMarkup(CHAT_ID, null))
            assertEquals(0L, repository.markupMessageId())
        }

    private companion object {
        const val CHAT_ID = 100L
        const val MESSAGE_ID = 7L shl 20
        const val NEWER_ID = 8L shl 20
    }
}

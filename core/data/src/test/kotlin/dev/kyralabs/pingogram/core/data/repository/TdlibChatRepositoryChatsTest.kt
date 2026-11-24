package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [TdlibChatRepository.chats]: several chats in one flow, which follows their changes and no others. */
class TdlibChatRepositoryChatsTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())

    @Test
    fun `chats gives the known chats of the ids, and emits only their changes`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, title = "Ada")))
            client.emit(updateNewChat(chat(id = OTHER_CHAT_ID)))

            repository.chats(setOf(CHAT_ID, UNKNOWN_CHAT_ID)).test {
                assertEquals(setOf(CHAT_ID), awaitItem().keys)

                client.emit(TdApi.UpdateChatTitle(OTHER_CHAT_ID, "Grace"))
                expectNoEvents()

                client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Ada Lovelace"))
                assertEquals("Ada Lovelace", awaitItem()[CHAT_ID]?.title)
            }
        }

    private companion object {
        const val CHAT_ID = 100L
        const val OTHER_CHAT_ID = 200L
        const val UNKNOWN_CHAT_ID = 300L
    }
}

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** How [TdlibChatRepository] keeps a group's online members, which TDLib sends for open chats only. */
class TdlibChatOnlineMembersTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())

    private suspend fun TdlibChatRepository.online() = checkNotNull(chat(CHAT_ID).first()).onlineMemberCount

    @Test
    fun `a chat has no online members until TDLib counts them`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = CHAT_ID)))

            assertEquals(0, repository.online())
        }

    @Test
    fun `updateChatOnlineMemberCount sets the online members`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID)))

            client.emit(TdApi.UpdateChatOnlineMemberCount(CHAT_ID, 36))

            assertEquals(36, repository.online())
        }

    private companion object {
        const val CHAT_ID = 100L
    }
}

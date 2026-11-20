package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chatPosition
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * [TdlibChatRepository.chatList] shares one sorted list among its collectors (`docs/PERFORMANCE.md` §3): the chat
 * list's tab, the Feed tab's count and the header's mention count sort the main list once per change, not once each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibChatListSharingTest {
    private val client = FakeTdlibClient()

    @Test
    fun `everyone who follows a list shares one sorted list`() =
        runTest {
            val repository =
                TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
            client.emit(updateNewChat(chat(id = CHAT_ID, title = "Ada", positions = arrayOf(chatPosition()))))
            val first = mutableListOf<List<Chat>>()
            val second = mutableListOf<List<Chat>>()
            backgroundScope.launch { repository.chatList(ChatListType.Main).collect { first += it } }
            backgroundScope.launch { repository.chatList(ChatListType.Main).collect { second += it } }
            runCurrent()

            client.emit(TdApi.UpdateChatTitle(CHAT_ID, "Grace"))
            runCurrent()

            assertEquals(listOf("Ada", "Grace"), first.map { it.single().title })
            // The same lists, not equal copies: sorted once for both.
            assertEquals(first.size, second.size)
            first.zip(second).forEach { (one, other) -> assertSame(one, other) }
        }

    private companion object {
        const val CHAT_ID = 1L
    }
}

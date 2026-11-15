package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.UnreadMessageCount
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** TDLib's count of a list's unread messages in [TdlibChatRepository] (the unread counter widget, board 33). */
class TdlibChatRepositoryUnreadTest {
    private val client = FakeTdlibClient()

    @Test
    fun `unreadMessageCount follows TDLib's count of that list's messages`() =
        runTest {
            val repository =
                TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
            assertEquals(UnreadMessageCount.None, repository.unreadMessageCount(ChatListType.Main).first())

            client.emit(TdApi.UpdateUnreadMessageCount(TdApi.ChatListMain(), 47, 40))
            client.emit(TdApi.UpdateUnreadMessageCount(TdApi.ChatListArchive(), 3, 0))

            assertEquals(UnreadMessageCount(47, 40), repository.unreadMessageCount(ChatListType.Main).first())
            assertEquals(UnreadMessageCount(3, 0), repository.unreadMessageCount(ChatListType.Archive).first())
        }
}

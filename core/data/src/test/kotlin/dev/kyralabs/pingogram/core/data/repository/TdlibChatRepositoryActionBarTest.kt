package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A chat's action bar, as TDLib announces it with the chat and changes it with `updateChatActionBar`. */
class TdlibChatRepositoryActionBarTest {
    private val client = FakeTdlibClient()

    @Test
    fun `a chat carries its action bar, and follows updateChatActionBar`() =
        runTest {
            val repository =
                TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
            client.emit(updateNewChat(chat(id = CHAT_ID).apply { actionBar = TdApi.ChatActionBarAddContact() }))
            assertEquals(ChatActionBar.AddContact, repository.chat(CHAT_ID).first()?.actionBar)

            client.emit(TdApi.UpdateChatActionBar(CHAT_ID, TdApi.ChatActionBarReportSpam(false)))
            assertEquals(ChatActionBar.ReportSpam(canUnarchive = false), repository.chat(CHAT_ID).first()?.actionBar)

            client.emit(TdApi.UpdateChatActionBar(CHAT_ID, null))
            assertNull(repository.chat(CHAT_ID).first()?.actionBar)
        }

    private companion object {
        const val CHAT_ID = 100L
    }
}

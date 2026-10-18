package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
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

/** How [TdlibChatRepository] keeps what members may do in a chat, which decides whether the composer shows. */
class TdlibChatPermissionsTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler))

    private suspend fun TdlibChatRepository.permissions() = checkNotNull(chat(CHAT_ID).first()).permissions

    private fun permissions(canSendBasicMessages: Boolean) =
        TdApi.ChatPermissions().also { it.canSendBasicMessages = canSendBasicMessages }

    @Test
    fun `a new chat comes with its permissions`() =
        runTest {
            val repository = repository()

            client.emit(updateNewChat(chat(id = CHAT_ID, permissions = permissions(canSendBasicMessages = true))))

            assertEquals(ChatPermissions.All, repository.permissions())
        }

    @Test
    fun `updateChatPermissions replaces the permissions`() =
        runTest {
            val repository = repository()
            client.emit(updateNewChat(chat(id = CHAT_ID, permissions = permissions(canSendBasicMessages = true))))

            client.emit(TdApi.UpdateChatPermissions(CHAT_ID, permissions(canSendBasicMessages = false)))

            assertEquals(ChatPermissions.None, repository.permissions())
        }

    private companion object {
        const val CHAT_ID = 100L
    }
}

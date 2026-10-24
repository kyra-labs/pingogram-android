package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretChatLinkPreviewsTest {
    private val client = FakeTdlibClient()
    private val settings = FakeLinkPreviewSettingsRepository()
    private val previews = SecretChatLinkPreviews(client, settings)

    private fun answerChat(type: TdApi.ChatType) =
        client.on(TdApi.GetChat::class, TdResult.Success(TdApiFixtures.chat(id = CHAT, type = type)))

    @Test
    fun `a secret chat gets no link preview until the user allows them`() =
        runTest {
            answerChat(TdApi.ChatTypeSecret(3, 42))

            assertTrue(previews.optionsFor(CHAT)!!.isDisabled)

            settings.setSecretChatPreviews(true)
            assertNull(previews.optionsFor(CHAT))
        }

    @Test
    fun `other chats keep TDLib's default`() =
        runTest {
            answerChat(TdApi.ChatTypePrivate(42))

            assertNull(previews.optionsFor(CHAT))
        }

    @Test
    fun `a chat TDLib cannot find keeps TDLib's default`() =
        runTest {
            assertNull(previews.optionsFor(CHAT))
        }

    private companion object {
        const val CHAT = 100L
    }
}

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibMessageRepository.loadFullRichMessage]: the whole of a rich message Telegram sent only the start of. */
class TdlibMessageRepositoryRichMessageTest {
    private val client = FakeTdlibClient()

    private fun richMessage(
        vararg paragraphs: String,
        isFull: Boolean,
    ) = TdApi.RichMessage(
        paragraphs.map { TdApi.PageBlockParagraph(TdApi.RichTextPlain(it)) }.toTypedArray(),
        false,
        isFull,
    )

    /** A repository with [CHAT]'s history loaded: message [MESSAGE], whose start alone Telegram sent. */
    private suspend fun TestScope.repository() =
        TdlibMessageRepository(
            client,
            backgroundScope,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
        ).also { repository ->
            val partial =
                message(
                    id = MESSAGE,
                    chatId = CHAT,
                    content = TdApi.MessageRichMessage(richMessage("Start", isFull = false)),
                )
            client.on(TdApi.GetChatHistory::class, TdResult.Success(messages(partial)))
            repository.loadOlder(CHAT)
        }

    private suspend fun TdlibMessageRepository.loaded(): RichMessage =
        (
            history(CHAT)
                .first()
                .messages
                .single { it.id == MESSAGE }
                .content as MessageContent.RichMessage
        ).message

    @Test
    fun `the full rich message replaces the start Telegram sent`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetFullRichMessage::class, TdResult.Success(richMessage("Start", "Rest", isFull = true)))

            val result = repository.loadFullRichMessage(CHAT, MESSAGE)

            assertEquals(Outcome.Success(Unit), result)
            val request = client.sentOf<TdApi.GetFullRichMessage>().single()
            assertEquals(CHAT to MESSAGE, request.chatId to request.messageId)
            val full = repository.loaded()
            assertTrue(full.isFull)
            assertEquals(listOf("Start", "Rest"), full.blocks.map { (it as RichBlock.Paragraph).text.text })
        }

    @Test
    fun `a failed full rich message leaves the start as it was`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetFullRichMessage::class, TdResult.Failure(TdError(400, "MESSAGE_ID_INVALID")))

            val result = repository.loadFullRichMessage(CHAT, MESSAGE)

            assertTrue(result is Outcome.Failure)
            assertFalse(repository.loaded().isFull)
        }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 7L
    }
}

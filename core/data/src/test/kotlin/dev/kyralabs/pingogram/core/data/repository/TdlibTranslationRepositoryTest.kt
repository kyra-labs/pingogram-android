package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.floodWait
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewChat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Message translation through Telegram: the request TDLib gets, what comes back, Telegram's refusals, the languages it
 * translates into, and the chat's `is_translatable` ([TdlibChatRepository]).
 */
class TdlibTranslationRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibTranslationRepository(client)

    @Test
    fun `asks Telegram to translate the message by its id, with its formatting where Telegram keeps it`() =
        runTest {
            client.on(
                TdApi.TranslateMessageText::class,
                TdResult.Success(
                    TdApi.FormattedText("Hello, world", arrayOf(TdApi.TextEntity(0, 5, TdApi.TextEntityTypeBold()))),
                ),
            )

            val translated = repository.translateMessage(CHAT, MESSAGE, "en")

            assertEquals(
                Outcome.Success(
                    FormattedText("Hello, world", persistentListOf(TextEntity(0, 5, TextEntityType.Bold))),
                ),
                translated,
            )
            val request = client.sentOf<TdApi.TranslateMessageText>().single()
            assertEquals(CHAT, request.chatId)
            assertEquals(MESSAGE, request.messageId)
            assertEquals("en", request.toLanguageCode)
            assertEquals("", request.tone)
        }

    @Test
    fun `a flood wait is the time to wait`() =
        runTest {
            client.on(TdApi.TranslateMessageText::class, TdResult.Failure(floodWait(30).toTdError()))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.translateMessage(CHAT, MESSAGE, "en"))
        }

    @Test
    fun `a refusal for want of Telegram Premium says so, and nothing is tried instead`() =
        runTest {
            client.on(TdApi.TranslateMessageText::class, TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")))

            assertEquals(Outcome.Failure(DomainError.PremiumRequired), repository.translateMessage(CHAT, MESSAGE, "en"))
            assertEquals(1, client.sentOf<TdApi.TranslateMessageText>().size)
            assertTrue(client.sentOf<TdApi.TranslateText>().isEmpty())
        }

    @Test
    fun `a session that is gone is unauthorized`() =
        runTest {
            client.on(TdApi.TranslateMessageText::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.translateMessage(CHAT, MESSAGE, "en"))
        }

    @Test
    fun `no answer in time is the network`() =
        runTest {
            val never = CompletableDeferred<TdResult<TdApi.FormattedText>>()
            client.on(TdApi.TranslateMessageText::class) { never.await() }

            assertEquals(Outcome.Failure(DomainError.Network), repository.translateMessage(CHAT, MESSAGE, "en"))
        }

    @Test
    fun `other refusals stay unknown, for the caller to say translation failed`() =
        runTest {
            client.on(TdApi.TranslateMessageText::class, TdResult.Failure(TdError(400, "TRANSLATE_REQ_QUOTA_EXCEEDED")))

            assertEquals(
                Outcome.Failure(DomainError.Unknown(400, "TRANSLATE_REQ_QUOTA_EXCEEDED")),
                repository.translateMessage(CHAT, MESSAGE, "en"),
            )
        }

    @Test
    fun `the languages Telegram translates into, by the codes it knows`() {
        assertEquals("en", repository.targetLanguageOf("en-GB"))
        assertEquals("pt", repository.targetLanguageOf("pt-BR"))
        assertEquals("he", repository.targetLanguageOf("he-IL"))
        assertEquals("he", repository.targetLanguageOf("iw"))
        assertEquals("id", repository.targetLanguageOf("in"))
        assertEquals("zh-CN", repository.targetLanguageOf("zh-Hans-CN"))
        assertEquals("zh-CN", repository.targetLanguageOf("zh"))
        assertEquals("zh-TW", repository.targetLanguageOf("zh-Hant"))
        assertEquals("zh-TW", repository.targetLanguageOf("zh-HK"))
        assertEquals("no", repository.targetLanguageOf("nb-NO"))
        assertEquals("tl", repository.targetLanguageOf("fil-PH"))
        assertNull(repository.targetLanguageOf("tok"))
        assertNull(repository.targetLanguageOf("und"))
    }

    @Test
    fun `a chat comes with is_translatable and follows updateChatIsTranslatable`() =
        runTest {
            val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
            client.emit(updateNewChat(chat(id = CHAT).apply { isTranslatable = true }))
            assertTrue(chats.chat(CHAT).first()?.isTranslatable == true)

            client.emit(TdApi.UpdateChatIsTranslatable(CHAT, false))
            assertFalse(chats.chat(CHAT).first()?.isTranslatable == true)
        }

    private fun TdApi.Error.toTdError() = TdError(code, message)

    private companion object {
        const val CHAT = -100L
        const val MESSAGE = 7L shl 20
    }
}

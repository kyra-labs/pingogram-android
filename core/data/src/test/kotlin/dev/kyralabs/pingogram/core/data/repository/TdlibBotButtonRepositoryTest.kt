package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackData
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The requests of bots' buttons: callback answers, login links and sharing users and chats with a bot. */
class TdlibBotButtonRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibBotButtonRepository(client)

    @Test
    fun `sends a callback button's data and maps the bot's answer`() =
        runTest {
            client.on(
                TdApi.GetCallbackQueryAnswer::class,
                TdResult.Success(TdApi.CallbackQueryAnswer("Done", true, "")),
            )

            val answer = repository.answer(CHAT, MESSAGE, CallbackPayload.Data(CallbackData(byteArrayOf(1, 2))))

            assertEquals(Outcome.Success(CallbackAnswer.Answered("Done", showAlert = true, url = "")), answer)
            val sent = client.sentOf<TdApi.GetCallbackQueryAnswer>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
            assertArrayEquals(byteArrayOf(1, 2), (sent.payload as TdApi.CallbackQueryPayloadData).data)
        }

    @Test
    fun `sends the password with a button that asks for it`() =
        runTest {
            client.on(TdApi.GetCallbackQueryAnswer::class, TdResult.Success(TdApi.CallbackQueryAnswer("", false, "")))

            repository.answer(CHAT, MESSAGE, CallbackPayload.DataWithPassword("secret", CallbackData(byteArrayOf(3))))

            val payload = client.sentOf<TdApi.GetCallbackQueryAnswer>().single().payload
            assertEquals("secret", (payload as TdApi.CallbackQueryPayloadDataWithPassword).password)
            assertArrayEquals(byteArrayOf(3), payload.data)
        }

    @Test
    fun `a bot that does not answer in time is no answer, not an error`() =
        runTest {
            client.on(TdApi.GetCallbackQueryAnswer::class, TdResult.Failure(TdError(502, "The bot is not responding")))

            assertEquals(
                Outcome.Success(CallbackAnswer.NoAnswer),
                repository.answer(CHAT, MESSAGE, CallbackPayload.Data(CallbackData(byteArrayOf(1)))),
            )
        }

    @Test
    fun `no answer from TDLib within the timeout is no answer too`() =
        runTest {
            client.on(TdApi.GetCallbackQueryAnswer::class) { _: TdApi.GetCallbackQueryAnswer -> awaitCancellation() }

            assertEquals(
                Outcome.Success(CallbackAnswer.NoAnswer),
                repository.answer(CHAT, MESSAGE, CallbackPayload.Data(CallbackData(byteArrayOf(1)))),
            )
        }

    @Test
    fun `a wrong password is a domain error`() =
        runTest {
            client.on(TdApi.GetCallbackQueryAnswer::class, TdResult.Failure(TdError(400, "PASSWORD_HASH_INVALID")))

            assertEquals(
                Outcome.Failure(DomainError.PasswordInvalid),
                repository.answer(CHAT, MESSAGE, CallbackPayload.DataWithPassword("x", CallbackData(byteArrayOf(1)))),
            )
        }

    @Test
    fun `a login button asks for confirmation or opens its link`() =
        runTest {
            client.on(
                TdApi.GetLoginUrlInfo::class,
                TdResult.Success(TdApi.LoginUrlInfoRequestConfirmation("https://a.example", "a.example", 9, true)),
            )
            assertEquals(
                Outcome.Success(LoginUrlInfo.RequestConfirmation("https://a.example", "a.example", 9, true)),
                repository.loginUrlInfo(CHAT, MESSAGE, buttonId = 5),
            )
            assertEquals(5, client.sentOf<TdApi.GetLoginUrlInfo>().single().buttonId)

            client.on(TdApi.GetLoginUrlInfo::class, TdResult.Success(TdApi.LoginUrlInfoOpen("https://b.example", true)))
            assertEquals(
                Outcome.Success(LoginUrlInfo.Open("https://b.example", skipConfirmation = true)),
                repository.loginUrlInfo(CHAT, MESSAGE, buttonId = 5),
            )
        }

    @Test
    fun `logs in with or without letting the bot write`() =
        runTest {
            client.on(TdApi.GetLoginUrl::class, TdResult.Success(TdApi.HttpUrl("https://a.example/?auth=1")))

            assertEquals(Outcome.Success("https://a.example/?auth=1"), repository.loginUrl(CHAT, MESSAGE, 5, true))
            val sent = client.sentOf<TdApi.GetLoginUrl>().single()
            assertEquals(5L, sent.buttonId)
            assertTrue(sent.allowWriteAccess)
        }

    @Test
    fun `shares picked users and a chat with the bot of a keyboard button`() =
        runTest {
            client.on(TdApi.ShareUsersWithBot::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ShareChatWithBot::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.shareUsers(CHAT, MESSAGE, 3, persistentListOf(7L, 8L)))
            assertEquals(Outcome.Success(Unit), repository.shareChat(CHAT, MESSAGE, 4, sharedChatId = -100L))

            val users = client.sentOf<TdApi.ShareUsersWithBot>().single()
            assertArrayEquals(longArrayOf(7, 8), users.sharedUserIds)
            assertEquals(MESSAGE, (users.source as TdApi.KeyboardButtonSourceMessage).messageId)
            assertFalse(users.onlyCheck)
            val chat = client.sentOf<TdApi.ShareChatWithBot>().single()
            assertEquals(4 to -100L, chat.buttonId to chat.sharedChatId)
        }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 7L
    }
}

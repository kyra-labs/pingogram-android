package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OauthLoginRequest
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TdlibOauthLoginRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibOauthLoginRepository(client)

    private fun info(
        matchCodeFirst: Boolean = true,
        tonWallet: Boolean = false,
    ) = TdApi.OauthLinkInfo(
        USER,
        URL,
        "example.org",
        false,
        "Example",
        BOT,
        true,
        true,
        tonWallet,
        "",
        "",
        "",
        "Firefox 145",
        "Linux",
        "203.0.113.7",
        "Berlin, Germany",
        matchCodeFirst,
        arrayOf("17", "42", "88"),
    )

    @Test
    fun `reads what the request asks for, as a link from outside the app`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info()))

            val request = (repository.request(URL) as Outcome.Success).value

            assertEquals(
                OauthLoginRequest(
                    domain = "example.org",
                    appName = "Example",
                    isApp = false,
                    botUserId = BOT,
                    asksToMessage = true,
                    asksForPhoneNumber = true,
                    asksForTonWallet = false,
                    browser = "Firefox 145",
                    platform = "Linux",
                    location = "Berlin, Germany",
                    matchCodes = listOf("17", "42", "88"),
                ),
                request,
            )
            assertEquals("", client.sentOf<TdApi.GetOauthLinkInfo>().single().inAppOrigin)
        }

    @Test
    fun `asks for no code where TDLib does not want one first`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = false)))

            val request = (repository.request(URL) as Outcome.Success).value

            assertFalse(request.needsMatchCode)
        }

    @Test
    fun `checks the code, accepts with only what the user allowed, and never shares a TON wallet`() =
        runTest {
            client.on(TdApi.CheckOauthRequestMatchCode::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.AcceptOauthRequest::class, TdResult.Success(TdApi.HttpUrl("https://example.org/done")))

            repository.checkMatchCode(URL, "42")
            val accepted = repository.accept(URL, "42", allowMessages = false, sharePhoneNumber = true)

            assertEquals(Outcome.Success("https://example.org/done"), accepted)
            assertEquals("42", client.sentOf<TdApi.CheckOauthRequestMatchCode>().single().matchCode)
            val accept = client.sentOf<TdApi.AcceptOauthRequest>().single()
            assertEquals("42", accept.matchCode)
            assertFalse(accept.allowWriteAccess)
            assertEquals(true, accept.allowPhoneNumberAccess)
            assertNull(accept.tonConnectConnectData)
        }

    @Test
    fun `declines the request`() =
        runTest {
            client.on(TdApi.DeclineOauthRequest::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.decline(URL))
            assertEquals(URL, client.sentOf<TdApi.DeclineOauthRequest>().single().url)
        }

    @Test
    fun `follows the requests Telegram announces`() =
        runTest {
            repository.requests.test {
                client.emit(TdApi.UpdateNewOauthRequest("example.org", "Berlin, Germany", URL))

                assertEquals(URL, awaitItem())
            }
        }

    private companion object {
        const val URL = "https://oauth.telegram.org/auth?bot_id=7"
        const val USER = 42L
        const val BOT = 7L
    }
}

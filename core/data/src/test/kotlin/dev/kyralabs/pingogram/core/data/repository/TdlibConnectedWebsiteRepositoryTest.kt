package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConnectedWebsite
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibConnectedWebsiteRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibConnectedWebsiteRepository(client)

    @Test
    fun `lists the websites in Telegram's order`() =
        runTest {
            client.on(
                TdApi.GetConnectedWebsites::class,
                TdResult.Success(
                    TdApi.ConnectedWebsites(
                        arrayOf(
                            TdApi.ConnectedWebsite(
                                1,
                                "example.org",
                                7,
                                "Firefox",
                                "Linux",
                                100,
                                200,
                                "203.0.113.1",
                                "Berlin",
                            ),
                            TdApi.ConnectedWebsite(2, "shop.example", 8, "Chrome", "Windows", 300, 400, "", ""),
                        ),
                    ),
                ),
            )

            val websites = (repository.websites() as Outcome.Success).value

            assertEquals(
                ConnectedWebsite(1, "example.org", 7, "Firefox", "Linux", 100, 200, "203.0.113.1", "Berlin"),
                websites.first(),
            )
            assertEquals(listOf(1L, 2L), websites.map { it.id })
        }

    @Test
    fun `logs one website or all of them out`() =
        runTest {
            client.on(TdApi.DisconnectWebsite::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DisconnectAllWebsites::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.disconnect(1))
            assertEquals(Outcome.Success(Unit), repository.disconnectAll())

            assertEquals(1L, client.sentOf<TdApi.DisconnectWebsite>().single().websiteId)
            assertEquals(1, client.sentOf<TdApi.DisconnectAllWebsites>().size)
        }

    @Test
    fun `a failure reaches the caller as a domain error`() =
        runTest {
            client.on(
                TdApi.GetConnectedWebsites::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            val failure = repository.websites() as Outcome.Failure

            assertTrue(failure.error is DomainError.FloodWait)
        }
}

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.JoinRequest
import dev.kyralabs.pingogram.core.domain.model.JoinRequestsPage
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TdlibJoinRequestRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibJoinRequestRepository(client)

    @Test
    fun `pages every link's requests after the last one`() =
        runTest {
            client.on(
                TdApi.GetChatJoinRequests::class,
                TdResult.Success(TdApi.ChatJoinRequests(3, arrayOf(TdApi.ChatJoinRequest(42, 1_700_000_000, "Hi")))),
            )

            val page = repository.requests(CHAT, query = "ad", after = JoinRequest(41, 1_700_000_100, "Yo"), limit = 20)

            assertEquals(
                Outcome.Success(JoinRequestsPage(persistentListOf(JoinRequest(42, 1_700_000_000, "Hi")), 3)),
                page,
            )
            val sent = client.sentOf<TdApi.GetChatJoinRequests>().single()
            assertEquals(Triple("", "ad", 20), Triple(sent.inviteLink, sent.query, sent.limit))
            assertEquals(41, sent.offsetRequest.userId)
        }

    @Test
    fun `approves one request and dismisses all of them`() =
        runTest {
            client.on(TdApi.ProcessChatJoinRequest::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ProcessChatJoinRequests::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.process(CHAT, 42, approve = true))
            assertEquals(Outcome.Success(Unit), repository.processAll(CHAT, approve = false))

            assertTrue(client.sentOf<TdApi.ProcessChatJoinRequest>().single().approve)
            val all = client.sentOf<TdApi.ProcessChatJoinRequests>().single()
            assertFalse(all.approve)
            assertEquals("", all.inviteLink)
        }

    @Test
    fun `logs no bio`() {
        assertFalse("secret" in JoinRequest(1, 2, "secret").toString())
    }

    private companion object {
        const val CHAT = -100L
    }
}

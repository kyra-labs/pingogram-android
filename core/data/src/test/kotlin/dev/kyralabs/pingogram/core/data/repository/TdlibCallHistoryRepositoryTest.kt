package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CallHistoryChange
import dev.kyralabs.pingogram.core.domain.model.CallHistoryEntry
import dev.kyralabs.pingogram.core.domain.model.CallHistoryPage
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** [TdlibCallHistoryRepository] on a [FakeTdlibClient]: the search, each outcome, deleting and the updates. */
class TdlibCallHistoryRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibCallHistoryRepository(client)

    private fun call(
        reason: TdApi.CallDiscardReason = TdApi.CallDiscardReasonHungUp(),
        duration: Int = 245,
        isVideo: Boolean = false,
    ) = TdApi.MessageCall(7, isVideo, reason, duration)

    private fun answer(
        vararg messages: TdApi.Message,
        next: String = "",
    ) = client.on(
        TdApi.SearchCallMessages::class,
        TdResult.Success(TdApi.FoundMessages(messages.size, arrayOf(*messages), next)),
    )

    private fun Outcome<CallHistoryPage>.page() = (this as Outcome.Success).value

    @Test
    fun `a page searches the calls from its offset, only missed ones when asked`() =
        runTest {
            answer()

            repository.page(offset = "abc", limit = 30, onlyMissed = true)

            val request = client.sentOf<TdApi.SearchCallMessages>().single()
            assertEquals("abc", request.offset)
            assertEquals(30, request.limit)
            assertEquals(true, request.onlyMissed)
        }

    @Test
    fun `a page holds each call with its person, date, kind and length, and where the next one starts`() =
        runTest {
            answer(
                message(id = 9, chatId = 42, date = 1_700_000_500, content = call(isVideo = true), isOutgoing = true),
                message(id = 3, chatId = 43, content = messageText()),
                next = "next",
            )

            val page = repository.page().page()

            assertEquals(
                listOf(
                    CallHistoryEntry(
                        chatId = 42,
                        messageId = 9,
                        userId = 42,
                        date = 1_700_000_500,
                        isOutgoing = true,
                        isVideo = true,
                        outcome = CallOutcome.Answered,
                        duration = 245,
                    ),
                ),
                page.entries,
            )
            assertEquals("next", page.nextOffset)
        }

    @Test
    fun `each call says how it went for the user`() =
        runTest {
            answer(
                message(id = 1, content = call(TdApi.CallDiscardReasonMissed(), 0)),
                message(id = 2, content = call(TdApi.CallDiscardReasonMissed(), 0), isOutgoing = true),
                message(id = 3, content = call(TdApi.CallDiscardReasonDeclined(), 0)),
                message(id = 4, content = call(TdApi.CallDiscardReasonHungUp(), 0)),
                message(id = 5, content = call(TdApi.CallDiscardReasonHungUp(), 0), isOutgoing = true),
                message(id = 6, content = call(TdApi.CallDiscardReasonDisconnected(), 12)),
            )

            assertEquals(
                listOf(
                    CallOutcome.Missed,
                    CallOutcome.Cancelled,
                    CallOutcome.Declined,
                    CallOutcome.Missed,
                    CallOutcome.Cancelled,
                    CallOutcome.Answered,
                ),
                repository
                    .page()
                    .page()
                    .entries
                    .map { it.outcome },
            )
        }

    @Test
    fun `a failed search fails the page`() =
        runTest {
            client.on(TdApi.SearchCallMessages::class, TdResult.Failure(TdError(400, "SOMETHING")))

            assertEquals(Outcome.Failure::class, repository.page()::class)
        }

    @Test
    fun `deleting removes the call message for the user only`() =
        runTest {
            client.on(TdApi.DeleteMessages::class, TdResult.Success(TdApi.Ok()))
            val entry = CallHistoryEntry(42, 9, 42, 0, false, false, CallOutcome.Missed, 0)

            assertEquals(Outcome.Success(Unit), repository.delete(entry))

            val request = client.sentOf<TdApi.DeleteMessages>().single()
            assertEquals(42L, request.chatId)
            assertArrayEquals(longArrayOf(9), request.messageIds)
            assertFalse(request.revoke)
        }

    @Test
    fun `a refused delete fails`() =
        runTest {
            client.on(TdApi.DeleteMessages::class, TdResult.Failure(TdError(400, "MESSAGE_DELETE_FORBIDDEN")))
            val entry = CallHistoryEntry(42, 9, 42, 0, false, false, CallOutcome.Missed, 0)

            val outcome = repository.delete(entry)

            assertEquals(true, outcome is Outcome.Failure && outcome.error !is DomainError.Network)
        }

    @Test
    fun `new call messages and permanent deletions change the history, other updates do not`() =
        runTest {
            repository.changes.test {
                client.emit(TdApi.UpdateNewMessage(message(id = 1, chatId = 42, content = messageText())))
                client.emit(TdApi.UpdateNewMessage(message(id = 2, chatId = 42, content = call())))
                client.emit(TdApi.UpdateDeleteMessages(42, longArrayOf(5), false, true))
                client.emit(TdApi.UpdateDeleteMessages(42, longArrayOf(6, 7), true, false))

                assertEquals(2L, (awaitItem() as CallHistoryChange.Added).entry.messageId)
                assertEquals(CallHistoryChange.Removed(42, persistentListOf(6L, 7L)), awaitItem())
                expectNoEvents()
            }
        }
}

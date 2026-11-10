package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Marking tasks of a checklist and adding tasks to it, each one request to TDLib. */
class TdlibChecklistRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChecklistRepository(client)

    @Test
    fun `marking sends the done and the not done tasks`() =
        runTest {
            client.on(TdApi.MarkChecklistTasksAsDone::class, TdResult.Success(TdApi.Ok()))

            val result = repository.markTasks(CHAT, MESSAGE, doneTaskIds = listOf(2, 3), notDoneTaskIds = listOf(1))

            assertEquals(Outcome.Success(Unit), result)
            val sent = client.sentOf<TdApi.MarkChecklistTasksAsDone>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
            assertArrayEquals(intArrayOf(2, 3), sent.markedAsDoneTaskIds)
            assertArrayEquals(intArrayOf(1), sent.markedAsNotDoneTaskIds)
        }

    @Test
    fun `adding sends the tasks with their ids as plain text`() =
        runTest {
            client.on(TdApi.AddChecklistTasks::class, TdResult.Success(TdApi.Ok()))

            repository.addTasks(CHAT, MESSAGE, listOf(NewChecklistTask(4, "Order chai"), NewChecklistTask(5, "Post")))

            val sent = client.sentOf<TdApi.AddChecklistTasks>().single()
            assertEquals(CHAT to MESSAGE, sent.chatId to sent.messageId)
            assertEquals(listOf(4 to "Order chai", 5 to "Post"), sent.tasks.map { it.id to it.text.text })
            assertEquals(0, sent.tasks.sumOf { it.text.entities.size })
        }

    @Test
    fun `a flood wait and a refusal come back as their errors`() =
        runTest {
            client.on(
                TdApi.MarkChecklistTasksAsDone::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")),
            )
            client.on(TdApi.AddChecklistTasks::class, TdResult.Failure(TdError(400, "MESSAGE_NOT_MODIFIED")))

            val marked = repository.markTasks(CHAT, MESSAGE, listOf(1), emptyList())
            val added = repository.addTasks(CHAT, MESSAGE, listOf(NewChecklistTask(4, "Post")))

            assertEquals(Outcome.Failure(DomainError.FloodWait(9)), marked)
            assertEquals(Outcome.Failure(DomainError.Unknown(400, "MESSAGE_NOT_MODIFIED")), added)
        }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 7L
    }
}

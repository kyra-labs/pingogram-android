package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.PollFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `updatePoll`, which names no chat, in the history windows: the poll it carries replaces the poll of every loaded
 * message that shows it, in every chat, also while a page loads.
 */
class TdlibMessageRepositoryPollTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibMessageRepository(client, backgroundScope)

    /** getChatHistory answers with [byChat]'s messages of the chat asked for. */
    private fun serve(byChat: Map<Long, List<TdApi.Message>>) =
        client.on(TdApi.GetChatHistory::class) { request ->
            TdResult.Success(messages(*byChat[request.chatId].orEmpty().toTypedArray()))
        }

    private suspend fun TdlibMessageRepository.poll(
        chatId: Long,
        messageId: Long,
    ): Poll =
        (
            history(chatId)
                .first()
                .messages
                .single { it.id == messageId }
                .content as MessageContent.Poll
        ).poll

    @Test
    fun `an updated poll reaches every message that shows it, in every chat`() =
        runTest {
            serve(
                mapOf(
                    CHAT to listOf(pollMessage(2, CHAT), message(id = 1, chatId = CHAT)),
                    OTHER_CHAT to listOf(pollMessage(5, OTHER_CHAT)),
                ),
            )
            val repository = repository()
            repository.loadOlder(CHAT)
            repository.loadOlder(OTHER_CHAT)

            client.emit(TdApi.UpdatePoll(voted()))
            runCurrent()

            assertEquals(listOf(3, 1), repository.poll(CHAT, 2).options.map { it.voterCount })
            assertEquals(listOf(3, 1), repository.poll(OTHER_CHAT, 5).options.map { it.voterCount })
            assertEquals(true, repository.poll(CHAT, 2).hasVoted)
        }

    @Test
    fun `another poll's update changes nothing`() =
        runTest {
            serve(mapOf(CHAT to listOf(pollMessage(2, CHAT))))
            val repository = repository()
            repository.loadOlder(CHAT)
            val before = repository.history(CHAT).first()

            client.emit(TdApi.UpdatePoll(voted(id = OTHER_POLL)))
            runCurrent()

            assertEquals(before, repository.history(CHAT).first())
        }

    @Test
    fun `an update that comes while a page loads applies to the page`() =
        runTest {
            val answer = CompletableDeferred<Unit>()
            client.on(TdApi.GetChatHistory::class) {
                answer.await()
                TdResult.Success(messages(pollMessage(2, CHAT)))
            }
            val repository = repository()
            val load = launch { repository.loadOlder(CHAT) }
            runCurrent()

            client.emit(TdApi.UpdatePoll(voted()))
            runCurrent()
            answer.complete(Unit)
            load.join()

            assertEquals(listOf(3, 1), repository.poll(CHAT, 2).options.map { it.voterCount })
        }

    private fun pollMessage(
        id: Long,
        chatId: Long,
    ) = message(id = id, chatId = chatId, content = PollFixtures.messagePoll(PollFixtures.poll(id = POLL)))

    private fun voted(id: Long = POLL) =
        PollFixtures.poll(
            id = id,
            options =
                arrayOf(
                    PollFixtures.pollOption("Pizza", voterCount = 3, votePercentage = 75, isChosen = true),
                    PollFixtures.pollOption("Salad", voterCount = 1, votePercentage = 25),
                ),
            totalVoterCount = 4,
            canSeeResults = true,
        )

    private companion object {
        const val CHAT = 100L
        const val OTHER_CHAT = 200L
        const val POLL = 77L
        const val OTHER_POLL = 78L
    }
}

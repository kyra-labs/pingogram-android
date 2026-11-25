package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.replyTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [RepliedMessages] on its own: what it asks for, once, and what it keeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class RepliedMessagesTest {
    private val repository = FakeMessageRepository()

    @Test
    fun `asks once for each replied message that is not loaded`() =
        runTest {
            val replies = RepliedMessages(PRIVATE_CHAT, repository, backgroundScope)
            repository.onRepliedMessage = { _, id -> Outcome.Success(message(id - 10)) }
            val loaded = history(message(1), message(11, replyTo = replyTo(1)), message(12, replyTo = replyTo(2)))

            replies.fetchMissing(loaded)
            runCurrent()
            replies.fetchMissing(loaded)
            runCurrent()

            assertEquals(listOf(PRIVATE_CHAT to 12L), repository.repliedRequests)
            assertEquals(mapOf(12L to ReplyState.Loaded(message(2))), replies.state.value)
        }

    @Test
    fun `keeps a failure and does not ask again`() =
        runTest {
            val replies = RepliedMessages(PRIVATE_CHAT, repository, backgroundScope)
            repository.onRepliedMessage = { _, _ -> Outcome.Failure(DomainError.NotFound) }
            val loaded = history(message(11, replyTo = replyTo(1)))

            replies.fetchMissing(loaded)
            runCurrent()
            replies.fetchMissing(loaded)
            runCurrent()

            assertEquals(1, repository.repliedRequests.size)
            assertEquals(mapOf(11L to ReplyState.Failed(DomainError.NotFound)), replies.state.value)
        }
}

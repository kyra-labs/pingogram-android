package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SavedTopicKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Saved Messages topics: the cache of `updateSavedMessagesTopic`, their order, loading them, and their messages. */
class TdlibSavedMessagesTopicRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibSavedMessagesTopicRepository(client, backgroundScope)

    private fun update(
        id: Long,
        type: TdApi.SavedMessagesTopicType = TdApi.SavedMessagesTopicTypeSavedFromChat(id * 10),
        isPinned: Boolean = false,
        order: Long = id,
        lastMessage: TdApi.Message? = null,
    ) = TdApi.UpdateSavedMessagesTopic(TdApi.SavedMessagesTopic(id, type, isPinned, order, lastMessage, null))

    @Test
    fun `lists announced topics pinned first, then by order, leaving unlisted ones out`() =
        runTest {
            val repository = repository()

            client.emit(update(id = 1, order = 5))
            client.emit(update(id = 2, order = 9))
            client.emit(update(id = 3, order = 1, isPinned = true))
            client.emit(update(id = 4, order = 0))

            assertEquals(listOf(3L, 2L, 1L), repository.topics.first().map { it.id })
        }

    @Test
    fun `maps each kind of topic and its last message, and follows a change`() =
        runTest {
            val repository = repository()

            client.emit(update(id = 1, type = TdApi.SavedMessagesTopicTypeMyNotes(), order = 3))
            client.emit(update(id = 2, type = TdApi.SavedMessagesTopicTypeAuthorHidden(), order = 2))
            client.emit(update(id = 3, order = 1, lastMessage = TdApiFixtures.message(id = 7, chatId = 100)))
            client.emit(update(id = 1, type = TdApi.SavedMessagesTopicTypeMyNotes(), order = 3, isPinned = true))

            val topics = repository.topics.first()
            assertEquals(
                listOf(SavedTopicKind.MyNotes, SavedTopicKind.AuthorHidden, SavedTopicKind.SavedFrom(30)),
                topics.map { it.kind },
            )
            assertTrue(topics.first().isPinned)
            assertEquals(7L, topics.last().lastMessage?.id)
            assertEquals(MessageTopic.SavedMessages(3), topics.last().topic)
        }

    @Test
    fun `forgets the topics when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(update(id = 1))

            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))

            assertTrue(repository.topics.first().isEmpty())
        }

    @Test
    fun `loading more reports when every topic is loaded, and fails with a flood wait`() =
        runTest {
            val repository = repository()
            client.on(TdApi.LoadSavedMessagesTopics::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(true), repository.loadMore())
            assertEquals(100, client.sentOf<TdApi.LoadSavedMessagesTopics>().single().limit)

            client.on(TdApi.LoadSavedMessagesTopics::class, TdResult.Failure(TdError(404, "Not Found")))
            assertEquals(Outcome.Success(false), repository.loadMore())

            client.on(
                TdApi.LoadSavedMessagesTopics::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")),
            )
            assertEquals(Outcome.Failure(DomainError.FloodWait(7)), repository.loadMore())
        }
}

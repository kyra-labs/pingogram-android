package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.SharedMediaPage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.document
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageDocument
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messagePhoto
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [TdlibSharedMediaRepository] on a [FakeTdlibClient]: each kind's filter, paging and what a page leaves out. */
class TdlibSharedMediaRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSharedMediaRepository(client)

    private fun answer(
        vararg messages: TdApi.Message,
        next: Long = 0,
        total: Int = messages.size,
    ) = client.on(
        TdApi.SearchChatMessages::class,
        TdResult.Success(TdApi.FoundChatMessages(total, arrayOf(*messages), next)),
    )

    private fun Outcome<SharedMediaPage>.page() = (this as Outcome.Success).value

    @Test
    fun `each kind searches the chat with its filter`() =
        runTest {
            answer()
            val filters =
                SharedMediaKind.entries.map { kind ->
                    repository.page(chatId = 100, kind = kind)
                    client
                        .sentOf<TdApi.SearchChatMessages>()
                        .last()
                        .filter.javaClass
                }

            assertEquals(
                listOf(
                    TdApi.SearchMessagesFilterPhotoAndVideo::class.java,
                    TdApi.SearchMessagesFilterDocument::class.java,
                    TdApi.SearchMessagesFilterUrl::class.java,
                    TdApi.SearchMessagesFilterVoiceNote::class.java,
                    TdApi.SearchMessagesFilterAudio::class.java,
                    TdApi.SearchMessagesFilterAnimation::class.java,
                ),
                filters,
            )
        }

    @Test
    fun `a page starts at the message given, with no query, topic or sender`() =
        runTest {
            answer()

            repository.page(chatId = 100, kind = SharedMediaKind.Files, fromMessageId = 77, limit = 30)

            val request = client.sentOf<TdApi.SearchChatMessages>().single()
            assertEquals(100L, request.chatId)
            assertEquals(77L, request.fromMessageId)
            assertEquals(0, request.offset)
            assertEquals(30, request.limit)
            assertEquals("", request.query)
            assertNull(request.topicId)
            assertNull(request.senderId)
        }

    @Test
    fun `a page holds the messages of its kind newest first and where the next one starts`() =
        runTest {
            answer(
                message(id = 3, content = messagePhoto()),
                message(id = 9, content = messagePhoto()),
                message(id = 5, content = messageDocument(document())),
                next = 3,
                total = 40,
            )

            val page = repository.page(chatId = 100, kind = SharedMediaKind.PhotosAndVideos).page()

            assertEquals(listOf(9L, 3L), page.messages.map { it.id })
            assertEquals(3L, page.nextFromMessageId)
            assertEquals(40, page.totalCount)
        }

    @Test
    fun `self-destructing media is left out`() =
        runTest {
            answer(message(id = 4, content = messagePhoto(isSecret = true)), message(id = 2, content = messagePhoto()))

            assertEquals(
                listOf(2L),
                repository
                    .page(chatId = 100, kind = SharedMediaKind.PhotosAndVideos)
                    .page()
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun `links keep every message TDLib found, captions included`() =
        runTest {
            answer(
                message(id = 2, content = messageText("https://example.org")),
                message(id = 1, content = messagePhoto()),
            )

            assertEquals(
                listOf(2L, 1L),
                repository
                    .page(chatId = 100, kind = SharedMediaKind.Links)
                    .page()
                    .messages
                    .map { it.id },
            )
        }

    @Test
    fun `an unknown total count is null`() =
        runTest {
            answer(total = -1)

            assertNull(repository.page(chatId = 100, kind = SharedMediaKind.Music).page().totalCount)
        }

    @Test
    fun `errors are mapped`() =
        runTest {
            client.on(
                TdApi.SearchChatMessages::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")),
            )

            val failure = repository.page(chatId = 100, kind = SharedMediaKind.Voice)

            assertInstanceOf(Outcome.Failure::class.java, failure)
            assertEquals(DomainError.FloodWait(3), (failure as Outcome.Failure).error)
        }
}

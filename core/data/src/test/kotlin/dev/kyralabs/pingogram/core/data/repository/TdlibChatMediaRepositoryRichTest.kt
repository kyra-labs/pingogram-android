package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messageText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.photo
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [TdlibChatMediaRepository.richMedia]: a rich message's photos, videos and GIFs as the media viewer's one page. */
class TdlibChatMediaRepositoryRichTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatMediaRepository(client)
    private val messages = mutableMapOf<Long, TdApi.Message>()

    init {
        client.on(TdApi.GetMessage::class) { request ->
            messages[request.messageId]?.let { TdResult.Success(it) } ?: TdResult.Failure(TdError(404, "Not Found"))
        }
    }

    private fun Outcome<ChatMediaPage>.page() = (this as Outcome.Success).value

    private fun richPost(): TdApi.MessageRichMessage {
        fun photoBlock(caption: String) =
            TdApi.PageBlockPhoto(photo(), TdApi.PageBlockCaption(TdApi.RichTextPlain(caption), null), "", false)
        val blocks =
            arrayOf<TdApi.PageBlock>(
                TdApi.PageBlockTitle(TdApi.RichTextPlain("News")),
                photoBlock("First"),
                TdApi.PageBlockDetails(TdApi.RichTextPlain("More"), arrayOf(photoBlock("Second")), false),
            )
        return TdApi.MessageRichMessage(TdApi.RichMessage(blocks, false, true))
    }

    @Test
    fun `a rich message's media are one page, oldest first by their ids, with their captions, both ends reached`() =
        runTest {
            messages[POST] = message(id = POST, chatId = CHAT, content = richPost())

            val page = repository.richMedia(CHAT, POST).page()

            // Newest first, as every page: the second media has the higher id.
            assertEquals(listOf(richMediaId(POST, 1), richMediaId(POST, 0)), page.messages.map { it.id })
            assertEquals(
                listOf("Second", "First"),
                page.messages.map { (it.content as MessageContent.Photo).caption.text },
            )
            assertTrue(page.reachedOldest && page.reachedNewest)
            assertEquals(ChatMediaFilter.PhotosAndVideos, page.filter)
            assertEquals(2, page.totalCount)
        }

    @Test
    fun `a message without media, or gone, is not found`() =
        runTest {
            messages[POST] = message(id = POST, chatId = CHAT, content = messageText("Hello"))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository.richMedia(CHAT, POST))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.richMedia(CHAT, POST * 2))
        }

    private companion object {
        const val CHAT = -100L
        const val POST = 7L shl 20
    }
}

package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.ImageInfo
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.UploadCleaner
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ShareResult
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** What [TdlibShareRepository] sends to several chats, and how it keeps and frees the copies of shared files. */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibShareRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository(): TdlibShareRepository {
        val io = StandardTestDispatcher(testScheduler)
        val cleaner = UploadCleaner(client, files, io, backgroundScope, maxAge = null)
        runCurrent()
        return TdlibShareRepository(
            client,
            files,
            content,
            media,
            cleaner,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
            io,
        )
    }

    private fun answerSends() {
        client.on(TdApi.SendMessage::class, TdResult.Success(message(id = -1)))
        client.on(TdApi.SendMessageAlbum::class) { album ->
            TdResult.Success(messages(*Array(album.inputMessageContents.size) { message(id = -1L - it) }))
        }
    }

    private fun text(text: String) = FormattedText(text, persistentListOf())

    /** Each message sent, as its chat and the kinds of its contents. */
    private fun sent(): List<String> =
        client.sent.mapNotNull { function ->
            when (function) {
                is TdApi.SendMessage -> {
                    "${function.chatId}:${function.inputMessageContent.kind}"
                }

                is TdApi.SendMessageAlbum -> {
                    "${function.chatId}:" + function.inputMessageContents.joinToString(",") { it.kind }
                }

                else -> {
                    null
                }
            }
        }

    private val TdApi.InputMessageContent.kind: String get() = javaClass.simpleName.removePrefix("InputMessage")

    private fun uploads(): List<File> = File(cacheDir, "uploads").listFiles()?.toList().orEmpty()

    @Test
    fun `a shared text goes to every chat as a message with a link preview, the comment first`() =
        runTest {
            answerSends()

            val result =
                repository().share(
                    chatIds = listOf(1, 2),
                    content = SharedContent(text("https://example.com")),
                    comment = text("Look"),
                )

            assertEquals(ShareResult(sent = 2, failed = 0), result)
            assertEquals(listOf("1:Text", "1:Text", "2:Text", "2:Text"), sent())
            val texts =
                client.sent.filterIsInstance<TdApi.SendMessage>().map {
                    it.inputMessageContent as TdApi.InputMessageText
                }
            assertEquals(
                listOf("Look", "https://example.com", "Look", "https://example.com"),
                texts.map { it.text.text },
            )
            assertTrue(texts.all { it.linkPreviewOptions == null && !it.clearDraft })
        }

    @Test
    fun `shared photos are prepared once and sent to each chat as an album with the text as caption`() =
        runTest {
            answerSends()
            media.images[1] = ImageInfo(1600, 1200, Orientation.Normal)
            content.add("content://a", "a.jpg", "image/jpeg", bytes = byteArrayOf(1))
            content.add("content://b", "b.jpg", "image/jpeg", bytes = byteArrayOf(1))
            val shared =
                SharedContent(text("Holidays"), persistentListOf("content://a", "content://b"), AttachmentKind.Media)

            val result = repository().share(listOf(1, 2, 3), shared)

            assertEquals(ShareResult(sent = 3, failed = 0), result)
            assertEquals(listOf("1:Photo,Photo", "2:Photo,Photo", "3:Photo,Photo"), sent())
            val paths =
                client.sent.filterIsInstance<TdApi.SendMessageAlbum>().map { album ->
                    album.inputMessageContents.map {
                        ((it as TdApi.InputMessagePhoto).photo.photo as TdApi.InputFileLocal)
                            .path
                    }
                }
            assertEquals(1, paths.distinct().size, "every chat gets the same two copies")
            assertEquals(2, uploads().size)
            val caption =
                (
                    client.sent
                        .filterIsInstance<TdApi.SendMessageAlbum>()
                        .first()
                        .inputMessageContents[0] as TdApi.InputMessagePhoto
                ).caption
            assertEquals("Holidays", caption?.text)
        }

    @Test
    fun `the copies stay while held, and go once released and nothing uploads them`() =
        runTest {
            answerSends()
            content.add("content://doc", "report.pdf", "application/pdf")
            val repository = repository()

            repository.share(listOf(1, 2), SharedContent(files = persistentListOf("content://doc")))
            runCurrent()
            assertEquals(1, uploads().size, "held until the grace after the last chat")

            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            runCurrent()
            assertTrue(uploads().isEmpty())
        }

    @Test
    fun `a chat that refuses counts as failed, and the others still get it`() =
        runTest {
            client.on(TdApi.SendMessage::class) { send ->
                if (send.chatId == 2L) {
                    TdResult.Failure(TdError(400, "CHAT_WRITE_FORBIDDEN"))
                } else {
                    TdResult.Success(message(id = -1))
                }
            }

            val result = repository().share(listOf(1, 2, 3), SharedContent(text("Hi")))

            assertEquals(2, result.sent)
            assertEquals(1, result.failed)
            assertTrue(result.error != null)
            assertEquals(listOf("1:Text", "2:Text", "3:Text"), sent())
        }

    @Test
    fun `a flood wait stops the share, the chats left counting as failed`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Failure(TdApiFixtures.floodWait(30).toTdError()))

            val result = repository().share(listOf(1, 2, 3), SharedContent(text("Hi")))

            assertEquals(0, result.sent)
            assertEquals(3, result.failed)
            assertTrue(result.error is DomainError.FloodWait)
            assertEquals(listOf("1:Text"), sent())
        }

    @Test
    fun `a file that cannot be read sends nothing and leaves no copy`() =
        runTest {
            answerSends()
            content.add("content://a", "a.pdf", "application/pdf")
            val shared = SharedContent(files = persistentListOf("content://a", "content://gone"))

            val result = repository().share(listOf(1, 2), shared)
            runCurrent()

            assertEquals(ShareResult(sent = 0, failed = 2, error = DomainError.FileUnavailable), result)
            assertTrue(sent().isEmpty())
            assertFalse(uploads().any { it.exists() })
        }

    @Test
    fun `nothing to share sends nothing`() =
        runTest {
            assertEquals(ShareResult(sent = 0, failed = 0), repository().share(listOf(1), SharedContent()))
            assertEquals(ShareResult(sent = 0, failed = 0), repository().share(emptyList(), SharedContent(text("Hi"))))
            assertTrue(client.sent.isEmpty())
        }
}

private fun TdApi.Error.toTdError() = TdError(code, message)

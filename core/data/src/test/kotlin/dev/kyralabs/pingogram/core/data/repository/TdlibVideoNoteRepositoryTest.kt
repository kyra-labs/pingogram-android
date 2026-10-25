package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.RecordedVideoNote
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.VideoNoteFiles
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What [TdlibVideoNoteRepository] does with recorded round video messages: their files, and what it sends. */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibVideoNoteRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        TdlibVideoNoteRepository(
            client = client,
            files = files,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private suspend fun TestScope.newFiles(): VideoNoteFiles =
        (repository().newVideoNoteFiles() as Outcome.Success).value

    /** A recording of 12 s at 384 px in [files], with its thumbnail written. */
    private fun recorded(files: VideoNoteFiles): RecordedVideoNote {
        File(files.thumbnail).writeText("jpeg")
        return RecordedVideoNote(files.video, 12, 384, AttachmentThumbnail(files.thumbnail, 320, 320))
    }

    @Test
    fun `a video note's files share a new directory of the uploads, the video empty, the thumbnail not made yet`() =
        runTest {
            val files = newFiles()

            val video = File(files.video)
            assertEquals("video_note.mp4", video.name)
            assertEquals(0L, video.length())
            assertTrue(this@TdlibVideoNoteRepositoryTest.files.isUpload(files.video))
            assertEquals(video.parentFile, File(files.thumbnail).parentFile)
            assertEquals("video_note.jpg", File(files.thumbnail).name)
            assertFalse(File(files.thumbnail).exists())
            assertEquals(
                listOf("video_note.mp4"),
                video.parentFile
                    .list()
                    .orEmpty()
                    .toList(),
            )
        }

    @Test
    fun `files that cannot be made fail as unavailable`() =
        runTest {
            val blocked = File(cacheDir, "blocked").apply { writeText("not a directory") }
            val repository =
                TdlibVideoNoteRepository(
                    client = client,
                    files = UploadFiles { blocked },
                    io = StandardTestDispatcher(testScheduler),
                    scope = backgroundScope,
                )

            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.newVideoNoteFiles())
        }

    @Test
    fun `a video note goes as an inputMessageVideoNote of its file, thumbnail, length and side, with its reply`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Success(message(id = -1)))
            val files = newFiles()

            val result =
                repository().sendVideoNote(CHAT, recorded(files), SendOptions(silent = true), replyTo = ReplyTarget(42))

            val request = client.sentOf<TdApi.SendMessage>().single()
            val content = request.inputMessageContent as TdApi.InputMessageVideoNote
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(files.video, (content.videoNote.videoNote as TdApi.InputFileLocal).path)
            val thumbnail = content.videoNote.thumbnail!!
            assertEquals(files.thumbnail, (thumbnail.thumbnail as TdApi.InputFileLocal).path)
            assertEquals(listOf(320, 320), listOf(thumbnail.width, thumbnail.height))
            assertEquals(12, content.videoNote.duration)
            assertEquals(384, content.videoNote.length)
            assertNull(content.selfDestructType)
            assertEquals(42L, (request.replyTo as TdApi.InputMessageReplyToMessage).messageId)
            assertTrue(request.options.disableNotification)
            assertTrue(File(files.video).exists())
        }

    @Test
    fun `a video note without a thumbnail goes without one, scheduled into a topic as the options say`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Success(message(id = -1)))
            val files = newFiles()
            val options = SendOptions(scheduledAt = 1_800_000_000L, topic = MessageTopic.Forum(7))

            repository().sendVideoNote(CHAT, RecordedVideoNote(files.video, 3, 384, thumbnail = null), options)

            val request = client.sentOf<TdApi.SendMessage>().single()
            assertNull((request.inputMessageContent as TdApi.InputMessageVideoNote).videoNote.thumbnail)
            assertNull(request.replyTo)
            assertTrue(request.options.schedulingState is TdApi.MessageSchedulingStateSendAtDate)
            assertTrue(request.topicId != null)
        }

    @Test
    fun `a refused video note fails and deletes its files`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(400, "CHAT_SEND_ROUNDVIDEOS_FORBIDDEN")))
            val files = newFiles()

            val result = repository().sendVideoNote(CHAT, recorded(files))
            runCurrent()

            assertTrue(result is Outcome.Failure)
            assertFalse(File(files.video).parentFile.exists())
        }

    @Test
    fun `a flood wait fails as one`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")))
            val files = newFiles()

            val result = repository().sendVideoNote(CHAT, recorded(files))

            assertEquals(Outcome.Failure(DomainError.FloodWait(9)), result)
        }

    @Test
    fun `discarding a video note deletes it with its thumbnail and directory, and nothing outside the uploads`() =
        runTest {
            val files = newFiles()
            File(files.thumbnail).writeText("jpeg")
            val elsewhere = File(cacheDir, "video_note.mp4").apply { writeText("data") }

            repository().discardVideoNote(files.video)
            repository().discardVideoNote(elsewhere.path)
            runCurrent()

            assertFalse(File(files.video).parentFile.exists())
            assertTrue(elsewhere.exists())
        }

    private companion object {
        const val CHAT = 100L
    }
}

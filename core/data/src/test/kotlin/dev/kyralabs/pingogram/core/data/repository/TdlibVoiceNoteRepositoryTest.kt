package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Waveform
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What [TdlibVoiceNoteRepository] does with recorded voice messages: their files, and what it sends for them. */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibVoiceNoteRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        TdlibVoiceNoteRepository(
            client = client,
            files = files,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private fun answerSends() {
        client.on(TdApi.SendMessage::class, TdResult.Success(message(id = -1)))
    }

    @Test
    fun `a voice note file is new, empty and alone in its directory, with the format's extension`() =
        runTest {
            val path = (repository().newVoiceNoteFile("ogg") as Outcome.Success).value

            val file = File(path)
            assertEquals("voice.ogg", file.name)
            assertEquals(0L, file.length())
            assertTrue(files.isUpload(path))
            assertEquals(
                listOf("voice.ogg"),
                file.parentFile
                    .list()
                    .orEmpty()
                    .toList(),
            )
        }

    @Test
    fun `a voice note goes as an inputMessageVoiceNote of its file, with its length, waveform and reply`() =
        runTest {
            answerSends()
            val waveform = byteArrayOf(1, 2, 3)
            val path = (repository().newVoiceNoteFile("ogg") as Outcome.Success).value
            val options = SendOptions(silent = true)

            val result = repository().sendVoiceNote(CHAT, RecordedVoiceNote(path, 7, Waveform(waveform)), options, 42)

            val request = client.sentOf<TdApi.SendMessage>().single()
            val content = request.inputMessageContent as TdApi.InputMessageVoiceNote
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(path, (content.voiceNote.voiceNote as TdApi.InputFileLocal).path)
            assertEquals(7, content.voiceNote.duration)
            assertArrayEquals(waveform, content.voiceNote.waveform)
            assertEquals(listOf<Any?>(null, null), listOf(content.caption, content.selfDestructType))
            assertEquals(42L, (request.replyTo as TdApi.InputMessageReplyToMessage).messageId)
            assertTrue(request.options.disableNotification)
            assertTrue(File(path).exists())
        }

    @Test
    fun `a refused voice note fails and deletes its file`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(400, "CHAT_SEND_VOICES_FORBIDDEN")))
            val path = (repository().newVoiceNoteFile("m4a") as Outcome.Success).value

            val result = repository().sendVoiceNote(CHAT, RecordedVoiceNote(path, 3, Waveform(ByteArray(0))))
            runCurrent()

            assertTrue(result is Outcome.Failure)
            assertFalse(File(path).exists())
            assertFalse(File(path).parentFile.exists())
        }

    @Test
    fun `discarding a voice note deletes its file with its directory, and nothing outside the uploads`() =
        runTest {
            val path = (repository().newVoiceNoteFile("ogg") as Outcome.Success).value
            val elsewhere = File(cacheDir, "voice.ogg").apply { writeText("data") }

            repository().discardVoiceNote(path)
            repository().discardVoiceNote(elsewhere.path)
            runCurrent()

            assertFalse(File(path).parentFile.exists())
            assertTrue(elsewhere.exists())
        }

    private companion object {
        const val CHAT = 100L
    }
}

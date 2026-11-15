package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Transcript
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Transcripts kept on the device by message, in a file of their own, gone at logout. */
class FileTranscriptRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileTranscriptRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileTranscriptRepositoryTest.file) =
        FileTranscriptRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    @Test
    fun `a transcript is kept by chat and message, and survives the app process`() =
        runTest {
            val transcript =
                Transcript("Reached the station,\nplatform 4.\tSee you!", isAutomatic = true, isFirst = true)
            assertEquals(Outcome.Success(Unit), repository().save(chatId = 7, messageId = 42, transcript))

            val again = repository()
            assertEquals(mapOf(42L to transcript), again.transcripts(7).first())
            assertTrue(again.transcripts(8).first().isEmpty())
        }

    @Test
    fun `saving again replaces the message's transcript`() =
        runTest {
            val repository = repository()
            repository.save(7, 42, Transcript("first"))
            repository.save(7, 42, Transcript("second"))

            assertEquals(mapOf(42L to Transcript("second")), repository().transcripts(7).first())
        }

    @Test
    fun `the oldest transcripts go once there are too many`() =
        runTest {
            val repository = repository()
            repeat(FileTranscriptRepository.MAX_TRANSCRIPTS + 2) { repository.save(1, it.toLong(), Transcript("$it")) }

            val kept = repository().transcripts(1).first()
            assertEquals(FileTranscriptRepository.MAX_TRANSCRIPTS, kept.size)
            assertFalse(0L in kept)
            assertFalse(1L in kept)
            assertTrue(2L in kept)
        }

    @Test
    fun `logging out deletes them`() =
        runTest {
            val repository = repository()
            repository.save(7, 42, Transcript("secret"))

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()

            assertFalse(file.exists())
            assertTrue(repository.transcripts(7).first().isEmpty())
        }

    @Test
    fun `the demo keeps them in memory only`() =
        runTest {
            val repository = repository(file = null)
            repository.save(7, 42, Transcript("demo"))

            assertEquals(mapOf(42L to Transcript("demo")), repository.transcripts(7).first())
            assertFalse(file.exists())
        }

    @Test
    fun `escaping keeps every character`() {
        val text = "a\\b\nc\td\re\\n"
        assertEquals(text, unescape(escape(text)))
        assertFalse('\n' in escape(text) || '\t' in escape(text))
    }
}

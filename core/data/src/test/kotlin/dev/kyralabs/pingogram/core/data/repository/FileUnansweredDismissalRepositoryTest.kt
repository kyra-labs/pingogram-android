package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
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

/** Dismissed unanswered chats kept on the device by chat, in a file of their own, gone at logout only. */
class FileUnansweredDismissalRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileUnansweredDismissalRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileUnansweredDismissalRepositoryTest.file) =
        FileUnansweredDismissalRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    @Test
    fun `a dismissal keeps the chat and its last message, and survives the app process`() =
        runTest {
            assertEquals(Outcome.Success(Unit), repository().dismiss(chatId = 7, messageId = 40))

            assertEquals(mapOf(7L to 40L), repository().dismissed.first())
            assertEquals("7\t40\n", file.readText())
        }

    @Test
    fun `dismissing again keeps the newer message, and lines that are not one are skipped`() =
        runTest {
            file.writeText("7\t40\nnot a line\n8\n9\t12\n")
            val repository = repository()

            repository.dismiss(chatId = 7, messageId = 55)

            assertEquals(mapOf(9L to 12L, 7L to 55L), repository.dismissed.first())
        }

    @Test
    fun `logging out forgets them, closing keeps them`() =
        runTest {
            val repository = repository()
            repository.dismiss(chatId = 7, messageId = 40)

            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())
            testScheduler.runCurrent()
            assertTrue(file.exists())

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()
            assertFalse(file.exists())
            assertEquals(emptyMap<Long, Long>(), repository.dismissed.first())
        }

    @Test
    fun `the demo keeps them in memory`() =
        runTest {
            val repository = repository(file = null)
            repository.dismiss(chatId = 7, messageId = 40)

            assertEquals(mapOf(7L to 40L), repository.dismissed.first())
            assertFalse(file.exists())
        }
}

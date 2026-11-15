package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Reminders of messages, kept on the device in a file of their own, the soonest first, gone at logout. */
class FileReminderRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileReminderRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileReminderRepositoryTest.file) =
        FileReminderRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    private fun reminder(
        messageId: Long,
        at: Long = messageId,
        done: Boolean = false,
    ) = Reminder(
        chatId = 7,
        messageId = messageId,
        at = at,
        note = "Book it\tby Friday\nplease",
        preview = BookmarkPreview(sender = "Meera", text = "Check the price"),
        done = done,
    )

    @Test
    fun `reminders survive the app process, the soonest first`() =
        runTest {
            val repository = repository()
            repository.save(reminder(1, at = 200))
            repository.save(reminder(2, at = 100, done = true))

            val again = repository()
            assertEquals(listOf(2L, 1L), again.reminders.first().map { it.messageId })
            assertEquals(reminder(1, at = 200), again.reminder(7, 1).first())
            assertEquals(reminder(2, at = 100, done = true), again.reminder(7, 2).first())
        }

    @Test
    fun `setting a message's reminder again replaces it`() =
        runTest {
            val repository = repository()
            repository.save(reminder(1, at = 100))
            repository.save(reminder(1, at = 500))

            assertEquals(listOf(reminder(1, at = 500)), repository.reminders.first())
        }

    @Test
    fun `a reminder is marked done and due again, and removed`() =
        runTest {
            val repository = repository()
            repository.save(reminder(1))

            repository.setDone(7, 1, done = true)
            assertTrue(repository().reminder(7, 1).first()!!.done)
            repository.setDone(7, 1, done = false)
            assertFalse(repository.reminder(7, 1).first()!!.done)

            repository.remove(7, 1)
            assertNull(repository().reminder(7, 1).first())
        }

    @Test
    fun `logging out deletes them`() =
        runTest {
            val repository = repository()
            repository.save(reminder(1))

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()

            assertFalse(file.exists())
            assertTrue(repository.reminders.first().isEmpty())
        }

    @Test
    fun `the demo keeps them in memory only`() =
        runTest {
            val repository = repository(file = null)
            repository.save(reminder(1))

            assertEquals(1, repository.reminders.first().size)
            assertFalse(file.exists())
        }
}

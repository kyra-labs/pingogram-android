package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
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
import java.time.Instant

/** Message reminders kept on the device by message, in a file of their own, gone at logout. */
class FileReminderRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileReminderRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileReminderRepositoryTest.file) =
        FileReminderRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    private fun reminder(
        chatId: Long = 7,
        messageId: Long = 42,
        at: Long = 1_000,
        note: String = "",
        isDone: Boolean = false,
    ) = MessageReminder(chatId, messageId, Instant.ofEpochSecond(at), note, isDone)

    @Test
    fun `a reminder is kept with its note and survives the app process, without the message's text`() =
        runTest {
            val reminder = reminder(note = "Reply about\nthe flat\tby Friday")
            assertEquals(Outcome.Success(Unit), repository().set(reminder))

            val again = repository()
            assertEquals(listOf(reminder), again.reminders.first())
            assertEquals(mapOf(42L to reminder), again.reminders(7).first())
            assertTrue(again.reminders(8).first().isEmpty())
            // The ids, the time, the flags and the note: nothing of the message itself.
            assertEquals("7\t42\t1000\t0\tReply about\\nthe flat\\tby Friday\n", file.readText())
        }

    @Test
    fun `setting again replaces the message's reminder, and they come the soonest first`() =
        runTest {
            val repository = repository()
            repository.set(reminder(messageId = 1, at = 3_000))
            repository.set(reminder(messageId = 2, at = 2_000))
            repository.set(reminder(messageId = 1, at = 1_000, isDone = true))

            assertEquals(
                listOf(reminder(messageId = 1, at = 1_000, isDone = true), reminder(messageId = 2, at = 2_000)),
                repository().reminders.first(),
            )
        }

    @Test
    fun `a note is trimmed and cut at its length`() =
        runTest {
            val repository = repository()
            repository.set(reminder(note = "  " + "a".repeat(MessageReminder.NOTE_LENGTH_MAX + 5) + "  "))

            assertEquals(
                MessageReminder.NOTE_LENGTH_MAX,
                repository.reminders
                    .first()
                    .single()
                    .note.length,
            )
        }

    @Test
    fun `deleting removes only that message's reminder`() =
        runTest {
            val repository = repository()
            repository.set(reminder(messageId = 1))
            repository.set(reminder(messageId = 2))

            repository.delete(chatId = 7, messageId = 1)

            assertEquals(listOf(2L), repository().reminders.first().map { it.messageId })
        }

    @Test
    fun `past the most kept, the done ones go first, then the oldest`() =
        runTest {
            val repository = repository()
            repeat(FileReminderRepository.MAX_REMINDERS) {
                repository.set(
                    reminder(
                        messageId = it.toLong(),
                        at =
                            10L + it,
                    ),
                )
            }
            repository.set(reminder(messageId = 999, at = 5_000, isDone = true))
            repository.set(reminder(messageId = 1_000, at = 6_000))

            val kept = repository().reminders.first()
            assertEquals(FileReminderRepository.MAX_REMINDERS, kept.size)
            assertFalse(kept.any { it.messageId == 999L })
            assertFalse(kept.any { it.messageId == 0L })
            assertTrue(kept.any { it.messageId == 1_000L })
        }

    @Test
    fun `logging out deletes them`() =
        runTest {
            val repository = repository()
            repository.set(reminder(note = "secret"))

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()

            assertFalse(file.exists())
            assertTrue(repository.reminders.first().isEmpty())
        }

    @Test
    fun `the demo keeps them in memory only`() =
        runTest {
            val repository = repository(file = null)
            repository.set(reminder())

            assertEquals(1, repository.reminders.first().size)
            assertFalse(directory.listFiles().orEmpty().any())
        }

    @Test
    fun `a line cut short is skipped`() =
        runTest {
            file.writeText("7\t42\t1000\t0\tok\n7\t43\n")

            assertEquals(listOf(reminder(note = "ok")), repository().reminders.first())
        }
}

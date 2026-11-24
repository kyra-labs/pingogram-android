package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
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

/** Birthday reminders kept on the device by user, one each, in a file of their own, gone at logout only. */
class FileBirthdayReminderRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val client = FakeTdlibClient()

    private val file get() = File(directory, FileBirthdayReminderRepository.FILE_NAME)

    private fun TestScope.repository(file: File? = this@FileBirthdayReminderRepositoryTest.file) =
        FileBirthdayReminderRepository(client, backgroundScope, file, StandardTestDispatcher(testScheduler))

    @Test
    fun `a reminder is kept with the day and month alone and survives the app process`() =
        runTest {
            assertEquals(Outcome.Success(Unit), repository().set(BirthdayReminder(7, 24, 11)))

            assertEquals(listOf(BirthdayReminder(7, 24, 11)), repository().reminders.first())
            assertEquals("7\t24\t11\n", file.readText())
        }

    @Test
    fun `setting again replaces the user's reminder, and deleting removes it`() =
        runTest {
            val repository = repository()
            repository.set(BirthdayReminder(8, 1, 2))
            repository.set(BirthdayReminder(7, 24, 11))
            repository.set(BirthdayReminder(7, 25, 11))
            assertEquals(listOf(BirthdayReminder(7, 25, 11), BirthdayReminder(8, 1, 2)), repository.reminders.first())

            repository.delete(7)

            assertEquals(listOf(BirthdayReminder(8, 1, 2)), repository().reminders.first())
        }

    @Test
    fun `lines that are not a reminder are skipped`() =
        runTest {
            file.writeText("7\t24\t11\nnot a line\n8\t32\t1\n9\t1\n10\t3\t4\n")

            assertEquals(
                listOf(BirthdayReminder(7, 24, 11), BirthdayReminder(10, 3, 4)),
                repository().reminders.first(),
            )
        }

    @Test
    fun `logging out deletes them, closing keeps them`() =
        runTest {
            val repository = repository()
            repository.set(BirthdayReminder(7, 24, 11))

            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())
            testScheduler.runCurrent()
            assertTrue(file.exists())

            client.emitAuthorizationState(TdApi.AuthorizationStateLoggingOut())
            testScheduler.runCurrent()
            assertFalse(file.exists())
            assertEquals(emptyList<BirthdayReminder>(), repository.reminders.first())
        }

    @Test
    fun `the demo keeps them in memory`() =
        runTest {
            val repository = repository(file = null)
            repository.set(BirthdayReminder(7, 24, 11))

            assertEquals(listOf(BirthdayReminder(7, 24, 11)), repository.reminders.first())
            assertFalse(file.exists())
        }
}

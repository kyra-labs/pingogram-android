package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreNotificationPromptRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreNotificationPromptRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `reads no answer before anything is saved`() =
        runTest {
            assertEquals(NotificationPrompt(), DataStoreNotificationPromptRepository(dataStore()).prompt.first())
        }

    @Test
    fun `emits each answer as it is saved`() =
        runTest {
            val repository = DataStoreNotificationPromptRepository(dataStore())

            repository.prompt.test {
                assertEquals(NotificationPrompt(), awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setRequested())
                assertEquals(NotificationPrompt(wasRequested = true), awaitItem())

                repository.dismiss()
                assertEquals(NotificationPrompt(isDismissed = true, wasRequested = true), awaitItem())
            }
        }

    @Test
    fun `answers survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreNotificationPromptRepository(dataStore(firstProcess)).dismiss()
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val restarted = DataStoreNotificationPromptRepository(dataStore()).prompt.first()

            assertEquals(NotificationPrompt(isDismissed = true), restarted)
        }

    @Test
    fun `answers that cannot be read count as none, and saving them fails`() =
        runTest {
            val repository = DataStoreNotificationPromptRepository(FailingDataStore())

            assertEquals(NotificationPrompt(), repository.prompt.first())
            assertTrue((repository.dismiss() as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

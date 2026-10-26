package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreDirectShareRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreDirectShareRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `chats are offered until turned off`() =
        runTest { assertTrue(DataStoreDirectShareRepository(dataStore()).enabled.first()) }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreDirectShareRepository(dataStore())

            repository.enabled.test {
                assertTrue(awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setEnabled(false))
                assertFalse(awaitItem())

                repository.setEnabled(true)
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `the setting survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreDirectShareRepository(dataStore(firstProcess)).setEnabled(false)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertFalse(DataStoreDirectShareRepository(dataStore()).enabled.first())
        }

    @Test
    fun `a setting that cannot be read offers the chats, and saving it fails`() =
        runTest {
            val repository = DataStoreDirectShareRepository(FailingDataStore())

            assertTrue(repository.enabled.first())
            val saved = repository.setEnabled(false)
            assertTrue((saved as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

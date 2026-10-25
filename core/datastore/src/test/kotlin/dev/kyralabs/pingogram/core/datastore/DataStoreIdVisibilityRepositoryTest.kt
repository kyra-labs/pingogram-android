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

/** [DataStoreIdVisibilityRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreIdVisibilityRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `IDs stay hidden until shown`() =
        runTest { assertFalse(DataStoreIdVisibilityRepository(dataStore()).shown.first()) }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreIdVisibilityRepository(dataStore())

            repository.shown.test {
                assertFalse(awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setShown(true))
                assertTrue(awaitItem())

                repository.setShown(false)
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `the setting survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreIdVisibilityRepository(dataStore(firstProcess)).setShown(true)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertTrue(DataStoreIdVisibilityRepository(dataStore()).shown.first())
        }

    @Test
    fun `a setting that cannot be read hides IDs, and saving it fails`() =
        runTest {
            val repository = DataStoreIdVisibilityRepository(FailingDataStore())

            assertFalse(repository.shown.first())
            val saved = repository.setShown(true)
            assertTrue((saved as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

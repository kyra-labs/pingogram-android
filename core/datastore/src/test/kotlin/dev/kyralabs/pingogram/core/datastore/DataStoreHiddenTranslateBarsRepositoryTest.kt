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

/** [DataStoreHiddenTranslateBarsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreHiddenTranslateBarsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `closes the bar of one chat only`() =
        runTest {
            val repository = DataStoreHiddenTranslateBarsRepository(dataStore())

            repository.isHidden(CHAT).test {
                assertFalse(awaitItem())

                assertEquals(Outcome.Success(Unit), repository.hide(CHAT))
                assertTrue(awaitItem())
            }
            assertFalse(repository.isHidden(OTHER_CHAT).first())
        }

    @Test
    fun `a closed bar stays closed in the next process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreHiddenTranslateBarsRepository(dataStore(firstProcess)).hide(CHAT)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertTrue(DataStoreHiddenTranslateBarsRepository(dataStore()).isHidden(CHAT).first())
        }

    @Test
    fun `a file that cannot be read closes nothing, and closing fails`() =
        runTest {
            val repository = DataStoreHiddenTranslateBarsRepository(FailingDataStore())

            assertFalse(repository.isHidden(CHAT).first())
            assertTrue((repository.hide(CHAT) as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }

    private companion object {
        const val CHAT = -100L
        const val OTHER_CHAT = 5L
    }
}

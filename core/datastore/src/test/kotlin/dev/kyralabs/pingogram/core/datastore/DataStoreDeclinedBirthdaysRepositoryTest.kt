package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreDeclinedBirthdaysRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreDeclinedBirthdaysRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `declines the suggestions of one chat only`() =
        runTest {
            val repository = DataStoreDeclinedBirthdaysRepository(dataStore())

            repository.declined(CHAT).test {
                assertEquals(emptySet<Long>(), awaitItem())

                assertEquals(Outcome.Success(Unit), repository.decline(CHAT, 7))
                assertEquals(setOf(7L), awaitItem())

                repository.decline(CHAT, 9)
                assertEquals(setOf(7L, 9L), awaitItem())
            }
            assertTrue(repository.declined(OTHER_CHAT).first().isEmpty())
        }

    @Test
    fun `a declined suggestion stays declined in the next process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreDeclinedBirthdaysRepository(dataStore(firstProcess)).decline(CHAT, 7)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(setOf(7L), DataStoreDeclinedBirthdaysRepository(dataStore()).declined(CHAT).first())
        }

    private companion object {
        const val CHAT = 42L
        const val OTHER_CHAT = 43L
    }
}

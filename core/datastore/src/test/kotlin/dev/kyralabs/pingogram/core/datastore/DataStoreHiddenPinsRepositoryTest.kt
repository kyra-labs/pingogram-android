package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreHiddenPinsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreHiddenPinsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `reads nothing hidden before a bar is hidden`() =
        runTest {
            assertNull(DataStoreHiddenPinsRepository(dataStore()).hiddenUpTo(CHAT).first())
        }

    @Test
    fun `keeps the newest pinned message of each chat apart`() =
        runTest {
            val repository = DataStoreHiddenPinsRepository(dataStore())

            repository.hiddenUpTo(CHAT).test {
                assertNull(awaitItem())

                assertEquals(Outcome.Success(Unit), repository.hide(CHAT, 7))
                assertEquals(7L, awaitItem())

                repository.hide(OTHER_CHAT, 3)
                repository.hide(CHAT, 9)
                assertEquals(9L, awaitItem())
            }
            assertEquals(3L, repository.hiddenUpTo(OTHER_CHAT).first())
        }

    @Test
    fun `the bar of a topic or thread is hidden on its own`() =
        runTest {
            val repository = DataStoreHiddenPinsRepository(dataStore())

            repository.hide(CHAT, 7, MessageTopic.Forum(2))
            repository.hide(CHAT, 8, MessageTopic.Thread(2))

            assertNull(repository.hiddenUpTo(CHAT).first())
            assertEquals(7L, repository.hiddenUpTo(CHAT, MessageTopic.Forum(2)).first())
            assertEquals(8L, repository.hiddenUpTo(CHAT, MessageTopic.Thread(2)).first())
            assertNull(repository.hiddenUpTo(CHAT, MessageTopic.Forum(3)).first())
        }

    @Test
    fun `a hidden bar stays hidden in the next process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreHiddenPinsRepository(dataStore(firstProcess)).hide(CHAT, 7)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(7L, DataStoreHiddenPinsRepository(dataStore()).hiddenUpTo(CHAT).first())
        }

    @Test
    fun `a file that cannot be read hides nothing, and hiding fails`() =
        runTest {
            val repository = DataStoreHiddenPinsRepository(FailingDataStore())

            assertNull(repository.hiddenUpTo(CHAT).first())
            assertTrue((repository.hide(CHAT, 7) as Outcome.Failure).error is DomainError.Unknown)
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

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
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

/** [DataStoreDoubleTapSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreDoubleTapSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `a double tap reacts on every message until changed`() =
        runTest {
            assertEquals(
                DoubleTapReactions(incoming = true, outgoing = true),
                DataStoreDoubleTapSettingsRepository(dataStore()).doubleTap.first(),
            )
        }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreDoubleTapSettingsRepository(dataStore())

            repository.doubleTap.test {
                assertEquals(DoubleTapReactions(), awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setDoubleTap(DoubleTapReactions(outgoing = false)))
                assertEquals(DoubleTapReactions(outgoing = false), awaitItem())

                repository.setDoubleTap(DoubleTapReactions(incoming = false, outgoing = false))
                assertEquals(DoubleTapReactions(incoming = false, outgoing = false), awaitItem())
            }
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreDoubleTapSettingsRepository(dataStore(firstProcess))
                .setDoubleTap(DoubleTapReactions(incoming = false))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                DoubleTapReactions(incoming = false),
                DataStoreDoubleTapSettingsRepository(dataStore()).doubleTap.first(),
            )
        }

    @Test
    fun `settings that cannot be read count as the defaults, and saving them fails`() =
        runTest {
            val repository = DataStoreDoubleTapSettingsRepository(FailingDataStore())

            assertEquals(DoubleTapReactions(), repository.doubleTap.first())
            val saved = repository.setDoubleTap(DoubleTapReactions(incoming = false))
            assertTrue((saved as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

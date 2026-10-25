package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
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

/** [DataStoreArchiveRowRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreArchiveRowRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `shows the full row until changed`() =
        runTest {
            assertEquals(ArchiveRowStyle.Full, DataStoreArchiveRowRepository(dataStore()).style.first())
        }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreArchiveRowRepository(dataStore())

            repository.style.test {
                assertEquals(ArchiveRowStyle.Full, awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setStyle(ArchiveRowStyle.Compact))
                assertEquals(ArchiveRowStyle.Compact, awaitItem())

                repository.setStyle(ArchiveRowStyle.Hidden)
                assertEquals(ArchiveRowStyle.Hidden, awaitItem())
            }
        }

    @Test
    fun `the style survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreArchiveRowRepository(dataStore(firstProcess)).setStyle(ArchiveRowStyle.Hidden)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(ArchiveRowStyle.Hidden, DataStoreArchiveRowRepository(dataStore()).style.first())
        }

    @Test
    fun `a style this version does not know shows the full row`() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit { it[stringPreferencesKey("chat_list.archive_row")] = "Floating" }

            assertEquals(ArchiveRowStyle.Full, DataStoreArchiveRowRepository(dataStore).style.first())
        }

    @Test
    fun `a style that cannot be read shows the full row, and saving it fails`() =
        runTest {
            val repository = DataStoreArchiveRowRepository(FailingDataStore())

            assertEquals(ArchiveRowStyle.Full, repository.style.first())
            assertTrue((repository.setStyle(ArchiveRowStyle.Hidden) as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

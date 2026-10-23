package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
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
import java.time.Instant

/** [DataStoreContactSyncSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreContactSyncSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `sync is off, never synced and its offer shown until changed`() =
        runTest {
            assertEquals(ContactSyncSettings(), DataStoreContactSyncSettingsRepository(dataStore()).settings.first())
        }

    @Test
    fun `each setting is saved, and clearing goes back to the defaults`() =
        runTest {
            val repository = DataStoreContactSyncSettingsRepository(dataStore())

            repository.setEnabled(true)
            repository.setLastSyncedAt(SYNCED)
            repository.setPromptDismissed(true)
            assertEquals(ContactSyncSettings(true, SYNCED, true), repository.settings.first())

            repository.setLastSyncedAt(null)
            assertEquals(ContactSyncSettings(true, null, true), repository.settings.first())

            assertEquals(Outcome.Success(Unit), repository.clear())
            assertEquals(ContactSyncSettings(), repository.settings.first())
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStoreContactSyncSettingsRepository(dataStore(firstProcess))
            first.setEnabled(true)
            first.setLastSyncedAt(SYNCED)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                ContactSyncSettings(enabled = true, lastSyncedAt = SYNCED),
                DataStoreContactSyncSettingsRepository(dataStore()).settings.first(),
            )
        }

    @Test
    fun `settings that cannot be read count as off, and saving fails`() =
        runTest {
            val repository = DataStoreContactSyncSettingsRepository(FailingDataStore())

            assertEquals(ContactSyncSettings(), repository.settings.first())
            assertTrue((repository.setEnabled(true) as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }

    private companion object {
        val SYNCED: Instant = Instant.ofEpochSecond(1_790_000_000)
    }
}

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
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

/** [DataStoreAutoDownloadRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreAutoDownloadRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private val custom =
        AutoDownloadRules(
            photos = false,
            videos = true,
            videoSizeLimit = 50_000_000,
            files = true,
            fileSizeLimit = 1_000_000,
            voiceAndVideoMessages = true,
        )

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository(dataStore: DataStore<Preferences> = dataStore()) =
        DataStoreAutoDownloadRepository(dataStore)

    @Test
    fun `reads the defaults before anything is saved`() =
        runTest {
            assertEquals(AutoDownloadSettings(), repository().settings.first())
        }

    @Test
    fun `saves the rules of one network and leaves the others at theirs`() =
        runTest {
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setRules(AutoDownloadNetwork.Roaming, custom))

            assertEquals(AutoDownloadSettings(roaming = custom), repository.settings.first())
        }

    @Test
    fun `emits every change, and nothing for rules saved again unchanged`() =
        runTest {
            val repository = repository()

            repository.settings.test {
                assertEquals(AutoDownloadSettings(), awaitItem())

                repository.setRules(AutoDownloadNetwork.Mobile, custom)
                assertEquals(AutoDownloadSettings(mobile = custom), awaitItem())

                repository.setRules(AutoDownloadNetwork.Mobile, custom)
                repository.setRules(AutoDownloadNetwork.WiFi, AutoDownloadRules.Nothing)
                assertEquals(AutoDownloadSettings(mobile = custom, wifi = AutoDownloadRules.Nothing), awaitItem())
            }
        }

    @Test
    fun `settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            repository(dataStore(firstProcess)).setRules(AutoDownloadNetwork.WiFi, custom)
            // One DataStore per file at a time: the first one goes with its scope.
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(AutoDownloadSettings(wifi = custom), repository().settings.first())
        }

    @Test
    fun `a size limit that is not positive reads as the default`() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit {
                it[longPreferencesKey("auto_download.wifi.video_size_limit")] = 0
                it[longPreferencesKey("auto_download.wifi.file_size_limit")] = -5
            }

            assertEquals(AutoDownloadSettings(), repository(dataStore).settings.first())
        }

    @Test
    fun `settings that cannot be read count as the defaults`() =
        runTest {
            assertEquals(AutoDownloadSettings(), DataStoreAutoDownloadRepository(FailingDataStore()).settings.first())
        }

    @Test
    fun `rules that cannot be written fail`() =
        runTest {
            val result = DataStoreAutoDownloadRepository(FailingDataStore()).setRules(AutoDownloadNetwork.WiFi, custom)

            assertTrue((result as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

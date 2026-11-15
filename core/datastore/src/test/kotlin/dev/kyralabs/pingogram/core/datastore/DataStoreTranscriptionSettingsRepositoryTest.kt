package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreTranscriptionSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreTranscriptionSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `nothing is transcribed by itself until the user turns it on`() =
        runTest {
            assertEquals(
                TranscriptionSettings(automatic = false, noticeShown = false),
                DataStoreTranscriptionSettingsRepository(dataStore()).settings.first(),
            )
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStoreTranscriptionSettingsRepository(dataStore(firstProcess))
            assertEquals(Outcome.Success(Unit), first.setAutomatic(true))
            assertEquals(Outcome.Success(Unit), first.markNoticeShown())
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                TranscriptionSettings(automatic = true, noticeShown = true),
                DataStoreTranscriptionSettingsRepository(dataStore()).settings.first(),
            )
        }
}

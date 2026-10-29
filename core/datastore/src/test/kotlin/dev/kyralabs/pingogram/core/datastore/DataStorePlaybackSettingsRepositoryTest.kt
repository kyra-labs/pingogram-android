package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
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

/** [DataStorePlaybackSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStorePlaybackSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `raise to listen is on and does not pause until changed`() =
        runTest {
            assertEquals(
                RaiseToListenSettings(enabled = true, pauseWhenLowered = false),
                DataStorePlaybackSettingsRepository(dataStore()).raiseToListen.first(),
            )
        }

    @Test
    fun `each setting is saved on its own and survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStorePlaybackSettingsRepository(dataStore(firstProcess))
            assertEquals(Outcome.Success(Unit), first.setPauseWhenLowered(true))
            assertEquals(Outcome.Success(Unit), first.setRaiseToListen(false))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                RaiseToListenSettings(enabled = false, pauseWhenLowered = true),
                DataStorePlaybackSettingsRepository(dataStore()).raiseToListen.first(),
            )
        }
}

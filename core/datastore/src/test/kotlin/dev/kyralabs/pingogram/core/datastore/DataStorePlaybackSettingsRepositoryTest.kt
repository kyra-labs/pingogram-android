package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSpeed
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

    @Test
    fun `voice messages keep their silences until skipping is turned on, which survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStorePlaybackSettingsRepository(dataStore(firstProcess))
            assertEquals(false, first.skipSilence.first())
            assertEquals(Outcome.Success(Unit), first.setSkipSilence(true))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val again = DataStorePlaybackSettingsRepository(dataStore())
            assertEquals(true, again.skipSilence.first())
            assertEquals(RaiseToListenSettings(), again.raiseToListen.first())
        }

    @Test
    fun `voice messages play at 1x until another speed is saved, which survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStorePlaybackSettingsRepository(dataStore(firstProcess))
            assertEquals(PlaybackSpeed.Normal, first.playbackSpeed.first())
            assertEquals(Outcome.Success(Unit), first.setPlaybackSpeed(PlaybackSpeed.Faster))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val again = DataStorePlaybackSettingsRepository(dataStore())
            assertEquals(PlaybackSpeed.Faster, again.playbackSpeed.first())
            assertEquals(false, again.skipSilence.first())
        }

    @Test
    fun `the speeds go round from 1x to 1_5x to 2x and back`() {
        assertEquals(
            listOf(PlaybackSpeed.Faster, PlaybackSpeed.Fastest, PlaybackSpeed.Normal),
            PlaybackSpeed.entries.map { it.next },
        )
        assertEquals(listOf(1f, 1.5f, 2f), PlaybackSpeed.entries.map { it.factor })
    }
}

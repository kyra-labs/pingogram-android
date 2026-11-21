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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreWallpaperSeedRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreWallpaperSeedRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `keeps a seed per wallpaper`() =
        runTest {
            val repository = DataStoreWallpaperSeedRepository(dataStore())

            repository.seed(WALLPAPER).test {
                assertNull(awaitItem())

                assertEquals(Outcome.Success(Unit), repository.setSeed(WALLPAPER, SEED))
                assertEquals(SEED, awaitItem())
            }
            assertNull(repository.seed(OTHER_WALLPAPER).first())
        }

    @Test
    fun `a seed stays in the next process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreWallpaperSeedRepository(dataStore(firstProcess)).setSeed(WALLPAPER, SEED)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(SEED, DataStoreWallpaperSeedRepository(dataStore()).seed(WALLPAPER).first())
        }

    @Test
    fun `seeds go into backups with the other wallpaper settings`() {
        assertEquals(true, "appearance.chat.wallpaper_seed." in BackedUpSettings)
    }

    private companion object {
        const val WALLPAPER = 5_402_011_234_567L
        const val OTHER_WALLPAPER = 5_402_011_234_568L
        const val SEED = 0xFFB4744B.toInt()
    }
}

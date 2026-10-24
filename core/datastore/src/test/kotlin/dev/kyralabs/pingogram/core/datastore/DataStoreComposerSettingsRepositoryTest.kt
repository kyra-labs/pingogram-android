package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SendKeys
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

/** [DataStoreComposerSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreComposerSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `a hardware keyboard's Enter sends, the on-screen keyboard's does not, until changed`() =
        runTest {
            assertEquals(SendKeys(), DataStoreComposerSettingsRepository(dataStore()).sendKeys.first())
        }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreComposerSettingsRepository(dataStore())

            repository.sendKeys.test {
                assertEquals(SendKeys(), awaitItem())

                val onScreen = SendKeys(hardwareKeyboard = true, onScreenKeyboard = true)
                assertEquals(Outcome.Success(Unit), repository.setSendKeys(onScreen))
                assertEquals(onScreen, awaitItem())

                repository.setSendKeys(SendKeys(hardwareKeyboard = false))
                assertEquals(SendKeys(hardwareKeyboard = false), awaitItem())
            }
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreComposerSettingsRepository(dataStore(firstProcess)).setSendKeys(SendKeys(onScreenKeyboard = true))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                SendKeys(onScreenKeyboard = true),
                DataStoreComposerSettingsRepository(dataStore()).sendKeys.first(),
            )
        }

    @Test
    fun `send with Ctrl+Enter, saved before, keeps Enter from sending until the next save`() =
        runTest {
            val store = dataStore()
            store.edit { it[booleanPreferencesKey("composer.send_with_ctrl_enter")] = true }
            val repository = DataStoreComposerSettingsRepository(store)

            assertEquals(SendKeys(hardwareKeyboard = false), repository.sendKeys.first())

            repository.setSendKeys(SendKeys())
            assertEquals(SendKeys(), repository.sendKeys.first())
            assertEquals(null, store.data.first()[booleanPreferencesKey("composer.send_with_ctrl_enter")])
        }

    @Test
    fun `settings that cannot be read count as the defaults, and saving them fails`() =
        runTest {
            val repository = DataStoreComposerSettingsRepository(FailingDataStore())

            assertEquals(SendKeys(), repository.sendKeys.first())
            assertTrue(
                (
                    repository.setSendKeys(
                        SendKeys(onScreenKeyboard = true),
                    ) as Outcome.Failure
                ).error is DomainError.Unknown,
            )
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

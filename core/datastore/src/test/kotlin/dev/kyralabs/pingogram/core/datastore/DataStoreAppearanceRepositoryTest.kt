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
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
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

/** [DataStoreAppearanceRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreAppearanceRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository(dataStore: DataStore<Preferences> = dataStore()) =
        DataStoreAppearanceRepository(dataStore)

    @Test
    fun `reads the defaults before anything is saved`() =
        runTest {
            assertEquals(AppearanceSettings(), repository().appearance.first())
        }

    @Test
    fun `saves each setting`() =
        runTest {
            val repository = repository()

            repository.setThemeMode(ThemeMode.Dark)
            repository.setDynamicColor(false)
            repository.setContrast(Contrast.High)
            repository.setAmoled(true)

            assertEquals(
                AppearanceSettings(
                    themeMode = ThemeMode.Dark,
                    dynamicColor = false,
                    contrast = Contrast.High,
                    amoled = true,
                ),
                repository.appearance.first(),
            )
        }

    @Test
    fun `saving succeeds`() =
        runTest {
            assertEquals(Outcome.Success(Unit), repository().setThemeMode(ThemeMode.Light))
        }

    @Test
    fun `emits every change, and nothing for a setting saved again unchanged`() =
        runTest {
            val repository = repository()

            repository.appearance.test {
                assertEquals(AppearanceSettings(), awaitItem())

                repository.setThemeMode(ThemeMode.Light)
                assertEquals(AppearanceSettings(themeMode = ThemeMode.Light), awaitItem())

                repository.setThemeMode(ThemeMode.Light)
                repository.setAmoled(true)
                assertEquals(AppearanceSettings(themeMode = ThemeMode.Light, amoled = true), awaitItem())
            }
        }

    @Test
    fun `settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = repository(dataStore(firstProcess))
            first.setContrast(Contrast.Medium)
            first.setDynamicColor(false)
            // One DataStore per file at a time: the first one goes with its scope.
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            val restarted = repository().appearance.first()

            assertEquals(AppearanceSettings(contrast = Contrast.Medium, dynamicColor = false), restarted)
        }

    @Test
    fun `a value saved by another version that this one does not know reads as the default`() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit {
                it[stringPreferencesKey("appearance.theme_mode")] = "Sepia"
                it[stringPreferencesKey("appearance.contrast")] = "Extreme"
            }

            assertEquals(AppearanceSettings(), repository(dataStore).appearance.first())
        }

    @Test
    fun `settings that cannot be read count as the defaults`() =
        runTest {
            val repository = DataStoreAppearanceRepository(FailingDataStore())

            assertEquals(AppearanceSettings(), repository.appearance.first())
        }

    @Test
    fun `a setting that cannot be written fails`() =
        runTest {
            val result = DataStoreAppearanceRepository(FailingDataStore()).setAmoled(true)

            assertTrue((result as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

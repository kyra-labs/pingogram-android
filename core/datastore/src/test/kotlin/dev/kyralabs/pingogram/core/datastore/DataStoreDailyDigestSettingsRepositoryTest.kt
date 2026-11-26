package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalTime

/** [DataStoreDailyDigestSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreDailyDigestSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) })

    @Test
    fun `off at 8 30 until set`() =
        runTest {
            val settings = DataStoreDailyDigestSettingsRepository(dataStore()).settings.first()

            assertEquals(DailyDigestSettings(enabled = false, time = LocalTime.of(8, 30)), settings)
        }

    @Test
    fun `keeps whether it comes and its time, to the minute`() =
        runTest {
            val repository = DataStoreDailyDigestSettingsRepository(dataStore())

            repository.setEnabled(true)
            repository.setTime(LocalTime.of(21, 5, 40))

            assertEquals(DailyDigestSettings(enabled = true, time = LocalTime.of(21, 5)), repository.settings.first())
        }

    @Test
    fun `a minute outside the day reads as 8 30`() =
        runTest {
            val store = dataStore()
            store.edit { it[intPreferencesKey("daily_digest.minute")] = 24 * 60 }

            assertEquals(LocalTime.of(8, 30), DataStoreDailyDigestSettingsRepository(store).settings.first().time)
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}

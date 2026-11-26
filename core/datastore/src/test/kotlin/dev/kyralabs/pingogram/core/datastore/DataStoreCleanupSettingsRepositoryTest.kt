package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreCleanupSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreCleanupSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.repository() =
        DataStoreCleanupSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) }),
        )

    @Test
    fun `on, after three months, until set`() =
        runTest {
            assertEquals(CleanupSettings(enabled = true, months = 3), repository().settings.first())
        }

    @Test
    fun `keeps whether it suggests and the months, and only the months offered`() =
        runTest {
            val repository = repository()

            repository.setEnabled(false)
            repository.setMonths(6)
            repository.setMonths(2)

            assertEquals(CleanupSettings(enabled = false, months = 6), repository.settings.first())
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreUnansweredSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreUnansweredSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.repository() =
        DataStoreUnansweredSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) }),
        )

    @Test
    fun `the button and three days until set`() =
        runTest {
            assertEquals(UnansweredSettings(showButton = true, afterDays = 3), repository().settings.first())
        }

    @Test
    fun `keeps the button and the days, and only the days offered`() =
        runTest {
            val repository = repository()

            repository.setShowButton(false)
            repository.setAfterDays(7)
            repository.setAfterDays(5)

            assertEquals(UnansweredSettings(showButton = false, afterDays = 7), repository.settings.first())
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}

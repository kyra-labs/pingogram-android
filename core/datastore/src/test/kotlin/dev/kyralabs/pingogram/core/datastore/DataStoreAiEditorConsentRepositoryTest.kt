package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreAiEditorConsentRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreAiEditorConsentRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `not read at first, then read for good`() =
        runTest {
            val firstProcess = Job()
            val repository =
                DataStoreAiEditorConsentRepository(dataStore(CoroutineScope(coroutineContext + firstProcess)))
            assertFalse(repository.acknowledged.first())

            assertEquals(Outcome.Success(Unit), repository.acknowledge())
            assertTrue(repository.acknowledged.first())
            firstProcess.cancelAndJoin()

            assertTrue(DataStoreAiEditorConsentRepository(dataStore()).acknowledged.first())
        }
}

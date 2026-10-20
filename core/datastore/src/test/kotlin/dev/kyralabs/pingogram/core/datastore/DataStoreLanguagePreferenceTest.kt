package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreLanguagePreference] on a real Preferences DataStore in a temporary file. */
class DataStoreLanguagePreferenceTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `follows the system until a language is chosen`() =
        runTest {
            assertNull(DataStoreLanguagePreference(dataStore()).languageTag.first())
        }

    @Test
    fun `emits each choice as it is saved, back to the system's language`() =
        runTest {
            val preference = DataStoreLanguagePreference(dataStore())

            preference.languageTag.test {
                assertNull(awaitItem())

                assertEquals(Outcome.Success(Unit), preference.setLanguageTag("pt-BR"))
                assertEquals("pt-BR", awaitItem())

                assertEquals(Outcome.Success(Unit), preference.setLanguageTag(null))
                assertNull(awaitItem())
            }
        }

    @Test
    fun `the choice survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreLanguagePreference(dataStore(firstProcess)).setLanguageTag("de")
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals("de", DataStoreLanguagePreference(dataStore()).languageTag.first())
        }

    @Test
    fun `a choice that cannot be read follows the system, and saving it fails`() =
        runTest {
            val preference = DataStoreLanguagePreference(FailingDataStore())

            assertNull(preference.languageTag.first())
            assertTrue((preference.setLanguageTag("de") as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

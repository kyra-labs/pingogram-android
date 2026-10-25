package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
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

/** [DataStoreCallSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreCallSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `calls use the data the network allows until changed`() =
        runTest {
            assertEquals(CallDataSaving.Never, DataStoreCallSettingsRepository(dataStore()).dataSaving.first())
        }

    @Test
    fun `the choice is saved and survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            val first = DataStoreCallSettingsRepository(dataStore(firstProcess))
            assertEquals(Outcome.Success(Unit), first.setDataSaving(CallDataSaving.OnMobileData))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(CallDataSaving.OnMobileData, DataStoreCallSettingsRepository(dataStore()).dataSaving.first())
        }

    @Test
    fun `an unknown value reads as the default`() =
        runTest {
            val store = dataStore()
            store.edit { it[stringPreferencesKey("calls.data_saving")] = "Sometimes" }

            assertEquals(CallDataSaving.Never, DataStoreCallSettingsRepository(store).dataSaving.first())
        }
}

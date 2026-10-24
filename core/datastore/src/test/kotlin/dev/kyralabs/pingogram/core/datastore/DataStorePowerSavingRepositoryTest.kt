package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.domain.model.PowerSavingSettings
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

/** [DataStorePowerSavingRepository] on a real Preferences DataStore in a temporary file. */
class DataStorePowerSavingRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `follows Battery Saver and turns every part off by default`() =
        runTest {
            assertEquals(PowerSavingSettings(), DataStorePowerSavingRepository(dataStore()).settings.first())
        }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStorePowerSavingRepository(dataStore())

            repository.settings.test {
                awaitItem()

                assertEquals(Outcome.Success(Unit), repository.setMode(PowerSavingMode.Always))
                assertEquals(PowerSavingMode.Always, awaitItem().mode)

                repository.setPart(PowerSavingPart.Autoplay, off = false)
                assertEquals(
                    setOf(PowerSavingPart.AnimatedStickers, PowerSavingPart.CallAnimations),
                    awaitItem().parts,
                )

                repository.setPart(PowerSavingPart.Autoplay, off = true)
                assertEquals(PowerSavingPart.entries.toSet(), awaitItem().parts)
            }
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStorePowerSavingRepository(dataStore(firstProcess)).run {
                setMode(PowerSavingMode.Off)
                setPart(PowerSavingPart.CallAnimations, off = false)
            }
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                PowerSavingSettings(
                    mode = PowerSavingMode.Off,
                    parts = setOf(PowerSavingPart.AnimatedStickers, PowerSavingPart.Autoplay),
                ),
                DataStorePowerSavingRepository(dataStore()).settings.first(),
            )
        }

    @Test
    fun `unknown names read as the defaults`() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit {
                it[stringPreferencesKey("power_saving.mode")] = "Sometimes"
                it[stringSetPreferencesKey("power_saving.kept_on")] = setOf("Holograms")
            }

            assertEquals(PowerSavingSettings(), DataStorePowerSavingRepository(dataStore).settings.first())
        }
}

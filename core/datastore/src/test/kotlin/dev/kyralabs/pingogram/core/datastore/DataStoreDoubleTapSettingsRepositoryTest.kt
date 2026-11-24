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
import dev.kyralabs.pingogram.core.domain.model.DoubleTapAction
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreDoubleTapSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreDoubleTapSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `a double tap reacts on every message until changed`() =
        runTest {
            assertEquals(
                DoubleTapReactions(incoming = DoubleTapAction.React, outgoing = DoubleTapAction.React),
                DataStoreDoubleTapSettingsRepository(dataStore()).doubleTap.first(),
            )
        }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreDoubleTapSettingsRepository(dataStore())

            repository.doubleTap.test {
                assertEquals(DoubleTapReactions(), awaitItem())

                val reply = DoubleTapReactions(outgoing = DoubleTapAction.Reply)
                assertEquals(Outcome.Success(Unit), repository.setDoubleTap(reply))
                assertEquals(reply, awaitItem())

                val nothing = DoubleTapReactions(DoubleTapAction.Nothing, DoubleTapAction.Nothing)
                repository.setDoubleTap(nothing)
                assertEquals(nothing, awaitItem())
            }
        }

    @Test
    fun `the settings survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreDoubleTapSettingsRepository(dataStore(firstProcess))
                .setDoubleTap(DoubleTapReactions(incoming = DoubleTapAction.Reply))
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(
                DoubleTapReactions(incoming = DoubleTapAction.Reply),
                DataStoreDoubleTapSettingsRepository(dataStore()).doubleTap.first(),
            )
        }

    @Test
    fun `the switches saved before there were actions read as react or nothing until the next save`() =
        runTest {
            val store = dataStore()
            store.edit {
                it[booleanPreferencesKey("chat.double_tap.incoming")] = false
                it[booleanPreferencesKey("chat.double_tap.outgoing")] = true
            }
            val repository = DataStoreDoubleTapSettingsRepository(store)
            assertEquals(
                DoubleTapReactions(incoming = DoubleTapAction.Nothing, outgoing = DoubleTapAction.React),
                repository.doubleTap.first(),
            )

            repository.setDoubleTap(DoubleTapReactions(incoming = DoubleTapAction.Reply))

            assertEquals(DoubleTapReactions(incoming = DoubleTapAction.Reply), repository.doubleTap.first())
            assertFalse(booleanPreferencesKey("chat.double_tap.incoming") in store.data.first())
        }

    @Test
    fun `settings that cannot be read count as the defaults, and saving them fails`() =
        runTest {
            val repository = DataStoreDoubleTapSettingsRepository(FailingDataStore())

            assertEquals(DoubleTapReactions(), repository.doubleTap.first())
            val saved = repository.setDoubleTap(DoubleTapReactions(incoming = DoubleTapAction.Nothing))
            assertTrue((saved as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

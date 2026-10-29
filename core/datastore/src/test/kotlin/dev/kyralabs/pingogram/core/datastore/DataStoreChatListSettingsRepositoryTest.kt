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
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
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

/** [DataStoreChatListSettingsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreChatListSettingsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `stories show until hidden`() =
        runTest { assertTrue(DataStoreChatListSettingsRepository(dataStore()).settings.first().showStories) }

    @Test
    fun `emits each change as it is saved`() =
        runTest {
            val repository = DataStoreChatListSettingsRepository(dataStore())

            repository.settings.test {
                assertTrue(awaitItem().showStories)

                assertEquals(Outcome.Success(Unit), repository.setShowStories(false))
                assertFalse(awaitItem().showStories)

                repository.setShowStories(true)
                assertTrue(awaitItem().showStories)
            }
        }

    @Test
    fun `the setting survives the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreChatListSettingsRepository(dataStore(firstProcess)).setShowStories(false)
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertFalse(DataStoreChatListSettingsRepository(dataStore()).settings.first().showStories)
        }

    @Test
    fun `settings that cannot be read show stories, and saving them fails`() =
        runTest {
            val repository = DataStoreChatListSettingsRepository(FailingDataStore())

            assertTrue(repository.settings.first().showStories)
            val saved = repository.setShowStories(false)
            assertTrue((saved as Outcome.Failure).error is DomainError.Unknown)
        }

    @Test
    fun `swipes read and archive until changed, and a change saves both`() =
        runTest {
            val repository = DataStoreChatListSettingsRepository(dataStore())
            assertEquals(ChatListSettings(), repository.settings.first())

            assertEquals(Outcome.Success(Unit), repository.setSwipeActions(SwipeAction.Pin, SwipeAction.None))

            assertEquals(
                ChatListSettings(startSwipe = SwipeAction.Pin, endSwipe = SwipeAction.None),
                repository.settings.first(),
            )
        }

    @Test
    fun `swiping changes the folder only once turned on, keeping the swipes`() =
        runTest {
            val repository = DataStoreChatListSettingsRepository(dataStore())
            repository.setSwipeActions(SwipeAction.Pin, SwipeAction.Mute)

            assertEquals(Outcome.Success(Unit), repository.setSwipeChangesFolder(true))

            assertEquals(
                ChatListSettings(startSwipe = SwipeAction.Pin, endSwipe = SwipeAction.Mute, swipeChangesFolder = true),
                repository.settings.first(),
            )
        }

    @Test
    fun `an action this version does not know reads as the default`() =
        runTest {
            val store = dataStore()
            store.edit { it[stringPreferencesKey("chat_list.swipe_start")] = "Teleport" }

            assertEquals(SwipeAction.Read, DataStoreChatListSettingsRepository(store).settings.first().startSwipe)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

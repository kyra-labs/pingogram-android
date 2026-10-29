package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreChatsShownFirstRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreChatsShownFirstRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `none until chosen`() =
        runTest {
            assertTrue(DataStoreChatsShownFirstRepository(dataStore()).chatIds.first().isEmpty())
        }

    @Test
    fun `keeps the chats in their order, each once, five at most`() =
        runTest {
            val repository = DataStoreChatsShownFirstRepository(dataStore())

            assertEquals(Outcome.Success(Unit), repository.setChatIds(listOf(3, -100, 3, 7, 1, 9, 12)))

            assertEquals(listOf(3L, -100L, 7L, 1L, 9L), repository.chatIds.first())
        }

    @Test
    fun `an empty list removes them, and what is not an id is skipped`() =
        runTest {
            val store = dataStore()
            val repository = DataStoreChatsShownFirstRepository(store)
            repository.setChatIds(listOf(3))
            repository.setChatIds(emptyList())
            assertTrue(repository.chatIds.first().isEmpty())

            store.edit { it[stringPreferencesKey("share.chats_shown_first")] = "4,x,,5" }
            assertEquals(listOf(4L, 5L), repository.chatIds.first())
        }
}

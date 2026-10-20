package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.MAX_RECENT_EMOJI
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

/** [DataStoreRecentEmojiRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreRecentEmojiRepositoryTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `is empty at first`() =
        runTest {
            assertEquals(emptyList<String>(), DataStoreRecentEmojiRepository(dataStore()).recentEmoji.first())
        }

    @Test
    fun `the latest emoji comes first, and one picked again moves to the front`() =
        runTest {
            val repository = DataStoreRecentEmojiRepository(dataStore())

            repository.addRecent("😀")
            repository.addRecent("👩‍👩‍👧")
            repository.addRecent("😀")

            assertEquals(listOf("😀", "👩‍👩‍👧"), repository.recentEmoji.first())
        }

    @Test
    fun `keeps at most the most recent ones`() =
        runTest {
            val repository = DataStoreRecentEmojiRepository(dataStore())

            (0..MAX_RECENT_EMOJI).forEach { repository.addRecent("e$it") }

            val recent = repository.recentEmoji.first()
            assertEquals(MAX_RECENT_EMOJI, recent.size)
            assertEquals("e$MAX_RECENT_EMOJI", recent.first())
            assertEquals("e1", recent.last())
        }

    @Test
    fun `the recent emoji survive the app process`() =
        runTest {
            val firstProcess = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
            DataStoreRecentEmojiRepository(dataStore(firstProcess)).addRecent("🎉")
            firstProcess.coroutineContext[Job]?.cancelAndJoin()

            assertEquals(listOf("🎉"), DataStoreRecentEmojiRepository(dataStore()).recentEmoji.first())
        }

    @Test
    fun `a file that cannot be read has no recent emoji, and saving fails`() =
        runTest {
            val repository = DataStoreRecentEmojiRepository(FailingDataStore())

            assertEquals(emptyList<String>(), repository.recentEmoji.first())
            assertTrue((repository.addRecent("😀") as Outcome.Failure).error is DomainError.Unknown)
        }

    /** A settings file that can be neither read nor written, as on a failing disk. */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("Disk unavailable") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("Disk full: ${transform(emptyPreferences()).asMap().size} keys not written")
    }
}

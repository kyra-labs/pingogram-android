package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreKeywordAlertsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreKeywordAlertsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.repository() =
        DataStoreKeywordAlertsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) }),
        )

    @Test
    fun `the defaults until set`() =
        runTest {
            assertEquals(KeywordAlertSettings(), repository().settings.first())
        }

    @Test
    fun `keeps the words in order, once each, trimmed, at most twenty`() =
        runTest {
            val repository = repository()

            repository.addWord(" launch ")
            repository.addWord("Pingogram, Pro")
            repository.addWord("LAUNCH")
            repository.addWord("Láunch")
            repository.addWord("   ")
            repository.addWord("two\nlines")

            assertEquals(listOf("launch", "Pingogram, Pro", "two lines"), repository.settings.first().words)

            repeat(30) { repository.addWord("word$it") }
            assertEquals(
                KeywordAlertSettings.MAX_WORDS,
                repository.settings
                    .first()
                    .words.size,
            )
        }

    @Test
    fun `removes a word`() =
        runTest {
            val repository = repository()
            repository.addWord("launch")
            repository.addWord("0.9.4")

            repository.removeWord("launch")

            assertEquals(listOf("0.9.4"), repository.settings.first().words)
        }

    @Test
    fun `keeps where to look and the switches`() =
        runTest {
            val first = repository()
            first.setWhere(KeywordScope.Folders(persistentSetOf(3, 1)))
            first.setAlertWhenMuted(false)
            first.setShowInChatList(true)

            val settings = first.settings.first()

            assertEquals(KeywordScope.Folders(persistentSetOf(1, 3)), settings.where)
            assertFalse(settings.alertWhenMuted)
            assertTrue(settings.showInChatList)

            first.setWhere(KeywordScope.MutedChats)
            assertEquals(KeywordScope.MutedChats, first.settings.first().where)
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}

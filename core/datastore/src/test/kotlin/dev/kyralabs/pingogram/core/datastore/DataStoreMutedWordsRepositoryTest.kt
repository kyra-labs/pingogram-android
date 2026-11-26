package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreMutedWordsRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreMutedWordsRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.repository() =
        DataStoreMutedWordsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(directory, FILE) }),
        )

    @Test
    fun `off and without words until set`() =
        runTest {
            assertEquals(MutedWordSettings(), repository().settings.first())
        }

    @Test
    fun `keeps the words in order, once each, trimmed and cut, while off too`() =
        runTest {
            val repository = repository()

            repository.addWord(" spoiler ")
            repository.addWord("SPOILER")
            repository.addWord("Spóiler")
            repository.addWord("   ")
            repository.addWord("two\nlines")
            repository.addWord("x".repeat(80))

            val settings = repository.settings.first()
            assertEquals(listOf("spoiler", "two lines", "x".repeat(64)), settings.words)
            assertEquals(false, settings.enabled)
        }

    @Test
    fun `at most a hundred words`() =
        runTest {
            val repository = repository()

            repeat(110) { repository.addWord("word$it") }

            assertEquals(
                MutedWordSettings.MAX_WORDS,
                repository.settings
                    .first()
                    .words.size,
            )
        }

    @Test
    fun `turns on and off, and removes a word`() =
        runTest {
            val repository = repository()
            repository.addWord("spoiler")
            repository.addWord("crypto")

            repository.setEnabled(true)
            repository.removeWord("spoiler")

            val settings = repository.settings.first()
            assertTrue(settings.enabled)
            assertEquals(listOf("crypto"), settings.words)
        }

    private companion object {
        const val FILE = "settings.preferences_pb"
    }
}

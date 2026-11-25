package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MutedWordsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): the switch, and the words
 * one per line in their order (a line break in a word is saved as a space). A file that cannot be read and missing keys
 * read as the defaults ([MutedWordSettings]).
 */
@Singleton
class DataStoreMutedWordsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : MutedWordsRepository {
        override val settings: Flow<MutedWordSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    MutedWordSettings(
                        enabled = preferences[ENABLED] ?: MutedWordSettings().enabled,
                        words = preferences.words().toImmutableList(),
                    )
                }.distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = save { it[ENABLED] = enabled }

        override suspend fun addWord(word: String): Outcome<Unit> =
            save { preferences ->
                val trimmed = word.replace(LINE_BREAK, " ").trim().take(MutedWordSettings.MAX_WORD_LENGTH)
                val words = preferences.words()
                val known = words.any { KeywordMatcher.keyOf(it) == KeywordMatcher.keyOf(trimmed) }
                if (trimmed.isNotEmpty() && !known && words.size < MutedWordSettings.MAX_WORDS) {
                    preferences.setWords(words + trimmed)
                }
            }

        override suspend fun removeWord(word: String): Outcome<Unit> =
            save { preferences -> preferences.setWords(preferences.words() - word) }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

private fun Preferences.words(): List<String> = this[WORDS].orEmpty().split('\n').filter { it.isNotBlank() }

private fun MutablePreferences.setWords(words: List<String>) {
    this[WORDS] = words.joinToString("\n")
}

private val LINE_BREAK = Regex("[\\r\\n]+")

// The keys in the settings file; renaming one loses what users saved under it.
private val ENABLED = booleanPreferencesKey("muted_words.enabled")
private val WORDS = stringPreferencesKey("muted_words.words")

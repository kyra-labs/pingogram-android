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
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [KeywordAlertsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): the words one per
 * line in their order (a line break in a word is saved as a space), where to look as its name and the folders' ids. A
 * file that cannot be read, missing keys and values it cannot parse read as the defaults ([KeywordAlertSettings]).
 */
@Singleton
class DataStoreKeywordAlertsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : KeywordAlertsRepository {
        override val settings: Flow<KeywordAlertSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = KeywordAlertSettings()
                    KeywordAlertSettings(
                        words = preferences.words().toImmutableList(),
                        where = preferences.where(),
                        alertWhenMuted = preferences[ALERT_WHEN_MUTED] ?: defaults.alertWhenMuted,
                        showInChatList = preferences[SHOW_IN_CHAT_LIST] ?: defaults.showInChatList,
                    )
                }.distinctUntilChanged()

        override suspend fun addWord(word: String): Outcome<Unit> =
            save { preferences ->
                val trimmed = word.replace(LINE_BREAK, " ").trim().take(KeywordAlertSettings.MAX_WORD_LENGTH)
                val words = preferences.words()
                val known = words.any { KeywordMatcher.keyOf(it) == KeywordMatcher.keyOf(trimmed) }
                if (trimmed.isNotEmpty() && !known && words.size < KeywordAlertSettings.MAX_WORDS) {
                    preferences.setWords(words + trimmed)
                }
            }

        override suspend fun removeWord(word: String): Outcome<Unit> =
            save { preferences -> preferences.setWords(preferences.words() - word) }

        override suspend fun setWhere(where: KeywordScope): Outcome<Unit> =
            save { preferences ->
                preferences[WHERE] =
                    when (where) {
                        KeywordScope.AllChats -> WHERE_ALL
                        KeywordScope.MutedChats -> WHERE_MUTED
                        is KeywordScope.Folders -> WHERE_FOLDERS
                    }
                preferences[FOLDERS] =
                    (where as? KeywordScope.Folders)
                        ?.folderIds
                        ?.sorted()
                        ?.joinToString(",")
                        .orEmpty()
            }

        override suspend fun setAlertWhenMuted(alert: Boolean): Outcome<Unit> = save { it[ALERT_WHEN_MUTED] = alert }

        override suspend fun setShowInChatList(show: Boolean): Outcome<Unit> = save { it[SHOW_IN_CHAT_LIST] = show }

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

private fun Preferences.where(): KeywordScope =
    when (this[WHERE]) {
        WHERE_MUTED -> {
            KeywordScope.MutedChats
        }

        WHERE_FOLDERS -> {
            val ids = this[FOLDERS].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
            KeywordScope.Folders(ids.toImmutableSet())
        }

        else -> {
            KeywordScope.AllChats
        }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val WORDS = stringPreferencesKey("keyword_alerts.words")
private val WHERE = stringPreferencesKey("keyword_alerts.where")
private val FOLDERS = stringPreferencesKey("keyword_alerts.folders")
private val ALERT_WHEN_MUTED = booleanPreferencesKey("keyword_alerts.alert_when_muted")
private val SHOW_IN_CHAT_LIST = booleanPreferencesKey("keyword_alerts.show_in_chat_list")
private const val WHERE_ALL = "all"
private const val WHERE_MUTED = "muted"
private const val WHERE_FOLDERS = "folders"

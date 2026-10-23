package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.MAX_RECENT_EMOJI
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecentEmojiRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): the emoji in one
 * string, separated by spaces, which no emoji sequence contains. A file that cannot be read reads as no recent emoji.
 */
@Singleton
class DataStoreRecentEmojiRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : RecentEmojiRepository {
        override val recentEmoji: Flow<List<String>> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[RECENT_EMOJI].toEmojiList() }
                .distinctUntilChanged()

        override suspend fun addRecent(emoji: String): Outcome<Unit> {
            if (emoji.isBlank() || emoji.any(Char::isWhitespace)) return Outcome.Success(Unit)
            return try {
                dataStore.edit { preferences ->
                    val recent = listOf(emoji) + (preferences[RECENT_EMOJI].toEmojiList() - emoji)
                    preferences[RECENT_EMOJI] = recent.take(MAX_RECENT_EMOJI).joinToString(SEPARATOR)
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Recent emoji not saved: ${error.message}"))
            }
        }

        private fun String?.toEmojiList(): List<String> =
            this
                ?.split(SEPARATOR)
                ?.filter { it.isNotEmpty() }
                ?.take(MAX_RECENT_EMOJI)
                .orEmpty()
    }

private const val SEPARATOR = " "

// The key in the settings file; renaming it loses what users saved under it.
private val RECENT_EMOJI = stringPreferencesKey("composer.recent_emoji")

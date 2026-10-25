package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatsShownFirstRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatsShownFirstRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), as decimal ids
 * joined by commas, in their order (a string set would lose it). A file that cannot be read reads as none; an id that
 * is not a number is skipped.
 */
@Singleton
class DataStoreChatsShownFirstRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ChatsShownFirstRepository {
        override val chatIds: Flow<ImmutableList<Long>> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    preferences[CHATS_SHOWN_FIRST]
                        .orEmpty()
                        .split(SEPARATOR)
                        .mapNotNull { it.toLongOrNull() }
                        .distinct()
                        .take(ChatsShownFirstRepository.MAX)
                        .toImmutableList()
                }.distinctUntilChanged()

        override suspend fun setChatIds(chatIds: List<Long>): Outcome<Unit> =
            try {
                val kept = chatIds.distinct().take(ChatsShownFirstRepository.MAX)
                dataStore.edit { preferences ->
                    if (kept.isEmpty()) {
                        preferences.remove(CHATS_SHOWN_FIRST)
                    } else {
                        preferences[CHATS_SHOWN_FIRST] = kept.joinToString(SEPARATOR)
                    }
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val CHATS_SHOWN_FIRST = stringPreferencesKey("share.chats_shown_first")
private const val SEPARATOR = ","

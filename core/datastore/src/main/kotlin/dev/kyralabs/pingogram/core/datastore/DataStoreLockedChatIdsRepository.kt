package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LockedChatIdsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), as a set of decimal
 * ids. A file that cannot be read reads as no locked chats; an id that is not a number is skipped.
 */
@Singleton
class DataStoreLockedChatIdsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : LockedChatIdsRepository {
        override val ids: Flow<Set<Long>> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    preferences[LOCKED_CHATS].orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }
                }.distinctUntilChanged()

        override suspend fun setIds(ids: Set<Long>): Outcome<Unit> =
            try {
                dataStore.edit { preferences ->
                    if (ids.isEmpty()) {
                        preferences.remove(LOCKED_CHATS)
                    } else {
                        preferences[LOCKED_CHATS] = ids.mapTo(HashSet()) { it.toString() }
                    }
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val LOCKED_CHATS = stringSetPreferencesKey("privacy.locked_chats")

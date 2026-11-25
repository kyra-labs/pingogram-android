package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotesAndNamesRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and a missing key read as on.
 */
@Singleton
class DataStoreNotesAndNamesRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : NotesAndNamesRepository {
        override val enabled: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[NOTES_AND_NAMES] ?: true }
                .distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[NOTES_AND_NAMES] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val NOTES_AND_NAMES = booleanPreferencesKey("chats.notes_and_names")

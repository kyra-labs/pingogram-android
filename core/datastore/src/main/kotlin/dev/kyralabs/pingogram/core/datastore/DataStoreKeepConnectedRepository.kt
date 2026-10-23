package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [KeepConnectedRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot be
 * read and a missing key read as off, the default: the service never runs unless the user turned it on.
 */
@Singleton
class DataStoreKeepConnectedRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : KeepConnectedRepository {
        override val keepConnected: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[KEEP_CONNECTED] ?: false }
                .distinctUntilChanged()

        override suspend fun setKeepConnected(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[KEEP_CONNECTED] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val KEEP_CONNECTED = booleanPreferencesKey("notifications.keep_connected")

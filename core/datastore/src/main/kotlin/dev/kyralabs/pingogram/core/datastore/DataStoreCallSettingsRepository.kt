package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.core.domain.repository.CallSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CallSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), the choice stored by
 * name. A file that cannot be read, a missing key and an unknown name read as [CallDataSaving.Never].
 */
@Singleton
class DataStoreCallSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : CallSettingsRepository {
        override val dataSaving: Flow<CallDataSaving> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    CallDataSaving.entries.firstOrNull { it.name == preferences[DATA_SAVING] } ?: CallDataSaving.Never
                }.distinctUntilChanged()

        override suspend fun setDataSaving(saving: CallDataSaving): Outcome<Unit> =
            try {
                dataStore.edit { it[DATA_SAVING] = saving.name }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val DATA_SAVING = stringPreferencesKey("calls.data_saving")

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CleanupSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read, missing keys and a number of months that is not a choice read as the defaults.
 */
@Singleton
class DataStoreCleanupSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : CleanupSettingsRepository {
        override val settings: Flow<CleanupSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = CleanupSettings()
                    CleanupSettings(
                        enabled = preferences[ENABLED] ?: defaults.enabled,
                        months = preferences[MONTHS]?.takeIf { it in CleanupSettings.MONTHS } ?: defaults.months,
                    )
                }.distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = save { it[ENABLED] = enabled }

        override suspend fun setMonths(months: Int): Outcome<Unit> =
            save { if (months in CleanupSettings.MONTHS) it[MONTHS] = months }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val ENABLED = booleanPreferencesKey("cleanup.enabled")
private val MONTHS = intPreferencesKey("cleanup.months")

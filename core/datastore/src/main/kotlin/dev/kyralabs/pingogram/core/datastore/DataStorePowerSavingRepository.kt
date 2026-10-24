package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PowerSavingMode
import dev.kyralabs.pingogram.core.domain.model.PowerSavingPart
import dev.kyralabs.pingogram.core.domain.model.PowerSavingSettings
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PowerSavingRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). The mode is stored by
 * name, and the parts the user keeps on (not turned off), so a part added later is turned off by power saving, as
 * every part is by default. A file that cannot be read and unknown names read as the defaults.
 */
@Singleton
class DataStorePowerSavingRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : PowerSavingRepository {
        override val settings: Flow<PowerSavingSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = PowerSavingSettings()
                    val kept = preferences[KEPT_ON].orEmpty()
                    PowerSavingSettings(
                        mode = PowerSavingMode.entries.find { it.name == preferences[MODE] } ?: defaults.mode,
                        parts = PowerSavingPart.entries.filterTo(mutableSetOf()) { it.name !in kept },
                    )
                }.distinctUntilChanged()

        override suspend fun setMode(mode: PowerSavingMode): Outcome<Unit> = write { it[MODE] = mode.name }

        override suspend fun setPart(
            part: PowerSavingPart,
            off: Boolean,
        ): Outcome<Unit> =
            write { preferences ->
                val kept = preferences[KEPT_ON].orEmpty()
                preferences[KEPT_ON] = if (off) kept - part.name else kept + part.name
            }

        private suspend fun write(change: (MutablePreferences) -> Unit) =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming them loses what users saved under them.
private val MODE = stringPreferencesKey("power_saving.mode")
private val KEPT_ON = stringSetPreferencesKey("power_saving.kept_on")

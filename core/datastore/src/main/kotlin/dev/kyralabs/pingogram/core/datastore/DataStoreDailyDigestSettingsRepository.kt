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
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DailyDigestSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), the time as its
 * minute of the day. A file that cannot be read, missing keys and a minute outside the day read as the defaults.
 */
@Singleton
class DataStoreDailyDigestSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : DailyDigestSettingsRepository {
        override val settings: Flow<DailyDigestSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = DailyDigestSettings()
                    DailyDigestSettings(
                        enabled = preferences[ENABLED] ?: defaults.enabled,
                        time =
                            preferences[MINUTE]
                                ?.takeIf { it in 0 until MINUTES_PER_DAY }
                                ?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
                                ?: defaults.time,
                    )
                }.distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = save { it[ENABLED] = enabled }

        override suspend fun setTime(time: LocalTime): Outcome<Unit> =
            save { it[MINUTE] = time.hour * MINUTES_PER_HOUR + time.minute }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val ENABLED = booleanPreferencesKey("daily_digest.enabled")
private val MINUTE = intPreferencesKey("daily_digest.minute")

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

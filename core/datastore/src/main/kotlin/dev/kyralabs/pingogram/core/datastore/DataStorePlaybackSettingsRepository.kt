package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PlaybackSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read and missing keys read as the defaults ([RaiseToListenSettings]).
 */
@Singleton
class DataStorePlaybackSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : PlaybackSettingsRepository {
        override val raiseToListen: Flow<RaiseToListenSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = RaiseToListenSettings()
                    RaiseToListenSettings(
                        enabled = preferences[RAISE_TO_LISTEN] ?: defaults.enabled,
                        pauseWhenLowered = preferences[PAUSE_WHEN_LOWERED] ?: defaults.pauseWhenLowered,
                    )
                }.distinctUntilChanged()

        override suspend fun setRaiseToListen(enabled: Boolean): Outcome<Unit> = save(RAISE_TO_LISTEN, enabled)

        override suspend fun setPauseWhenLowered(enabled: Boolean): Outcome<Unit> = save(PAUSE_WHEN_LOWERED, enabled)

        private suspend fun save(
            key: Preferences.Key<Boolean>,
            value: Boolean,
        ): Outcome<Unit> =
            try {
                dataStore.edit { it[key] = value }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val RAISE_TO_LISTEN = booleanPreferencesKey("playback.raise_to_listen")
private val PAUSE_WHEN_LOWERED = booleanPreferencesKey("playback.pause_when_lowered")

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import dev.kyralabs.pingogram.core.domain.repository.TranscriptionSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TranscriptionSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and missing keys read as the defaults ([TranscriptionSettings]).
 */
@Singleton
class DataStoreTranscriptionSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : TranscriptionSettingsRepository {
        override val settings: Flow<TranscriptionSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = TranscriptionSettings()
                    TranscriptionSettings(
                        automatic = preferences[AUTOMATIC] ?: defaults.automatic,
                        noticeShown = preferences[NOTICE_SHOWN] ?: defaults.noticeShown,
                    )
                }.distinctUntilChanged()

        override suspend fun setAutomatic(enabled: Boolean): Outcome<Unit> = save(AUTOMATIC, enabled)

        override suspend fun markNoticeShown(): Outcome<Unit> = save(NOTICE_SHOWN, true)

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
private val AUTOMATIC = booleanPreferencesKey("transcription.automatic")
private val NOTICE_SHOWN = booleanPreferencesKey("transcription.notice_shown")

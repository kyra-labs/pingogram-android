package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ContactSyncSettings
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ContactSyncSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and missing keys read as the defaults: sync is off unless the user turned it on. Nothing of the
 * phone's contacts is stored, only the switch, the time of the last sync and the dismissed offer.
 */
@Singleton
class DataStoreContactSyncSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ContactSyncSettingsRepository {
        override val settings: Flow<ContactSyncSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    ContactSyncSettings(
                        enabled = preferences[ENABLED] ?: false,
                        lastSyncedAt = preferences[LAST_SYNCED]?.let(Instant::ofEpochSecond),
                        promptDismissed = preferences[PROMPT_DISMISSED] ?: false,
                    )
                }.distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = write { it[ENABLED] = enabled }

        override suspend fun setLastSyncedAt(time: Instant?): Outcome<Unit> =
            write { preferences ->
                if (time == null) preferences.remove(LAST_SYNCED) else preferences[LAST_SYNCED] = time.epochSecond
            }

        override suspend fun setPromptDismissed(dismissed: Boolean): Outcome<Unit> =
            write { it[PROMPT_DISMISSED] = dismissed }

        override suspend fun clear(): Outcome<Unit> =
            write { preferences ->
                preferences.remove(ENABLED)
                preferences.remove(LAST_SYNCED)
                preferences.remove(PROMPT_DISMISSED)
            }

        private suspend fun write(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val ENABLED = booleanPreferencesKey("contacts.sync_enabled")
private val LAST_SYNCED = longPreferencesKey("contacts.sync_last_synced")
private val PROMPT_DISMISSED = booleanPreferencesKey("contacts.sync_prompt_dismissed")

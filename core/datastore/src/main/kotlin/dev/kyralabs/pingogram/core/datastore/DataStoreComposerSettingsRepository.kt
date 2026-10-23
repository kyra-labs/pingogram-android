package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ComposerSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read and a missing key read as off, the default: Enter sends.
 */
@Singleton
class DataStoreComposerSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ComposerSettingsRepository {
        override val sendWithCtrlEnter: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[SEND_WITH_CTRL_ENTER] ?: false }
                .distinctUntilChanged()

        override suspend fun setSendWithCtrlEnter(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[SEND_WITH_CTRL_ENTER] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val SEND_WITH_CTRL_ENTER = booleanPreferencesKey("composer.send_with_ctrl_enter")

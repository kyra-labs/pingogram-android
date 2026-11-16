package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AiEditorConsentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AiEditorConsentRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read reads as not acknowledged, so the worst case is the explanation shown once more.
 */
@Singleton
class DataStoreAiEditorConsentRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : AiEditorConsentRepository {
        override val acknowledged: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[ACKNOWLEDGED] == true }
                .distinctUntilChanged()

        override suspend fun acknowledge(): Outcome<Unit> =
            try {
                dataStore.edit { it[ACKNOWLEDGED] = true }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key of the settings file; renaming it shows the explanation again.
private val ACKNOWLEDGED = booleanPreferencesKey("ai_editor.acknowledged")

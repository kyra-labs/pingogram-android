package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The language chosen for the app on Android 8–12, which keep no per-app language of their own, in the app's
 * settings ([SettingsDataStore]): an IETF BCP 47 tag, or null for the system's language. `:app`'s
 * `PlatformAppLanguageRepository` reads it there and Android's per-app language from Android 13 on. A file that
 * cannot be read and a missing key read as null: the app then follows the system.
 */
@Singleton
class DataStoreLanguagePreference
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) {
        /** The chosen language, or null for the system's, then every change. Never fails. */
        val languageTag: Flow<String?> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[LANGUAGE] }
                .distinctUntilChanged()

        /** Saves [languageTag], or the system's language with null; fails if it cannot be written (a full disk). */
        suspend fun setLanguageTag(languageTag: String?): Outcome<Unit> =
            try {
                dataStore.edit { if (languageTag != null) it[LANGUAGE] = languageTag else it.remove(LANGUAGE) }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val LANGUAGE = stringPreferencesKey("language.app_language")

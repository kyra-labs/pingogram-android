package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StickerSettings
import dev.kyralabs.pingogram.core.domain.repository.StickerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSuggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StickerSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). The suggestions are
 * stored by name; a file that cannot be read, a missing key and an unknown name read as the defaults.
 */
@Singleton
class DataStoreStickerSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : StickerSettingsRepository {
        override val settings: Flow<StickerSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = StickerSettings()
                    StickerSettings(
                        suggestions =
                            StickerSuggestions.entries.firstOrNull { it.name == preferences[SUGGESTIONS] }
                                ?: defaults.suggestions,
                        loop = preferences[LOOP] ?: defaults.loop,
                    )
                }.distinctUntilChanged()

        override suspend fun setSuggestions(suggestions: StickerSuggestions): Outcome<Unit> =
            save { it[SUGGESTIONS] = suggestions.name }

        override suspend fun setLoop(loop: Boolean): Outcome<Unit> = save { it[LOOP] = loop }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val SUGGESTIONS = stringPreferencesKey("stickers.suggestions")
private val LOOP = booleanPreferencesKey("stickers.loop")

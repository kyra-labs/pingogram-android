package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.domain.repository.ViewerHintsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ViewerHintsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot be
 * read reads as the hint not shown, and one that cannot be written leaves it to show once more: at worst, a hint twice.
 */
@Singleton
class DataStoreViewerHintsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ViewerHintsRepository {
        override val controlsHintShown: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[CONTROLS_HINT_SHOWN] ?: false }
                .distinctUntilChanged()

        override suspend fun setControlsHintShown() {
            try {
                dataStore.edit { it[CONTROLS_HINT_SHOWN] = true }
            } catch (_: IOException) {
                // It shows once more next time.
            }
        }
    }

// A key of the settings file; renaming it shows the hint again.
private val CONTROLS_HINT_SHOWN = booleanPreferencesKey("media_viewer.controls_hint_shown")

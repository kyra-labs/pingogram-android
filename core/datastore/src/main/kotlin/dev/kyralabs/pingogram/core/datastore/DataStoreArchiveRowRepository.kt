package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ArchiveRowRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]), the style stored by its
 * name. A file that cannot be read, a missing key and a name this version does not know read as
 * [ArchiveRowStyle.Full], the default.
 */
@Singleton
class DataStoreArchiveRowRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ArchiveRowRepository {
        override val style: Flow<ArchiveRowStyle> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val name = preferences[ARCHIVE_ROW_STYLE]
                    ArchiveRowStyle.entries.firstOrNull { it.name == name } ?: ArchiveRowStyle.Full
                }.distinctUntilChanged()

        override suspend fun setStyle(style: ArchiveRowStyle): Outcome<Unit> =
            try {
                dataStore.edit { it[ARCHIVE_ROW_STYLE] = style.name }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val ARCHIVE_ROW_STYLE = stringPreferencesKey("chat_list.archive_row")

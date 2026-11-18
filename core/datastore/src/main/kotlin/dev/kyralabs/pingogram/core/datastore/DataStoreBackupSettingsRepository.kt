package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupSettingsRepository] in the app's settings file ([SettingsDataStore]), under keys that never go into a backup
 * ([LeftOutSettings]): a folder's grant is this phone's, and so is what the user hid.
 */
@Singleton
class DataStoreBackupSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : BackupSettingsRepository {
        private val data: Flow<Preferences> =
            dataStore.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

        override val folderUri: Flow<String?> = data.map { it[FOLDER] }.distinctUntilChanged()

        override suspend fun setFolderUri(treeUri: String?): Outcome<Unit> =
            save { if (treeUri == null) it.remove(FOLDER) else it[FOLDER] = treeUri }

        override val restoreDismissedFor: Flow<String?> = data.map { it[DISMISSED_FOR] }.distinctUntilChanged()

        override suspend fun dismissRestore(document: String): Outcome<Unit> = save { it[DISMISSED_FOR] = document }

        override val restorePromptPending: Flow<Boolean> =
            data.map { it[PROMPT_PENDING] ?: false }.distinctUntilChanged()

        override fun offerRestorePrompt() {
            scope.launch { save { it[PROMPT_PENDING] = true } }
        }

        override suspend fun dismissRestorePrompt(): Outcome<Unit> = save { it.remove(PROMPT_PENDING) }

        override val frequency: Flow<BackupFrequency?> =
            data
                .map { preferences -> BackupFrequency.entries.firstOrNull { it.name == preferences[FREQUENCY] } }
                .distinctUntilChanged()

        override suspend fun setFrequency(frequency: BackupFrequency): Outcome<Unit> =
            save { it[FREQUENCY] = frequency.name }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Backup settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming one loses what users saved under it.
private val FOLDER = stringPreferencesKey("backup.folder")
private val DISMISSED_FOR = stringPreferencesKey("backup.restore_dismissed_for")
private val PROMPT_PENDING = booleanPreferencesKey("backup.restore_prompt_pending")

/** By its name, so one this version does not know reads as none chosen. */
private val FREQUENCY = stringPreferencesKey("backup.frequency")

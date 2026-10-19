package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotificationPromptRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and a missing key read as no answer, so the worst case is one more request.
 */
@Singleton
class DataStoreNotificationPromptRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : NotificationPromptRepository {
        override val prompt: Flow<NotificationPrompt> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { NotificationPrompt(isDismissed = it[DISMISSED] ?: false, wasRequested = it[REQUESTED] ?: false) }
                .distinctUntilChanged()

        override suspend fun dismiss() = edit { it[DISMISSED] = true }

        override suspend fun setRequested() = edit { it[REQUESTED] = true }

        private suspend fun edit(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// Keys of the settings file; renaming one loses what users saved under it.
private val DISMISSED = booleanPreferencesKey("notifications.prompt_dismissed")
private val REQUESTED = booleanPreferencesKey("notifications.permission_requested")

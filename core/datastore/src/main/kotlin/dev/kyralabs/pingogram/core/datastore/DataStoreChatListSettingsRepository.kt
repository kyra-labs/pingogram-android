package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatListSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read and a missing key read as the defaults.
 */
@Singleton
class DataStoreChatListSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ChatListSettingsRepository {
        override val settings: Flow<ChatListSettings> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { ChatListSettings(showStories = it[SHOW_STORIES] ?: true) }
                .distinctUntilChanged()

        override suspend fun setShowStories(show: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[SHOW_STORIES] = show }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val SHOW_STORIES = booleanPreferencesKey("chat_list.show_stories")

package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LinkPreviewSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and a missing key read as off.
 */
@Singleton
class DataStoreLinkPreviewSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : LinkPreviewSettingsRepository {
        override val secretChatPreviews: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[SECRET_CHAT_PREVIEWS] ?: false }
                .distinctUntilChanged()

        override suspend fun setSecretChatPreviews(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[SECRET_CHAT_PREVIEWS] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key in the settings file; renaming it loses what users saved under it.
private val SECRET_CHAT_PREVIEWS = booleanPreferencesKey("privacy.secret_chat_link_previews")

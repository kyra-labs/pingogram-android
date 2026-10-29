package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ComposerSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that cannot
 * be read and missing keys read as the defaults. "Send with Ctrl+Enter", the setting before (2026-10-23), still counts
 * until the first save: on, it means a hardware keyboard's Enter does not send. Photos go compressed by default, and
 * the record button records voice messages.
 */
@Singleton
class DataStoreComposerSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : ComposerSettingsRepository {
        override val sendKeys: Flow<SendKeys> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    SendKeys(
                        hardwareKeyboard =
                            preferences[ENTER_SENDS_HARDWARE] ?: (preferences[SEND_WITH_CTRL_ENTER] != true),
                        onScreenKeyboard = preferences[ENTER_SENDS_ON_SCREEN] ?: false,
                    )
                }.distinctUntilChanged()

        override suspend fun setSendKeys(keys: SendKeys): Outcome<Unit> =
            try {
                dataStore.edit {
                    it[ENTER_SENDS_HARDWARE] = keys.hardwareKeyboard
                    it[ENTER_SENDS_ON_SCREEN] = keys.onScreenKeyboard
                    it.remove(SEND_WITH_CTRL_ENTER)
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }

        override val sendPhotosInOriginalQuality: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[SEND_PHOTOS_ORIGINAL] ?: false }
                .distinctUntilChanged()

        override suspend fun setSendPhotosInOriginalQuality(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[SEND_PHOTOS_ORIGINAL] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }

        override val recordsVideoMessages: Flow<Boolean> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[RECORDS_VIDEO_MESSAGES] ?: false }
                .distinctUntilChanged()

        override suspend fun setRecordsVideoMessages(enabled: Boolean): Outcome<Unit> =
            try {
                dataStore.edit { it[RECORDS_VIDEO_MESSAGES] = enabled }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming them loses what users saved under them.
private val ENTER_SENDS_HARDWARE = booleanPreferencesKey("composer.enter_sends_hardware")
private val ENTER_SENDS_ON_SCREEN = booleanPreferencesKey("composer.enter_sends_on_screen")

/** The setting before [ENTER_SENDS_HARDWARE], read until the first save, which removes it. */
private val SEND_WITH_CTRL_ENTER = booleanPreferencesKey("composer.send_with_ctrl_enter")
private val SEND_PHOTOS_ORIGINAL = booleanPreferencesKey("composer.send_photos_original")
private val RECORDS_VIDEO_MESSAGES = booleanPreferencesKey("composer.records_video_messages")

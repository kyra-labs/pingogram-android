package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AutoDownloadRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): six keys per network,
 * each missing one, and every key of a file that cannot be read, reading as that network's default. A size limit that
 * is not positive reads as the default too.
 */
@Singleton
class DataStoreAutoDownloadRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : AutoDownloadRepository {
        override val settings: Flow<AutoDownloadSettings> =
            dataStore.data
                // Unreadable settings must not stop conversations from showing media.
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it.toAutoDownloadSettings() }
                .distinctUntilChanged()

        override suspend fun setRules(
            network: AutoDownloadNetwork,
            rules: AutoDownloadRules,
        ): Outcome<Unit> =
            edit { preferences ->
                val keys = RuleKeys.of(network)
                preferences[keys.photos] = rules.photos
                preferences[keys.videos] = rules.videos
                preferences[keys.videoSizeLimit] = rules.videoSizeLimit
                preferences[keys.files] = rules.files
                preferences[keys.fileSizeLimit] = rules.fileSizeLimit
                preferences[keys.voiceAndVideoMessages] = rules.voiceAndVideoMessages
            }

        private suspend fun edit(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

private fun Preferences.toAutoDownloadSettings(): AutoDownloadSettings {
    val defaults = AutoDownloadSettings()
    return AutoDownloadNetwork.entries.fold(defaults) { settings, network ->
        settings.with(network, rules(RuleKeys.of(network), defaults.rulesFor(network)))
    }
}

private fun Preferences.rules(
    keys: RuleKeys,
    defaults: AutoDownloadRules,
) = AutoDownloadRules(
    photos = this[keys.photos] ?: defaults.photos,
    videos = this[keys.videos] ?: defaults.videos,
    videoSizeLimit = this[keys.videoSizeLimit]?.takeIf { it > 0 } ?: defaults.videoSizeLimit,
    files = this[keys.files] ?: defaults.files,
    fileSizeLimit = this[keys.fileSizeLimit]?.takeIf { it > 0 } ?: defaults.fileSizeLimit,
    voiceAndVideoMessages = this[keys.voiceAndVideoMessages] ?: defaults.voiceAndVideoMessages,
)

/** The keys of one network's rules, under `auto_download.<network>.`; renaming one loses what users saved under it. */
private class RuleKeys(
    network: String,
) {
    val photos = booleanPreferencesKey("auto_download.$network.photos")
    val videos = booleanPreferencesKey("auto_download.$network.videos")
    val videoSizeLimit = longPreferencesKey("auto_download.$network.video_size_limit")
    val files = booleanPreferencesKey("auto_download.$network.files")
    val fileSizeLimit = longPreferencesKey("auto_download.$network.file_size_limit")
    val voiceAndVideoMessages = booleanPreferencesKey("auto_download.$network.voice_video_messages")

    companion object {
        // Spelled out rather than the enum's names, so renaming a constant keeps what was saved.
        private val Mobile = RuleKeys("mobile")
        private val WiFi = RuleKeys("wifi")
        private val Roaming = RuleKeys("roaming")

        fun of(network: AutoDownloadNetwork): RuleKeys =
            when (network) {
                AutoDownloadNetwork.Mobile -> Mobile
                AutoDownloadNetwork.WiFi -> WiFi
                AutoDownloadNetwork.Roaming -> Roaming
            }
    }
}

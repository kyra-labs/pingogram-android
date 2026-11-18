package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [DataStoreSettingsBackupRepository] on a real Preferences DataStore in a temporary file. */
class DataStoreSettingsBackupRepositoryTest {
    @TempDir
    lateinit var directory: File

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(directory, "s.preferences_pb") },
        )

    @Test
    fun `a backup holds the backed-up settings of every type, and never the passcode or locked chats`() =
        runTest {
            val store = dataStore()
            store.edit {
                it[stringPreferencesKey("appearance.theme_mode")] = "Dark"
                it[floatPreferencesKey("playback.speed")] = 1.5f
                it[longPreferencesKey("auto_download.wifi.video_size_limit")] = 10L
                it[stringSetPreferencesKey("power_saving.kept_on")] = setOf("Stickers")
                it[stringPreferencesKey("passcode.hash")] = "hash"
                it[stringSetPreferencesKey("privacy.locked_chats")] = setOf("1")
                it[booleanPreferencesKey("media_viewer.controls_hint_shown")] = true
            }

            assertEquals(
                mapOf(
                    "appearance.theme_mode" to "Dark",
                    "playback.speed" to 1.5f,
                    "auto_download.wifi.video_size_limit" to 10L,
                    "power_saving.kept_on" to setOf("Stickers"),
                ),
                DataStoreSettingsBackupRepository(store).backedUpSettings(),
            )
        }

    @Test
    fun `restoring replaces the backed-up settings and keeps the passcode, even when the backup names it`() =
        runTest {
            val store = dataStore()
            store.edit {
                it[stringPreferencesKey("appearance.theme_mode")] = "Dark"
                it[booleanPreferencesKey("stickers.loop")] = false
                it[stringPreferencesKey("passcode.hash")] = "this phone's"
            }

            val restored =
                DataStoreSettingsBackupRepository(store).restoreSettings(
                    mapOf(
                        "appearance.theme_mode" to "Light",
                        "playback.skip_silence" to true,
                        "passcode.hash" to "the backup's",
                        "privacy.locked_chats" to setOf("7"),
                    ),
                )

            assertEquals(Outcome.Success(Unit), restored)
            assertEquals(
                mapOf(
                    "appearance.theme_mode" to "Light",
                    "playback.skip_silence" to true,
                    "passcode.hash" to "this phone's",
                ),
                store.data
                    .first()
                    .asMap()
                    .mapKeys { it.key.name },
            )
        }
}

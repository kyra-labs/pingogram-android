package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.SettingsBackupRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsBackupRepository] on the app's settings file ([SettingsDataStore]): the keys of [BackedUpSettings] go into
 * backups, those of [LeftOutSettings] never do, and a test fails while a key of the file is in neither.
 */
@Singleton
class DataStoreSettingsBackupRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : SettingsBackupRepository {
        override suspend fun backedUpSettings(): Map<String, Any> =
            try {
                dataStore.data
                    .first()
                    .asMap()
                    .filter { (key, value) -> isBackedUp(key.name) && value.isSettingValue() }
                    .mapKeys { (key, _) -> key.name }
            } catch (_: IOException) {
                emptyMap()
            }

        override suspend fun restoreSettings(settings: Map<String, Any>): Outcome<Unit> =
            try {
                dataStore.edit { preferences ->
                    preferences
                        .asMap()
                        .keys
                        .filter { isBackedUp(it.name) }
                        .forEach { preferences.remove(it) }
                    settings.forEach { (name, value) -> if (isBackedUp(name)) preferences.put(name, value) }
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not restored: ${error.message}"))
            }
    }

/** Whether the setting [name] goes into backups. */
internal fun isBackedUp(name: String): Boolean = BackedUpSettings.any { it.covers(name) }

/** Whether this name of [BackedUpSettings] or [LeftOutSettings] is [name], or, ending with a dot, starts it. */
internal fun String.covers(name: String): Boolean = if (endsWith(".")) name.startsWith(this) else name == this

private fun Any.isSettingValue(): Boolean =
    this is Boolean || this is Int || this is Long || this is Float || this is Double || this is String ||
        (this is Set<*> && all { it is String })

private fun MutablePreferences.put(
    name: String,
    value: Any,
) {
    when (value) {
        is Boolean -> this[booleanPreferencesKey(name)] = value
        is Int -> this[intPreferencesKey(name)] = value
        is Long -> this[longPreferencesKey(name)] = value
        is Float -> this[floatPreferencesKey(name)] = value
        is Double -> this[doublePreferencesKey(name)] = value
        is String -> this[stringPreferencesKey(name)] = value
        is Set<*> -> this[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
    }
}

/**
 * The settings file's keys that go into backups; one ending with a dot stands for every key that starts with it. A new
 * setting stays out of backups until it is added here or to [LeftOutSettings].
 */
internal val BackedUpSettings: Set<String> =
    setOf(
        "appearance.theme_mode",
        "appearance.dynamic_color",
        "appearance.palette",
        "appearance.contrast",
        "appearance.amoled",
        "appearance.chat.text_scale",
        "appearance.chat.bubble_corners",
        "appearance.chat.wallpaper",
        "appearance.chat.group_bubbles",
        "appearance.chat.wallpaper_dimming",
        "appearance.chat.wallpaper_seed.",
        "auto_download.",
        "calls.data_saving",
        "chat.double_tap.incoming",
        "chat.double_tap.outgoing",
        "chat_list.archive_row",
        "chat_list.show_stories",
        "chat_list.swipe_start",
        "chat_list.swipe_end",
        "chat_list.swipe_changes_folder",
        "chat_list.edit_folders_button",
        "chat_list.chat_type_icons",
        "chat_list.folder_tab_style",
        "chat_list.catch_up_includes_muted",
        "chat_list.feed_tab",
        "composer.enter_sends_hardware",
        "composer.enter_sends_on_screen",
        "composer.send_with_ctrl_enter",
        "composer.send_photos_original",
        "composer.records_video_messages",
        "composer.recent_emoji",
        "focus.profiles",
        "focus.manual",
        "focus.last_used",
        "focus.next_id",
        "focus.shows_in_chat_list",
        "keyword_alerts.words",
        "keyword_alerts.where",
        "keyword_alerts.folders",
        "keyword_alerts.alert_when_muted",
        "keyword_alerts.show_in_chat_list",
        "language.app_language",
        "notifications.from_all_accounts",
        "notifications.keep_connected",
        "playback.raise_to_listen",
        "playback.pause_when_lowered",
        "playback.skip_silence",
        "playback.speed",
        "power_saving.mode",
        "power_saving.kept_on",
        "privacy.hide_phone_numbers",
        "privacy.secret_chat_link_previews",
        "privacy.direct_share",
        "privacy.show_ids",
        "privacy.incognito_keyboard",
        "privacy.clean_links",
        "share.chats_shown_first",
        "stickers.suggestions",
        "stickers.loop",
        "transcription.automatic",
    )

/**
 * The settings file's keys that never go into backups, and why; one ending with a dot stands for every key that starts
 * with it. (The account registry and the widgets' state are files of their own, never in the settings file.)
 */
internal val LeftOutSettings: Map<String, String> =
    mapOf(
        "passcode." to "the passcode stays as it is on the phone a backup is restored on",
        "backup." to "the backup folder's grant and the restore card's choices are this phone's",
        "privacy.locked_chats" to "a locked chat's existence is itself private",
        "media_viewer.controls_hint_shown" to "a one-time hint",
        "transcription.notice_shown" to "a one-time hint",
        "notifications.prompt_dismissed" to "a one-time prompt",
        "notifications.permission_requested" to "a one-time prompt, about this phone's permission",
        "contacts.sync_prompt_dismissed" to "a one-time prompt",
        "ai_editor.acknowledged" to "consent given on this phone",
        "contacts.sync_enabled" to "syncs this phone's contacts, with its permission",
        "contacts.sync_last_synced" to "about this phone's contacts",
        "welcome.pack_collapsed." to "a one-time hint per chat",
        "birthday.declined." to "a one-time prompt per chat",
        "pinned.hidden_up_to." to "a pinned message hidden in one chat until the next one",
        "translation.bar_hidden." to "a translate bar hidden in one chat",
    )

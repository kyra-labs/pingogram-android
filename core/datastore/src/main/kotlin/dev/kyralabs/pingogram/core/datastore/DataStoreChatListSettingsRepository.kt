package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ChatListSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). Swipe actions are
 * stored by name; a file that cannot be read, a missing key and an unknown name read as the defaults.
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
                .map { preferences ->
                    val defaults = ChatListSettings()
                    ChatListSettings(
                        showStories = preferences[SHOW_STORIES] ?: defaults.showStories,
                        startSwipe = swipeActionOf(preferences[START_SWIPE]) ?: defaults.startSwipe,
                        endSwipe = swipeActionOf(preferences[END_SWIPE]) ?: defaults.endSwipe,
                    )
                }.distinctUntilChanged()

        override suspend fun setShowStories(show: Boolean): Outcome<Unit> = save { it[SHOW_STORIES] = show }

        override suspend fun setSwipeActions(
            start: SwipeAction,
            end: SwipeAction,
        ): Outcome<Unit> =
            save {
                it[START_SWIPE] = start.name
                it[END_SWIPE] = end.name
            }

        private suspend fun save(change: (MutablePreferences) -> Unit): Outcome<Unit> =
            try {
                dataStore.edit(change)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

private fun swipeActionOf(name: String?): SwipeAction? = SwipeAction.entries.firstOrNull { it.name == name }

// The keys in the settings file; renaming one loses what users saved under it.
private val SHOW_STORIES = booleanPreferencesKey("chat_list.show_stories")
private val START_SWIPE = stringPreferencesKey("chat_list.swipe_start")
private val END_SWIPE = stringPreferencesKey("chat_list.swipe_end")

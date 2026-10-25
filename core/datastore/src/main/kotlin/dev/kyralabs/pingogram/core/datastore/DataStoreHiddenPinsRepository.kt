package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HiddenPinsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): one key per chat (or
 * forum topic, or message thread) whose bar was hidden, holding the newest pinned message then. A file that cannot be
 * read reads as nothing hidden, so the worst case is a bar that shows again.
 */
@Singleton
class DataStoreHiddenPinsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : HiddenPinsRepository {
        override fun hiddenUpTo(
            chatId: Long,
            topic: MessageTopic?,
        ): Flow<Long?> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[keyOf(chatId, topic)] }
                .distinctUntilChanged()

        override suspend fun hide(
            chatId: Long,
            messageId: Long,
            topic: MessageTopic?,
        ): Outcome<Unit> =
            try {
                dataStore.edit { it[keyOf(chatId, topic)] = messageId }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// Keys of the settings file; renaming them shows hidden bars again. A forum topic's or a thread's bar has its own.
private fun keyOf(
    chatId: Long,
    topic: MessageTopic?,
) = longPreferencesKey(
    when (topic) {
        null -> "pinned.hidden_up_to.$chatId"
        is MessageTopic.Forum -> "pinned.hidden_up_to.$chatId.topic.${topic.topicId}"
        is MessageTopic.Thread -> "pinned.hidden_up_to.$chatId.thread.${topic.threadId}"
        is MessageTopic.SavedMessages -> "pinned.hidden_up_to.$chatId.saved.${topic.topicId}"
    },
)

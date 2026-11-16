package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.DeclinedBirthdaysRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeclinedBirthdaysRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): one key per chat
 * with a declined suggestion, holding the messages' ids. A file that cannot be read reads as nothing declined, so the
 * worst case is a card that offers "Accept" again.
 */
@Singleton
class DataStoreDeclinedBirthdaysRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : DeclinedBirthdaysRepository {
        override fun declined(chatId: Long): Flow<Set<Long>> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    preferences[keyOf(chatId)].orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }
                }.distinctUntilChanged()

        override suspend fun decline(
            chatId: Long,
            messageId: Long,
        ): Outcome<Unit> =
            try {
                dataStore.edit { it[keyOf(chatId)] = it[keyOf(chatId)].orEmpty() + messageId.toString() }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key of the settings file; renaming it offers declined suggestions again.
private fun keyOf(chatId: Long) = stringSetPreferencesKey("birthday.declined.$chatId")

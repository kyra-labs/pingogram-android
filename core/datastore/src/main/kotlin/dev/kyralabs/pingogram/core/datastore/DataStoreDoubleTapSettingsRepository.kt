package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DoubleTapSettingsRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]). A file that
 * cannot be read and missing keys read as the defaults.
 */
@Singleton
class DataStoreDoubleTapSettingsRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : DoubleTapSettingsRepository {
        override val doubleTap: Flow<DoubleTapReactions> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { preferences ->
                    val defaults = DoubleTapReactions()
                    DoubleTapReactions(
                        incoming = preferences[INCOMING] ?: defaults.incoming,
                        outgoing = preferences[OUTGOING] ?: defaults.outgoing,
                    )
                }.distinctUntilChanged()

        override suspend fun setDoubleTap(doubleTap: DoubleTapReactions): Outcome<Unit> =
            try {
                dataStore.edit {
                    it[INCOMING] = doubleTap.incoming
                    it[OUTGOING] = doubleTap.outgoing
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The keys in the settings file; renaming them loses what users saved under them.
private val INCOMING = booleanPreferencesKey("chat.double_tap.incoming")
private val OUTGOING = booleanPreferencesKey("chat.double_tap.outgoing")

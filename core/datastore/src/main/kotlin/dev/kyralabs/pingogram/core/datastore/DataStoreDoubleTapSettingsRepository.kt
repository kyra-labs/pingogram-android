package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DoubleTapAction
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
                        incoming = actionOf(preferences[INCOMING_ACTION], preferences[INCOMING]) ?: defaults.incoming,
                        outgoing = actionOf(preferences[OUTGOING_ACTION], preferences[OUTGOING]) ?: defaults.outgoing,
                    )
                }.distinctUntilChanged()

        override suspend fun setDoubleTap(doubleTap: DoubleTapReactions): Outcome<Unit> =
            try {
                dataStore.edit {
                    it[INCOMING_ACTION] = doubleTap.incoming.name
                    it[OUTGOING_ACTION] = doubleTap.outgoing.name
                    it.remove(INCOMING)
                    it.remove(OUTGOING)
                }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

/**
 * The action saved under [name]; else the switch saved before there were actions ([reacted]: React or Nothing); null
 * for neither, or an action this version does not know.
 */
private fun actionOf(
    name: String?,
    reacted: Boolean?,
): DoubleTapAction? =
    when {
        name != null -> DoubleTapAction.entries.firstOrNull { it.name == name }
        reacted != null -> if (reacted) DoubleTapAction.React else DoubleTapAction.Nothing
        else -> null
    }

// The keys in the settings file; renaming them loses what users saved under them.
private val INCOMING_ACTION = stringPreferencesKey("chat.double_tap.incoming_action")
private val OUTGOING_ACTION = stringPreferencesKey("chat.double_tap.outgoing_action")

/** The switches before [INCOMING_ACTION] and [OUTGOING_ACTION], read until the first save, which removes them. */
private val INCOMING = booleanPreferencesKey("chat.double_tap.incoming")
private val OUTGOING = booleanPreferencesKey("chat.double_tap.outgoing")

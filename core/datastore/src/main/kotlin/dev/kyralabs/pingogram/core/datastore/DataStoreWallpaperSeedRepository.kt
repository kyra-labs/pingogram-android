package dev.kyralabs.pingogram.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.WallpaperSeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WallpaperSeedRepository] in the app's settings, a Preferences DataStore ([SettingsDataStore]): one key per
 * background, backed up with the other wallpaper settings. A file that cannot be read reads as no seed, so the worst
 * case is a seed computed again.
 */
@Singleton
class DataStoreWallpaperSeedRepository
    @Inject
    constructor(
        @param:SettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : WallpaperSeedRepository {
        override fun seed(wallpaperId: Long): Flow<Int?> =
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map { it[keyOf(wallpaperId)] }
                .distinctUntilChanged()

        override suspend fun setSeed(
            wallpaperId: Long,
            argb: Int,
        ): Outcome<Unit> =
            try {
                dataStore.edit { it[keyOf(wallpaperId)] = argb }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Settings not saved: ${error.message}"))
            }
    }

// The key of the settings file (backed up: BackedUpSettings); renaming it computes every seed again.
private fun keyOf(wallpaperId: Long) = intPreferencesKey("appearance.chat.wallpaper_seed.$wallpaperId")

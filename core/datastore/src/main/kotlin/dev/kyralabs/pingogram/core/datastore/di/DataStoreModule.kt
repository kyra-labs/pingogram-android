package dev.kyralabs.pingogram.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.datastore.DataStoreAppearanceRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreAutoDownloadRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreComposerSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenPinsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreKeepConnectedRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreNotificationPromptRepository
import dev.kyralabs.pingogram.core.datastore.DataStorePasscodeRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreRecentEmojiRepository
import dev.kyralabs.pingogram.core.datastore.SettingsDataStore
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * The app's settings file, a Preferences DataStore in `filesDir/datastore/settings.preferences_pb` (never backed up:
 * the app allows no backup), and the repositories on it. One instance per process: DataStore refuses two for one
 * file. It reads and writes on the IO dispatcher, in a scope that lives as long as the process.
 */
@Module
@InstallIn(SingletonComponent::class)
interface DataStoreModule {
    @Binds
    fun appearanceRepository(repository: DataStoreAppearanceRepository): AppearanceRepository

    @Binds
    fun notificationPromptRepository(repository: DataStoreNotificationPromptRepository): NotificationPromptRepository

    @Binds
    fun keepConnectedRepository(repository: DataStoreKeepConnectedRepository): KeepConnectedRepository

    @Binds
    fun composerSettingsRepository(repository: DataStoreComposerSettingsRepository): ComposerSettingsRepository

    @Binds
    fun passcodeRepository(repository: DataStorePasscodeRepository): PasscodeRepository

    @Binds
    fun recentEmojiRepository(repository: DataStoreRecentEmojiRepository): RecentEmojiRepository

    @Binds
    fun autoDownloadRepository(repository: DataStoreAutoDownloadRepository): AutoDownloadRepository

    @Binds
    fun hiddenPinsRepository(repository: DataStoreHiddenPinsRepository): HiddenPinsRepository

    @Binds
    fun hiddenTranslateBarsRepository(repository: DataStoreHiddenTranslateBarsRepository): HiddenTranslateBarsRepository

    @Binds
    fun contactSyncSettingsRepository(
        repository: DataStoreContactSyncSettingsRepository,
    ): ContactSyncSettingsRepository

    companion object {
        @Provides
        @Singleton
        @SettingsDataStore
        fun settingsDataStore(
            @ApplicationContext context: Context,
            @Dispatcher(IO) ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                // A corrupt file starts over with the defaults rather than failing every read.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
            )

        private const val SETTINGS_FILE = "settings"
    }
}

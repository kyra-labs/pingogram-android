package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.datastore.DataStoreComposerSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreDoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenPinsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreHiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.datastore.DataStoreRecentEmojiRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DoubleTapSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.HiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RecentEmojiRepository

/**
 * The repositories of the settings file ([DataStoreModule]) that conversations read: the composer's, the bars' and the
 * link previews'.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ChatPreferencesModule {
    @Binds
    fun composerSettingsRepository(repository: DataStoreComposerSettingsRepository): ComposerSettingsRepository

    @Binds
    fun doubleTapSettingsRepository(repository: DataStoreDoubleTapSettingsRepository): DoubleTapSettingsRepository

    @Binds
    fun recentEmojiRepository(repository: DataStoreRecentEmojiRepository): RecentEmojiRepository

    @Binds
    fun hiddenPinsRepository(repository: DataStoreHiddenPinsRepository): HiddenPinsRepository

    @Binds
    fun hiddenTranslateBarsRepository(repository: DataStoreHiddenTranslateBarsRepository): HiddenTranslateBarsRepository

    @Binds
    fun linkPreviewSettingsRepository(repository: DataStoreLinkPreviewSettingsRepository): LinkPreviewSettingsRepository
}

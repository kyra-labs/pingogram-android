package dev.kyralabs.pingogram.feature.media.ui.files

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** The viewer's view model saves into the gallery through [AndroidMediaFileActions]. */
@Module
@InstallIn(ViewModelComponent::class)
internal interface MediaFilesModule {
    @Binds
    fun mediaFileActions(actions: AndroidMediaFileActions): MediaFileActions
}

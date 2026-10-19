package dev.kyralabs.pingogram.feature.settings.ui.photo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Edit profile's view model prepares new profile photos through [AndroidProfilePhotoFiles]. */
@Module
@InstallIn(ViewModelComponent::class)
internal interface ProfilePhotoFilesModule {
    @Binds
    fun profilePhotoFiles(files: AndroidProfilePhotoFiles): ProfilePhotoFiles
}

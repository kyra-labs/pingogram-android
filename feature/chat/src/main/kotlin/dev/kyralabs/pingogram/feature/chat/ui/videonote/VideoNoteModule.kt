package dev.kyralabs.pingogram.feature.chat.ui.videonote

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Each conversation's view model gets a video recorder of its own, released with it ([CameraXVideoNoteRecorder]). */
@Module
@InstallIn(ViewModelComponent::class)
internal interface VideoNoteModule {
    @Binds
    fun videoNoteRecorder(recorder: CameraXVideoNoteRecorder): VideoNoteRecorder
}

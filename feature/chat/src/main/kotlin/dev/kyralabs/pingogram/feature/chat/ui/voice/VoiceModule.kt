package dev.kyralabs.pingogram.feature.chat.ui.voice

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Each conversation's view model gets a voice recorder of its own, released with it ([MediaVoiceRecorder]). */
@Module
@InstallIn(ViewModelComponent::class)
internal interface VoiceModule {
    @Binds
    fun voiceRecorder(recorder: MediaVoiceRecorder): VoiceRecorder
}

package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.speech.AndroidTextSpeaker
import dev.kyralabs.pingogram.core.domain.repository.TextSpeaker

/** Reading text aloud with the device's own speech engine. */
@Module
@InstallIn(SingletonComponent::class)
interface SpeechModule {
    @Binds
    fun textSpeaker(speaker: AndroidTextSpeaker): TextSpeaker
}

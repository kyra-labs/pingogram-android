package dev.kyralabs.pingogram.feature.chat.ui.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Each conversation's view model gets a player of its own, released with it ([ExoAudioPlayer], [RaiseToListen]). */
@Module
@InstallIn(ViewModelComponent::class)
internal interface PlayerModule {
    @Binds
    fun audioPlayer(player: ExoAudioPlayer): AudioPlayer

    @Binds
    fun proximitySensor(sensor: AndroidProximitySensor): ProximitySensor

    @Binds
    fun earpieceRoute(route: AndroidEarpieceRoute): EarpieceRoute
}

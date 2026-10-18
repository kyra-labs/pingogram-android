package dev.kyralabs.pingogram.feature.media.ui.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** The viewer's view model gets a player of its own, released with it ([ExoVideoPlayer]). */
@Module
@InstallIn(ViewModelComponent::class)
internal interface PlayerModule {
    @Binds
    fun videoPlayer(player: ExoVideoPlayer): VideoPlayer
}

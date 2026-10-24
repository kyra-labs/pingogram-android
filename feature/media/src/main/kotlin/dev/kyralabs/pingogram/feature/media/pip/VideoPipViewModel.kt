package dev.kyralabs.pingogram.feature.media.pip

import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The video [VideoPipActivity] plays in picture-in-picture, on a player of its own: taken from [videos] once, played
 * from the position the viewer left it at, paused and played on by the window's button, given back when the window is
 * expanded ([giveBack]), and released with the activity. [state] is null without a video to play: the activity closes.
 */
@HiltViewModel
class VideoPipViewModel
    @Inject
    internal constructor(
        private val videos: PictureInPictureVideos,
        private val player: VideoPlayer,
    ) : ViewModel() {
        private val video: PipVideo? = videos.take()

        private val mutableState = MutableStateFlow(video?.let { PipState(it, isPlaying = true, ended = false) })

        /** The video and whether it plays; null once there is nothing to show. */
        val state: StateFlow<PipState?> = mutableState.asStateFlow()

        /** The Media3 player the window's surface shows. */
        val surfacePlayer: Player? get() = player.surfacePlayer

        init {
            player.listener =
                object : VideoPlayer.Listener {
                    override fun onPlayingChanged(isPlaying: Boolean) {
                        mutableState.value = mutableState.value?.copy(isPlaying = isPlaying)
                    }

                    override fun onFirstFrame() = Unit

                    override fun onEnded() {
                        mutableState.value = mutableState.value?.copy(isPlaying = false, ended = true)
                    }

                    override fun onError() {
                        mutableState.value = null
                    }
                }
            if (video != null) {
                player.play(video.source, VideoKind.Video)
                if (video.positionMillis > 0) player.seekTo(video.positionMillis)
            }
        }

        /** The window's play or pause button. */
        fun onAction(action: PipAction) {
            val current = mutableState.value ?: return
            when (action) {
                PipAction.PlayPause -> {
                    if (current.isPlaying) player.pause() else player.resume()
                    mutableState.value = current.copy(isPlaying = !current.isPlaying, ended = false)
                }
            }
        }

        /** The window was expanded: the video goes back to its viewer at the position it reached. */
        fun giveBack() {
            val video = video ?: return
            player.pause()
            val position = if (mutableState.value?.ended == true) 0L else player.positionMillis
            videos.giveBack(video.copy(positionMillis = position))
        }

        override fun onCleared() {
            player.listener = null
            player.release()
        }
    }

/** The video in picture-in-picture, whether it plays, and whether it played to its end. */
data class PipState(
    val video: PipVideo,
    val isPlaying: Boolean,
    val ended: Boolean,
)

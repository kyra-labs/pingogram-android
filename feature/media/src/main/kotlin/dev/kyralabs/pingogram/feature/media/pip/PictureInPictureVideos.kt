package dev.kyralabs.pingogram.feature.media.pip

import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a viewer's video to picture-in-picture and back (`docs/DESIGN_SYSTEM.md` §6, media viewer): the viewer gives
 * it to [VideoPipActivity] with [start], which takes it ([take]) and plays on from there in its own window; expanding
 * the window gives it back ([giveBack]), which the app's navigation follows ([returns]) by opening the viewer again at
 * that video, which takes up its position ([takeReturn]). In memory only, one video at a time; main thread only.
 */
@Singleton
class PictureInPictureVideos
    @Inject
    constructor() {
        private var handed: PipVideo? = null

        private val mutableReturns = MutableStateFlow<PipVideo?>(null)

        /** A video given back from picture-in-picture, whose viewer is to open; null once it opened. */
        val returns: StateFlow<PipVideo?> = mutableReturns.asStateFlow()

        private val mutableActions =
            MutableSharedFlow<PipAction>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** What the window's buttons ask for, for the activity that shows it. */
        val actions: SharedFlow<PipAction> = mutableActions.asSharedFlow()

        /** The viewer hands [video] to picture-in-picture; one given back and not yet reopened is dropped. */
        fun start(video: PipVideo) {
            handed = video
            mutableReturns.value = null
        }

        /** The video to play in picture-in-picture, once; null without one. */
        fun take(): PipVideo? = handed.also { handed = null }

        /** Picture-in-picture gives [video] back to the app, at [PipVideo.positionMillis]. */
        fun giveBack(video: PipVideo) {
            mutableReturns.value = video
        }

        /** The navigation opened the viewer for [video]. */
        fun reopened(video: PipVideo) {
            mutableReturns.compareAndSet(video, video.copy(reopened = true))
        }

        /**
         * Where the viewer of message [messageId] of chat [chatId] is to start its video, if picture-in-picture gave it
         * back; once.
         */
        fun takeReturn(
            chatId: Long,
            messageId: Long,
        ): PipVideo? {
            val video = mutableReturns.value?.takeIf { it.chatId == chatId && it.messageId == messageId } ?: return null
            mutableReturns.value = null
            return video
        }

        /** A button of the window was pressed. */
        fun onAction(action: PipAction) {
            mutableActions.tryEmit(action)
        }
    }

/**
 * A viewer's video in picture-in-picture: message [messageId] of chat [chatId], played from [source] at
 * [positionMillis], [width] × [height] (0 where unknown). [reopened] once the navigation opened its viewer again.
 */
data class PipVideo(
    val chatId: Long,
    val messageId: Long,
    val source: PlaySource,
    val positionMillis: Long,
    val width: Int,
    val height: Int,
    val reopened: Boolean = false,
)

/** What a button of the picture-in-picture window asks for. */
enum class PipAction {
    PlayPause,
}

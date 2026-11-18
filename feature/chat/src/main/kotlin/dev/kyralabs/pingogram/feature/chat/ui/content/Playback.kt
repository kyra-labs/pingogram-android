package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.runtime.Stable

/**
 * How a voice message, a round video message or a song is playing, as the ViewModel's player reports it; [Stopped] for
 * every other one.
 *
 * @property isPlaying it is the one playing now, or about to once its file is downloaded.
 * @property inPlayer it is the one in the player, playing or paused: a round video message shows grown while it is.
 * @property progress how much of it has played, 0–1. It may read snapshot state, e.g. a player position updated every
 *   frame: waveforms and progress bars read it in the draw phase, the time once a second ([playedSeconds]).
 */
@Stable
class Playback(
    val isPlaying: Boolean,
    val progress: () -> Float,
    val inPlayer: Boolean = true,
) {
    companion object {
        /** Not playing, from the start. */
        val Stopped = Playback(isPlaying = false, progress = { 0f }, inPlayer = false)
    }
}

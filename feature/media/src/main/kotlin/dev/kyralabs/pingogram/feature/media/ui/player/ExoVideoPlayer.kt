package dev.kyralabs.pingogram.feature.media.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * [VideoPlayer] on Media3's [ExoPlayer], playing files TDLib has downloaded (no network module). The player is built
 * on the first [play], on the main thread it then belongs to, so a viewer that shows only photos costs nothing;
 * [release] frees it. The viewer's video surface attaches itself to [surfacePlayer].
 *
 * A video holds audio focus while it plays (it pauses when another app plays, and plays on after a short
 * interruption) and pauses when headphones are unplugged. A GIF loops silently: its audio track, if any, is not even
 * decoded, and it asks for no audio focus, so music elsewhere keeps playing. The player sets no wake lock (the app
 * holds no `WAKE_LOCK` permission); the screen stays on while a video plays because the viewer keeps it on.
 */
internal class ExoVideoPlayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : VideoPlayer {
        private var player: ExoPlayer? = null

        /** The kind the player is set up for. */
        private var kind: VideoKind? = null

        override var listener: VideoPlayer.Listener? = null

        private val playerListener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    // Our own calls report themselves; these are audio focus, unplugged headphones and the like.
                    if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        listener?.onPlayingChanged(playWhenReady)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) listener?.onEnded()
                }

                override fun onRenderedFirstFrame() {
                    listener?.onFirstFrame()
                }

                override fun onPlayerError(error: PlaybackException) {
                    listener?.onError()
                }
            }

        override val surfacePlayer: Player? get() = player

        override val positionMillis: Long get() = player?.currentPosition ?: 0L

        override val durationMillis: Long?
            get() = player?.duration?.takeIf { it != C.TIME_UNSET }

        override fun play(
            path: String,
            kind: VideoKind,
        ) {
            val player = player ?: newPlayer().also { player = it }
            if (this.kind != kind) {
                player.setUp(kind)
                this.kind = kind
            }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            player.prepare()
            player.play()
        }

        override fun pause() {
            player?.pause()
        }

        override fun resume() {
            val player = player ?: return
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }

        override fun seekTo(positionMillis: Long) {
            player?.seekTo(positionMillis)
        }

        override fun stop() {
            player?.run {
                stop()
                clearMediaItems()
            }
        }

        override fun release() {
            listener = null
            player?.release()
            player = null
        }

        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply { addListener(playerListener) }
    }

/** A video with its sound and audio focus, once; a GIF silent, without audio focus, in a loop. */
private fun ExoPlayer.setUp(kind: VideoKind) {
    val isGif = kind == VideoKind.Gif
    setAudioAttributes(MOVIE_ATTRIBUTES, !isGif)
    volume = if (isGif) 0f else 1f
    repeatMode = if (isGif) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, isGif)
            .build()
}

/** Media playback of a film; ExoPlayer derives the audio focus it asks for from these. */
private val MOVIE_ATTRIBUTES: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

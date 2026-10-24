package dev.kyralabs.pingogram.feature.media.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.repository.FileStreamRepository
import dev.kyralabs.pingogram.core.ui.media.TdlibDataSource
import java.io.File
import javax.inject.Inject

/**
 * [VideoPlayer] on Media3's [ExoPlayer], playing files TDLib has downloaded or downloads as they play
 * ([TdlibDataSource], `docs/adr/0028-streaming-media.md`; no network module). The player is built
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
        private val streams: FileStreamRepository,
    ) : VideoPlayer {
        private var player: ExoPlayer? = null

        /** The kind the player is set up for. */
        private var kind: VideoKind? = null

        /** Whether a video plays silenced ([setMuted]); GIFs are always silent. */
        private var isMuted = false

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

        override val bufferedMillis: Long get() = player?.bufferedPosition ?: 0L

        override fun play(
            source: PlaySource,
            kind: VideoKind,
        ) {
            val player = player ?: newPlayer().also { player = it }
            if (this.kind != kind) {
                player.setUp(kind)
                this.kind = kind
            }
            if (kind == VideoKind.Video) player.volume = if (isMuted) 0f else 1f
            val uri =
                when (source) {
                    is PlaySource.File -> Uri.fromFile(File(source.path))
                    is PlaySource.Stream -> TdlibDataSource.uriOf(source.fileId)
                }
            player.setMediaItem(MediaItem.fromUri(uri))
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

        override fun setMuted(muted: Boolean) {
            isMuted = muted
            if (kind == VideoKind.Video) player?.volume = if (muted) 0f else 1f
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

        /** Files on the device as they are; TDLib's (`tdlib-file://`, not a scheme of the device's) as they stream. */
        @androidx.annotation.OptIn(UnstableApi::class)
        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(DefaultDataSource.Factory(context, TdlibDataSource.Factory(streams))),
                ).setHandleAudioBecomingNoisy(true)
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

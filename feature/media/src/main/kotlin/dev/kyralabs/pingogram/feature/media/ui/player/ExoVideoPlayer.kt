package dev.kyralabs.pingogram.feature.media.ui.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * [VideoPlayer] on Media3's [ExoPlayer], playing files TDLib has downloaded or downloads as they play
 * ([TdlibDataSource], `docs/adr/0028-streaming-media.md`; no network module). A viewer that shows only photos costs
 * nothing: the player is made for the first [play]. The viewer's video surface attaches itself to [surfacePlayer],
 * which composition reads as snapshot state, so the surface attaches as soon as the player is there.
 *
 * Neither making the player nor freeing it blocks a frame (`docs/audit/performance-review-2026-11.md`, M2):
 *
 * - it is built on a thread of its own, for the main looper it then belongs to: building one creates its renderers, its
 *   audio sink, its bandwidth meter and its playback thread, 10–30 ms on a phone, which landed in the frame the viewer
 *   composed its content, just before its container transform. What is asked of it meanwhile ([play], [pause],
 *   [resume], [seekTo], [setMuted], [stop]) is applied in order once it is there;
 * - [release] stops it at once (asynchronous) and frees it a second later on the main looper: `ExoPlayer.release()`
 *   must run there and waits for the playback thread to release the codec and the audio track, which made the last
 *   frame of closing a video late by as much.
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
        private val mainHandler = Handler(Looper.getMainLooper())

        /** The player, once built; snapshot state, so the video surface attaches as it arrives. */
        private var player by mutableStateOf<ExoPlayer?>(null)

        /** Whether a player is being built on [builder]. */
        private var building = false

        /** Whether [release] was called: a player that arrives after it is freed at once. */
        private var released = false

        /** What was asked of the player before it was there, to apply once it is ([Pending]). */
        private var pending = Pending()

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

        override val positionMillis: Long get() = player?.currentPosition ?: pending.seekMillis ?: 0L

        override val durationMillis: Long?
            get() = player?.duration?.takeIf { it != C.TIME_UNSET }

        override val bufferedMillis: Long get() = player?.bufferedPosition ?: 0L

        override fun play(
            source: PlaySource,
            kind: VideoKind,
        ) {
            val player = player
            if (player == null) {
                pending = Pending(source = source, kind = kind)
                build()
                return
            }
            start(player, source, kind)
        }

        override fun pause() {
            player?.pause() ?: run { pending = pending.copy(paused = true) }
        }

        override fun resume() {
            val player = player
            if (player == null) {
                pending = pending.copy(paused = false)
                return
            }
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }

        override fun seekTo(positionMillis: Long) {
            player?.seekTo(positionMillis) ?: run { pending = pending.copy(seekMillis = positionMillis) }
        }

        override fun setMuted(muted: Boolean) {
            isMuted = muted
            if (kind == VideoKind.Video) player?.volume = if (muted) 0f else 1f
        }

        override fun stop() {
            pending = Pending()
            player?.run {
                stop()
                clearMediaItems()
            }
        }

        override fun release() {
            listener = null
            released = true
            pending = Pending()
            val player = player ?: return
            this.player = null
            player.stop()
            mainHandler.releaseLater(player)
        }

        private fun start(
            player: ExoPlayer,
            source: PlaySource,
            kind: VideoKind,
        ) {
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

        /** Builds the player on [builder] once, then takes it on the main thread, with what was asked meanwhile. */
        private fun build() {
            if (building || released) return
            building = true
            builder.execute {
                val built = newPlayer()
                mainHandler.post {
                    building = false
                    if (released) {
                        mainHandler.releaseLater(built)
                    } else {
                        // Takes the player, with what was asked of it meanwhile.
                        built.addListener(playerListener)
                        player = built
                        val asked = pending
                        pending = Pending()
                        if (asked.source != null && asked.kind != null) {
                            start(built, asked.source, asked.kind)
                            asked.seekMillis?.let(built::seekTo)
                            if (asked.paused) built.pause()
                        }
                    }
                }
            }
        }

        /** Files on the device as they are; TDLib's (`tdlib-file://`, not a scheme of the device's) as they stream. */
        @androidx.annotation.OptIn(UnstableApi::class)
        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setLooper(Looper.getMainLooper())
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(DefaultDataSource.Factory(context, TdlibDataSource.Factory(streams))),
                ).setHandleAudioBecomingNoisy(true)
                .build()

        /** What was asked of a player not built yet: the file to play, where to start, and whether it is paused. */
        private data class Pending(
            val source: PlaySource? = null,
            val kind: VideoKind? = null,
            val seekMillis: Long? = null,
            val paused: Boolean = false,
        )

        private companion object {
            /** The thread players are built on, one for the process; idle between builds. */
            val builder: ExecutorService =
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "VideoPlayerBuilder").apply { isDaemon = true }
                }
        }
    }

/**
 * Frees [player] a second after [ExoVideoPlayer.release], past the viewer's exit transition, whose last frame would
 * otherwise wait for the codec; on the main looper it belongs to.
 */
private fun Handler.releaseLater(player: ExoPlayer) {
    postDelayed({ player.release() }, RELEASE_DELAY_MILLIS)
}

private const val RELEASE_DELAY_MILLIS = 1_000L

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

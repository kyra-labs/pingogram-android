package dev.kyralabs.pingogram.feature.chat.ui.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
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
 * [AudioPlayer] on Media3's [ExoPlayer], playing files TDLib has downloaded (no network module).
 *
 * The player lives on a thread of its own, started and built there on the first [play], so a conversation nobody
 * plays anything in costs nothing. Every call from the main thread is posted to it and returns at once, [release]
 * above all: ExoPlayer's release waits, on the thread that calls it, until its playback thread has released the codec
 * and the audio track, for up to half a second, which on the main thread held up the frames after leaving a
 * conversation. What the player does by itself comes back to the main thread for [listener], unless the main thread
 * has put another file in the player, emptied or released it since ([generation]).
 *
 * The main thread never asks the player where it is: [positionMillis] moves on from the position the player last
 * reported ([PlaybackClock]), which it reports whenever it starts or stops advancing, jumps or learns the file's
 * length.
 *
 * It holds audio focus while it plays (speech pauses when another app talks over it, music only gets quieter), and
 * pauses when headphones are unplugged. It plays only while the conversation is started: playing on in the
 * background would need a media session in a foreground service.
 */
internal class ExoAudioPlayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AudioPlayer {
        private val mainHandler = Handler(Looper.getMainLooper())

        // Main thread.

        /** Posts to the player's thread; null until the first [play] and after [release]. */
        private var playerHandler: Handler? = null

        /** Counts the files put in the player and its emptying and release, so their stale reports are dropped. */
        private var generation = 0

        private var clock = PlaybackClock.Empty

        override var listener: AudioPlayer.Listener? = null

        override val positionMillis: Long get() = clock.positionAt(SystemClock.elapsedRealtime())

        override val durationMillis: Long? get() = clock.durationMillis

        // The player's thread.

        private var player: ExoPlayer? = null

        /** The kind the player's audio attributes are set for. */
        private var kind: AudioKind? = null

        /** The [generation] of the last call the player carried out. */
        private var playerGeneration = 0

        private val playerListener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (CLOCK_EVENTS.any(events::contains)) report(player)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    // Our own calls report themselves; these are audio focus, unplugged headphones and the like.
                    if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        onMainThread { listener?.onPlayingChanged(playWhenReady) }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onMainThread { listener?.onEnded() }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onMainThread { listener?.onError() }
                }
            }

        override fun play(
            path: String,
            kind: AudioKind,
            positionMillis: Long,
        ) {
            val generation = ++generation
            clock = PlaybackClock.startingAt(positionMillis, SystemClock.elapsedRealtime())
            val handler =
                playerHandler
                    ?: Handler(HandlerThread(THREAD_NAME).apply { start() }.looper).also { playerHandler = it }
            handler.post {
                val player = player ?: newPlayer().also { player = it }
                playerGeneration = generation
                if (this.kind != kind) {
                    // Handles audio focus: asks for it, pauses on losing it, plays on when it comes back.
                    player.setAudioAttributes(kind.attributes, true)
                    this.kind = kind
                }
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))), positionMillis)
                player.prepare()
                player.play()
            }
        }

        override fun pause() {
            clock = clock.pausedAt(SystemClock.elapsedRealtime())
            onPlayer { it.pause() }
        }

        override fun resume() {
            onPlayer { it.play() }
        }

        override fun seekTo(positionMillis: Long) {
            clock = clock.movedTo(positionMillis, SystemClock.elapsedRealtime())
            onPlayer { it.seekTo(positionMillis) }
        }

        override fun stop() {
            val generation = ++generation
            clock = PlaybackClock.Empty
            onPlayer {
                playerGeneration = generation
                it.stop()
                it.clearMediaItems()
            }
        }

        override fun release() {
            listener = null
            generation++
            val handler = playerHandler ?: return
            playerHandler = null
            handler.post {
                player?.release()
                player = null
                handler.looper.quitSafely()
            }
        }

        /** Built on the player's thread, whose looper it then calls back on. */
        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .apply { addListener(playerListener) }

        /** Runs [action] with the player on its thread, if there is one. */
        private fun onPlayer(action: (ExoPlayer) -> Unit) {
            playerHandler?.post { player?.let(action) }
        }

        /** On the player's thread: tells the main thread where the player is. */
        private fun report(player: Player) {
            val clock =
                PlaybackClock(
                    positionMillis = player.currentPosition,
                    advancing = player.isPlaying,
                    atMillis = SystemClock.elapsedRealtime(),
                    durationMillis = player.duration.takeIf { it != C.TIME_UNSET },
                )
            onMainThread { this.clock = clock }
        }

        /** On the player's thread: runs [action] on the main thread, unless what it reports on is stale by then. */
        private fun onMainThread(action: () -> Unit) {
            val reported = playerGeneration
            mainHandler.post { if (reported == generation) action() }
        }

        private companion object {
            const val THREAD_NAME = "ChatAudioPlayer"

            /** What moves the position or the length the main thread reads. */
            val CLOCK_EVENTS =
                intArrayOf(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
        }
    }

/** Media playback of speech or music; ExoPlayer derives the audio focus it asks for from these. */
private val AudioKind.attributes: AudioAttributes
    get() =
        AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(
                when (this) {
                    AudioKind.Speech -> C.AUDIO_CONTENT_TYPE_SPEECH
                    AudioKind.Music -> C.AUDIO_CONTENT_TYPE_MUSIC
                },
            ).build()

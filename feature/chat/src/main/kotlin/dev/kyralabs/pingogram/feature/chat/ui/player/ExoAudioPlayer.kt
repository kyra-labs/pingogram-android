package dev.kyralabs.pingogram.feature.chat.ui.player

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
 * [AudioPlayer] on Media3's [ExoPlayer], playing files TDLib has downloaded (no network module). The player is built
 * on the first [play], on the main thread it then belongs to, so a conversation nobody plays anything in costs
 * nothing; [release] frees it.
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
        private var player: ExoPlayer? = null

        /** The kind the player's audio attributes are set for. */
        private var kind: AudioKind? = null

        override var listener: AudioPlayer.Listener? = null

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

                override fun onPlayerError(error: PlaybackException) {
                    listener?.onError()
                }
            }

        override val positionMillis: Long get() = player?.currentPosition ?: 0L

        override val durationMillis: Long?
            get() = player?.duration?.takeIf { it != C.TIME_UNSET }

        override fun play(
            path: String,
            kind: AudioKind,
            positionMillis: Long,
        ) {
            val player = player ?: newPlayer().also { player = it }
            if (this.kind != kind) {
                // Handles audio focus: asks for it, pauses on losing it, plays on when it comes back.
                player.setAudioAttributes(kind.attributes, true)
                this.kind = kind
            }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))), positionMillis)
            player.prepare()
            player.play()
        }

        override fun pause() {
            player?.pause()
        }

        override fun resume() {
            player?.play()
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

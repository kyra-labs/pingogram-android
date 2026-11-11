package dev.kyralabs.pingogram.feature.media.profilemusic

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Plays a profile's songs (board 13a), a part of [ProfileMusicViewModel]: one at a time through [player], streamed
 * from TDLib as they download, one after another, the last one ending the playlist. How far the shown song played is
 * [progress], ticked while it plays ([PlaybackTicker]), in [scope].
 */
internal class MusicPlayback(
    private val player: VideoPlayer,
    private val state: MutableStateFlow<ProfileMusicUiState>,
    private val scope: CoroutineScope,
) {
    private val ticker = PlaybackTicker(player, state, scope)
    private val played get() = ticker.played

    /** How far the shown song has played, 0–1. */
    val progress: State<Float> get() = ticker.played

    /** Whether the shown song is in the player, so a toggle resumes rather than starts it. */
    private var loaded = false

    init {
        player.listener =
            object : VideoPlayer.Listener {
                override fun onPlayingChanged(isPlaying: Boolean) = setPlaying(isPlaying)

                override fun onFirstFrame() = Unit

                override fun onEnded() = playNextOrStop()

                override fun onError() {
                    empty()
                    state.updateContent { it.copy(isPlaying = false, cannotPlay = true) }
                }
            }
    }

    /** Pauses the shown song, plays it on, or starts it. */
    fun toggle() {
        val content = state.content() ?: return
        when {
            content.isPlaying -> player.pause()
            loaded -> player.resume()
            else -> play(content.current)
        }
    }

    /** Plays the song after the shown one, the first after the last. */
    fun next() {
        val content = state.content()?.takeIf { it.tracks.isNotEmpty() } ?: return
        play((content.current + 1) % content.tracks.size)
    }

    /** Plays song [index] from its start. */
    fun play(index: Int) {
        val track = state.content()?.tracks?.getOrNull(index) ?: return
        loaded = true
        played.floatValue = 0f
        state.updateContent { it.copy(current = index, isPlaying = true, cannotPlay = false) }
        player.play(PlaySource.Stream(track.fileId), VideoKind.Video)
        ticker.start()
    }

    /** Moves the shown song to [fraction] of its length, once it is in the player. */
    fun seek(fraction: Float) {
        val track = state.content()?.currentTrack ?: return
        if (!loaded) return
        played.floatValue = fraction.coerceIn(0f, 1f)
        player.seekTo((player.lengthOf(track) * played.floatValue).toLong())
    }

    /** The profile left the screen: nothing plays in the background, which would need a media session. */
    fun pause() {
        if (state.content()?.isPlaying == true) player.pause()
        setPlaying(false)
    }

    /** The song of [fileId] leaves the playlist: out of the player if it is the one in it. */
    fun forget(fileId: Int) {
        if (loaded && state.content()?.currentTrack?.fileId == fileId) {
            player.stop()
            empty()
        }
    }

    fun release() {
        ticker.stop()
        player.listener = null
        player.release()
    }

    private fun playNextOrStop() {
        val content = state.content() ?: return
        if (content.current + 1 < content.tracks.size) {
            play(content.current + 1)
        } else {
            player.stop()
            empty()
            state.updateContent { it.copy(current = 0, isPlaying = false) }
        }
    }

    /** Nothing in the player any more: no ticks, no progress. */
    private fun empty() {
        loaded = false
        ticker.stop()
        played.floatValue = 0f
    }

    private fun setPlaying(playing: Boolean) {
        state.updateContent { it.copy(isPlaying = playing) }
        if (playing) {
            ticker.start()
        } else {
            ticker.stop()
            if (loaded) ticker.tick()
        }
    }
}

/**
 * Puts how far the shown song played into [played] every [TICK_MILLIS] while it plays, from [player], in [scope]: a UI
 * ticker during playback, not polling.
 */
private class PlaybackTicker(
    private val player: VideoPlayer,
    private val state: MutableStateFlow<ProfileMusicUiState>,
    private val scope: CoroutineScope,
) {
    val played = mutableFloatStateOf(0f)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (true) {
                    tick()
                    delay(TICK_MILLIS)
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun tick() {
        val track = state.content()?.currentTrack ?: return
        val length = player.lengthOf(track)
        if (length > 0) played.floatValue = (player.positionMillis.toFloat() / length).coerceIn(0f, 1f)
    }

    private companion object {
        /** How often a playing song's position goes into [played]: smooth enough for a slider. */
        const val TICK_MILLIS = 100L
    }
}

/** [track]'s length in milliseconds: the player's once it knows it, else Telegram's. */
private fun VideoPlayer.lengthOf(track: MusicTrack): Long =
    durationMillis ?: (track.durationSeconds * MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1000L

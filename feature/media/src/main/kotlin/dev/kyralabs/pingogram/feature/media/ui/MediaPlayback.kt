package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.runtime.mutableFloatStateOf
import dev.kyralabs.pingogram.feature.media.ui.player.PlaySource
import dev.kyralabs.pingogram.feature.media.ui.player.VideoKind
import dev.kyralabs.pingogram.feature.media.ui.player.VideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the viewer's one player plays, for [MediaViewerViewModel]: the current item's video or GIF once its file is on
 * the device ([show]), on a [VideoPlayer]. [state] is the item in the player.
 *
 * - **The current item** plays as soon as it is shown ([show]) and the screen is started: a video once, a GIF in a
 *   silent loop. Another item stops it; nothing replaces it for a photo or a file still downloading. Without autoplay
 *   ([setAutoplay], power saving) it waits for [playPause] instead ([VideoPlayback.waitsForTap]).
 * - **Play and pause** ([playPause]) toggle the video; after its end it plays again from the start. [seek] moves it.
 * - **The screen stopping** ([setStarted]) pauses what plays; starting again plays a GIF on, and a video that has
 *   not played yet, but leaves a paused video paused.
 * - **Progress**: while a video plays, its position goes into snapshot state every other frame ([ProgressTicker]),
 *   which [VideoPlayback.progress] reads in the draw or layout phase, so a tick re-emits nothing. GIFs show no
 *   progress.
 * - [release] when the view model is cleared.
 *
 * Main thread only, like the player. A file the player cannot play goes to [onFailed], once.
 */
@Suppress("TooManyFunctions") // The one player's state machine: a function per thing the viewer or the player does.
internal class MediaPlayback(
    private val player: VideoPlayer,
    private val scope: CoroutineScope,
    private val onFailed: () -> Unit,
) {
    private val mutableState = MutableStateFlow<VideoPlayback?>(null)

    /** The item in the player; null when none is. */
    val state: StateFlow<VideoPlayback?> = mutableState.asStateFlow()

    private var session: Session? = null
    private val ticker = ProgressTicker(player, scope)
    private var isStarted = false
    private var autoplay = true

    /** Where a message's video is to start ([startAt]), until it does. */
    private var startPosition: Pair<Long, Long>? = null

    /** The target the player failed to play, not tried again until another one was shown. */
    private var failed: PlayTarget? = null

    private val listener =
        object : VideoPlayer.Listener {
            override fun onPlayingChanged(isPlaying: Boolean) {
                val current = session?.takeIf { it.isPlaying != isPlaying } ?: return
                current.isPlaying = isPlaying
                if (isPlaying) ticker.start(current) else ticker.stop(current)
                publish()
            }

            override fun onFirstFrame() {
                val current = session?.takeUnless { it.showsFrame } ?: return
                current.showsFrame = true
                publish()
            }

            override fun onEnded() {
                val current = session ?: return
                current.isPlaying = false
                ticker.stop(current)
                current.progress.floatValue = 1f
                publish()
            }

            override fun onError() {
                failed = session?.target
                stop()
                onFailed()
            }
        }

    init {
        player.listener = listener
    }

    /**
     * Puts [target] in the player, the current item's video or GIF, or nothing for null. The same target again changes
     * nothing; another one starts from its beginning. A target the player failed to play stays out of it until another
     * one (or none) was shown, e.g. after turning the page away and back.
     */
    fun show(target: PlayTarget?) {
        if (target != null && (session?.target == target || target == failed)) return
        failed = null
        stop()
        if (target == null) return
        val session = Session(target)
        this.session = session
        if (isStarted && autoplay) start(session) else publish()
    }

    /** Whether the current item plays as it is shown; turned back on, one waiting for a tap plays. */
    fun setAutoplay(on: Boolean) {
        if (on == autoplay) return
        autoplay = on
        val current = session ?: return
        if (on && isStarted && !current.hasStarted) start(current) else publish()
    }

    /** The screen was started or stopped. */
    fun setStarted(started: Boolean) {
        if (started == isStarted) return
        isStarted = started
        val current = session ?: return
        when {
            !started -> pause(current)
            !current.hasStarted -> if (autoplay) start(current)
            current.target.kind == VideoKind.Gif -> resume(current)
        }
    }

    /** Plays or pauses the video in the player, or plays the item waiting for a tap. */
    fun playPause() {
        val current = session ?: return
        when {
            !current.hasStarted -> if (isStarted) start(current)
            current.isPlaying -> pause(current)
            else -> resume(current)
        }
    }

    /** Moves the video in the player to [fraction] (0–1) of its length, shown at once. */
    fun seek(fraction: Float) {
        val current = session?.takeIf { it.hasStarted } ?: return
        val target = fraction.coerceIn(0f, 1f)
        current.progress.floatValue = target
        val length = player.durationMillis ?: current.target.durationMillis
        player.seekTo((target * length).toLong())
    }

    /**
     * The video in the player with where it is, for picture-in-picture, which plays on from there: the player stops.
     * Null for a GIF, or a video not playing yet.
     */
    fun handOff(): HandOff? {
        val current = session?.takeIf { it.hasStarted && it.target.kind == VideoKind.Video } ?: return null
        val position = if (current.progress.floatValue >= 1f) 0L else player.positionMillis
        stop()
        return HandOff(current.target, position)
    }

    /** Message [messageId]'s video, once it plays, starts at [positionMillis]: picture-in-picture gave it back. */
    fun startAt(
        messageId: Long,
        positionMillis: Long,
    ) {
        startPosition = messageId to positionMillis
    }

    /** Stops and frees the player for good. */
    fun release() {
        stop()
        player.listener = null
        player.release()
    }

    private fun start(session: Session) {
        session.hasStarted = true
        session.isPlaying = true
        player.play(session.target.source, session.target.kind)
        startPosition?.takeIf { it.first == session.target.messageId }?.let { (_, position) ->
            startPosition = null
            player.seekTo(position)
            val length = session.target.durationMillis
            if (length > 0) session.progress.floatValue = (position.toFloat() / length).coerceIn(0f, 1f)
        }
        ticker.start(session)
        publish()
    }

    private fun pause(session: Session) {
        if (!session.isPlaying) return
        player.pause()
        session.isPlaying = false
        ticker.stop(session)
        publish()
    }

    private fun resume(session: Session) {
        if (session.isPlaying) return
        if (session.progress.floatValue >= 1f) session.progress.floatValue = 0f
        player.resume()
        session.isPlaying = true
        ticker.start(session)
        publish()
    }

    private fun stop() {
        val current = session ?: return
        session = null
        ticker.stop(session = null)
        if (current.hasStarted) player.stop()
        publish()
    }

    private fun publish() {
        mutableState.value =
            session?.let {
                VideoPlayback(
                    messageId = it.target.messageId,
                    isPlaying = it.isPlaying,
                    showsFrame = it.showsFrame,
                    progress = it.readProgress,
                    buffered = it.readBuffered,
                    waitsForTap = !it.hasStarted && !autoplay,
                )
            }
    }
}

/** A video taken out of the player at [positionMillis], for picture-in-picture. */
internal data class HandOff(
    val target: PlayTarget,
    val positionMillis: Long,
)

/** What the player is to play: [source] of message [messageId], as [kind], [durationMillis] long. */
internal data class PlayTarget(
    val messageId: Long,
    val source: PlaySource,
    val kind: VideoKind,
    val durationMillis: Long,
)

/**
 * [target] in the player: whether it has been given to the player yet ([hasStarted]: not while the screen is
 * stopped), whether it plays, whether its first frame is on the surface, and how far it played, in snapshot state of
 * its own, so an item that leaves the player keeps no reader.
 */
private class Session(
    val target: PlayTarget,
) {
    val progress = mutableFloatStateOf(0f)
    val readProgress: () -> Float = { progress.floatValue }

    /** How far the player has the video, 0–1: all of a file on the device, what came so far of a stream. */
    val buffered = mutableFloatStateOf(0f)
    val readBuffered: () -> Float = { buffered.floatValue }
    var hasStarted = false
    var isPlaying = false
    var showsFrame = false
}

/**
 * Puts the played fraction of a session's video into its progress every [TICK_MILLIS] while it plays, from [player],
 * in [scope]: a UI ticker during playback, not polling. GIFs show no progress, so they get no ticks.
 */
private class ProgressTicker(
    private val player: VideoPlayer,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(session: Session) {
        job?.cancel()
        if (session.target.kind != VideoKind.Video) return
        job =
            scope.launch {
                while (true) {
                    session.progress.floatValue = player.playedFraction(session)
                    session.buffered.floatValue = player.bufferedFraction(session)
                    delay(TICK_MILLIS)
                }
            }
    }

    /** Stops ticking, with [session]'s progress where the player is; none for a session that leaves the player. */
    fun stop(session: Session?) {
        job?.cancel()
        job = null
        if (session?.target?.kind == VideoKind.Video) session.progress.floatValue = player.playedFraction(session)
    }

    private companion object {
        /** How often a playing video's position goes into the progress: about every second frame at 60 Hz. */
        const val TICK_MILLIS = 32L
    }
}

/** How much of [session] has played, 0–1, by the player's length or the video's until the player knows it. */
private fun VideoPlayer.playedFraction(session: Session): Float = fractionOf(positionMillis, session)

/** How much of [session] the player has, 0–1. */
private fun VideoPlayer.bufferedFraction(session: Session): Float = fractionOf(bufferedMillis, session)

private fun VideoPlayer.fractionOf(
    millis: Long,
    session: Session,
): Float {
    val length = durationMillis ?: session.target.durationMillis
    return if (length > 0) (millis.toFloat() / length).coerceIn(0f, 1f) else 0f
}
